// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.codeinsight;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.json.JsonBundle;
import com.intellij.json.psi.JsonElementVisitor;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.psi.*;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class JsonDuplicatePropertyKeysInspection extends LocalInspectionTool {
  private static final String COMMENT = "$comment";

  @Override
  public @NotNull PsiElementVisitor buildVisitor(final @NotNull ProblemsHolder holder, boolean isOnTheFly) {
    boolean isSchemaFile = JsonSchemaService.isSchemaFile(holder.getFile());
    return new JsonElementVisitor() {
      @Override
      public void visitObject(@NotNull JsonObject o) {
        final MultiMap<String, PsiElement> keys = new MultiMap<>();
        for (JsonProperty property : o.getPropertyList()) {
          keys.putValue(property.getName(), property.getNameElement());
        }
        visitKeys(keys, isSchemaFile, holder);
      }
    };
  }

  protected static void visitKeys(MultiMap<String, PsiElement> keys, boolean isSchemaFile, @NotNull ProblemsHolder holder) {
    for (Map.Entry<String, Collection<PsiElement>> entry : keys.entrySet()) {
      final Collection<PsiElement> sameNamedKeys = entry.getValue();
      final String entryKey = entry.getKey();
      if (sameNamedKeys.size() > 1 && (!isSchemaFile || !COMMENT.equalsIgnoreCase(entryKey))) {
        for (PsiElement element : sameNamedKeys) {
          holder.registerProblem(element, JsonBundle.message("inspection.duplicate.keys.msg.duplicate.keys", entryKey),
                                 getNavigateToDuplicatesFix(sameNamedKeys, element, entryKey));
        }
      }
    }
  }

  protected static @NotNull NavigateToDuplicatesFix getNavigateToDuplicatesFix(Collection<PsiElement> sameNamedKeys,
                                                                               PsiElement element,
                                                                               String entryKey) {
    return new NavigateToDuplicatesFix(sameNamedKeys, element, entryKey);
  }

  private static final class NavigateToDuplicatesFix extends LocalQuickFixAndIntentionActionOnPsiElement {
    private final @NotNull Collection<SmartPsiElementPointer<PsiElement>> mySameNamedKeys;
    private final @NotNull String myEntryKey;

    private NavigateToDuplicatesFix(@NotNull Collection<PsiElement> sameNamedKeys, @NotNull PsiElement element, @NotNull String entryKey) {
      super(element);
      mySameNamedKeys = ContainerUtil.map(sameNamedKeys, k -> SmartPointerManager.createPointer(k));
      myEntryKey = entryKey;
    }

    @Override
    public @NotNull String getText() {
      return JsonBundle.message("navigate.to.duplicates");
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getFamilyName() {
      return getText();
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
      return IntentionPreviewInfo.EMPTY;
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
      return IntentionPreviewInfo.EMPTY;
    }

    @Override
    public void invoke(@NotNull Project project,
                       @NotNull PsiFile psiFile,
                       @Nullable Editor editor,
                       @NotNull PsiElement startElement,
                       @NotNull PsiElement endElement) {
      if (editor == null) return;

      if (mySameNamedKeys.size() == 2) {
        final Iterator<SmartPsiElementPointer<PsiElement>> iterator = mySameNamedKeys.iterator();
        final PsiElement next = iterator.next().getElement();
        PsiElement toNavigate = next != startElement ? next : iterator.next().getElement();
        if (toNavigate == null) return;
        navigateTo(editor, toNavigate);
      }
      else {
        final List<PsiElement> allElements =
          mySameNamedKeys.stream().map(k -> k.getElement()).filter(k -> k != startElement).collect(Collectors.toList());
        JBPopupFactory.getInstance().createListPopup(
          new BaseListPopupStep<>(JsonBundle.message("navigate.to.duplicates.header", myEntryKey), allElements) {
            @Override
            public @NotNull Icon getIconFor(PsiElement aValue) {
              return IconManager.getInstance().getPlatformIcon(PlatformIcons.Property);
            }

            @Override
            public @NotNull String getTextFor(PsiElement value) {
              return JsonBundle
                .message("navigate.to.duplicates.desc", myEntryKey, editor.getDocument().getLineNumber(value.getTextOffset()));
            }

            @Override
            public int getDefaultOptionIndex() {
              return 0;
            }

            @Override
            public @Nullable PopupStep<?> onChosen(PsiElement selectedValue, boolean finalChoice) {
              navigateTo(editor, selectedValue);
              return PopupStep.FINAL_CHOICE;
            }

            @Override
            public boolean isSpeedSearchEnabled() {
              return true;
            }
          }).showInBestPositionFor(editor);
      }
    }

    private static void navigateTo(@NotNull Editor editor, @NotNull PsiElement toNavigate) {
      editor.getCaretModel().moveToOffset(toNavigate.getTextOffset());
      editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    }
  }
}
