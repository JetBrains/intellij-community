// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.modcommand.ModCommand;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ModCommandAwareExternalAnnotationsManager extends ReadableExternalAnnotationsManager {
  public ModCommandAwareExternalAnnotationsManager(PsiManager psiManager) { super(psiManager); }

  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static ModCommandAwareExternalAnnotationsManager getInstance(@NotNull Project project) {
    return (ModCommandAwareExternalAnnotationsManager)ExternalAnnotationsManager.getInstance(project);
  }

  protected @Nullable List<XmlFile> findExternalAnnotationsXmlFiles(@NotNull PsiModifierListOwner listOwner) {
    List<PsiFile> psiFiles = findExternalAnnotationsFiles(listOwner);
    if (psiFiles == null) {
      return null;
    }
    List<XmlFile> xmlFiles = new ArrayList<>();
    for (PsiFile psiFile : psiFiles) {
      if (psiFile instanceof XmlFile) {
        xmlFiles.add((XmlFile)psiFile);
      }
    }
    return xmlFiles;
  }

  private @NotNull ModCommand processExistingExternalAnnotationsModCommand(@NotNull List<PsiModifierListOwner> listOwners,
                                                                           @NotNull List<String> annotationFQNs,
                                                                           @NotNull Processor<? super XmlTag> annotationTagProcessor) {
    if (listOwners.isEmpty()) return ModCommand.nop();
    List<XmlTag> origTags = StreamEx.of(listOwners)
      .mapToEntry(this::findExternalAnnotationsXmlFiles)
      .removeValues(f -> f == null || f.isEmpty())
      .flatMapKeyValue((owner, fileList) -> StreamEx.of(fileList)
        .cross(annotationFQNs)
        .flatMapKeyValue((file, annotationFQN) -> getTagsToProcess(file, owner, annotationFQN).stream()))
      .toList();
    if (origTags.isEmpty()) return ModCommand.nop();
    return ModCommand.psiUpdate(origTags.get(0), (tg, updater) -> {
      List<XmlTag> tags = ContainerUtil.map(origTags, updater::getWritable);
      Set<XmlFile> files = StreamEx.of(tags).map(t -> (XmlFile)t.getContainingFile()).toSet();
      for (XmlTag tag : tags) {
        if (tag.isValid()) {
          annotationTagProcessor.process(tag);
        }
      }
      if (!IntentionPreviewUtils.isIntentionPreviewActive()) {
        // Avoid too big preview changes
        for (XmlFile file : files) {
          sortItems(file);
        }
      }
    });
  }

  public @NotNull ModCommand annotateExternallyModCommand(@NotNull PsiModifierListOwner listOwner,
                                                          @NotNull String annotationFQName,
                                                          PsiNameValuePair @Nullable [] value) {
    return annotateExternallyModCommand(listOwner, annotationFQName, value, List.of());
  }

  public @NotNull ModCommand annotateExternallyModCommand(@NotNull PsiModifierListOwner listOwner,
                                                          @NotNull String annotationFQName,
                                                          PsiNameValuePair @Nullable [] value,
                                                          @NotNull List<@NotNull String> annotationsToRemove) {
    throw new UnsupportedOperationException("annotateExternallyModCommand is not implemented in " + getClass().getName());
  }

  protected static @NotNull List<XmlTag> getTagsToProcess(@NotNull XmlFile file, @NotNull PsiModifierListOwner listOwner, @NotNull String annotationFQN) {
    final XmlDocument document = file.getDocument();
    if (document == null) return List.of();
    final XmlTag rootTag = document.getRootTag();
    if (rootTag == null) return List.of();
    final String externalName = getExternalName(listOwner);
    final List<XmlTag> tagsToProcess = new ArrayList<>();
    for (XmlTag tag : rootTag.getSubTags()) {
      String nameValue = tag.getAttributeValue("name");
      String className = nameValue == null ? null : StringUtil.unescapeXmlEntities(nameValue);
      if (!Comparing.strEqual(className, externalName)) continue;
      for (XmlTag annotationTag : tag.getSubTags()) {
        if (!Comparing.strEqual(annotationTag.getAttributeValue("name"), annotationFQN)) {
          continue;
        }
        tagsToProcess.add(annotationTag);
      }
    }
    return tagsToProcess;
  }

  /**
   * Returns a command that removes the specified external annotations
   *
   * @param listOwner      The list of PsiModifierListOwner to deannotate.
   * @param annotationFQNs The list of fully qualified annotation names to remove.
   * @return The ModCommand that removes the specified annotations
   */
  @Contract(pure = true)
  public @NotNull ModCommand deannotateModCommand(List<PsiModifierListOwner> listOwner, @NotNull List<String> annotationFQNs) {
    return processExistingExternalAnnotationsModCommand(listOwner, annotationFQNs, annotationTag -> {
      PsiElement parent = annotationTag.getParent();
      annotationTag.delete();
      if (parent instanceof XmlTag xmlTag) {
        if (xmlTag.getSubTags().length == 0) {
          parent.delete();
        }
      }
      return true;
    });
  }

  /**
   * Returns a command that edits an existing external annotation.
   *
   * @param listOwner      The modifier list owner to edit external annotations for.
   * @param annotationFQN  The fully qualified name of the annotation to edit.
   * @param value          An array of new key-value pairs (old ones will be replaced with new ones).
   * @return The ModCommand that edits an existing external annotation.
   */
  public @NotNull ModCommand editExternalAnnotationModCommand(@NotNull PsiModifierListOwner listOwner,
                                                              @NotNull String annotationFQN,
                                                              PsiNameValuePair @Nullable [] value) {
    return processExistingExternalAnnotationsModCommand(List.of(listOwner), List.of(annotationFQN), annotationTag -> {
      annotationTag.replace(XmlElementFactory.getInstance(myPsiManager.getProject()).createTagFromText(
        createAnnotationTag(annotationFQN, value)));
      return true;
    });
  }

  protected static void sortItems(@NotNull XmlFile xmlFile) {
    XmlDocument document = xmlFile.getDocument();
    if (document == null) {
      return;
    }
    XmlTag rootTag = document.getRootTag();
    if (rootTag == null) {
      return;
    }

    List<XmlTag> itemTags = new ArrayList<>();
    for (XmlTag item : rootTag.getSubTags()) {
      if (item.getAttributeValue("name") != null) {
        itemTags.add(item);
      }
      else {
        item.delete();
      }
    }

    List<XmlTag> sorted = new ArrayList<>(itemTags);
    sorted.sort((item1, item2) -> {
      String externalName1 = item1.getAttributeValue("name");
      String externalName2 = item2.getAttributeValue("name");
      assert externalName1 != null && externalName2 != null; // null names were not added
      return externalName1.compareTo(externalName2);
    });
    if (!sorted.equals(itemTags)) {
      for (XmlTag item : sorted) {
        rootTag.addAfter(item, null);
        item.delete();
      }
    }
  }

  @VisibleForTesting
  public static @NonNls @NotNull String createAnnotationTag(@NotNull String annotationFQName, PsiNameValuePair @Nullable [] values) {
    @NonNls String text;
    if (values != null && values.length != 0) {
      text = "<annotation name='" + annotationFQName + "'>\n";
      text += StringUtil.join(values, pair -> "<val" +
                                              (pair.getName() != null ? " name=\"" + pair.getName() + "\"" : "") +
                                              " val=\"" + StringUtil.escapeXmlEntities(pair.getValue().getText()) + "\"/>", "    \n");
      text += "</annotation>";
    }
    else {
      text = "<annotation name='" + annotationFQName + "'/>\n";
    }
    return text;
  }
}
