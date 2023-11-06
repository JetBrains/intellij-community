// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.modcommand.ModCommand;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Processor;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ModCommandAwareExternalAnnotationsManager extends ReadableExternalAnnotationsManager {
  public ModCommandAwareExternalAnnotationsManager(PsiManager psiManager) { super(psiManager); }

  @Nullable
  protected List<XmlFile> findExternalAnnotationsXmlFiles(@NotNull PsiModifierListOwner listOwner) {
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
    return ModCommand.psiUpdate(listOwners.get(0), (lo, updater) -> {
      List<XmlTag> tags = StreamEx.of(listOwners)
        .mapToEntry(this::findExternalAnnotationsXmlFiles)
        .removeValues(f -> f == null || f.isEmpty())
        .flatMapKeyValue((owner, fileList) -> StreamEx.of(fileList)
          .cross(annotationFQNs)
          .flatMapKeyValue((file, annotationFQN) -> getTagsToProcess(file, owner, annotationFQN).stream()))
        .map(updater::getWritable)
        .toList();
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
}
