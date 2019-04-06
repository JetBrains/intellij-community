/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.json.codeinsight;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.icons.AllIcons;
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

/**
 * @author Mikhail Golubev
 */
public class JsonDuplicatePropertyKeysInspection extends LocalInspectionTool {
  private static final String COMMENT = "$comment";

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return JsonBundle.message("inspection.duplicate.keys.name");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    boolean isSchemaFile = JsonSchemaService.isSchemaFile(holder.getFile());
    return new JsonElementVisitor() {
      @Override
      public void visitObject(@NotNull JsonObject o) {
        final MultiMap<String, PsiElement> keys = new MultiMap<>();
        for (JsonProperty property : o.getPropertyList()) {
          keys.putValue(property.getName(), property.getNameElement());
        }
        for (Map.Entry<String, Collection<PsiElement>> entry : keys.entrySet()) {
          final Collection<PsiElement> sameNamedKeys = entry.getValue();
          final String entryKey = entry.getKey();
          if (sameNamedKeys.size() > 1 && (!isSchemaFile || !COMMENT.equalsIgnoreCase(entryKey))) {
            for (PsiElement element : sameNamedKeys) {
              holder.registerProblem(element, JsonBundle.message("inspection.duplicate.keys.msg.duplicate.keys", entryKey),
                                     new NavigateToDuplicatesFix(sameNamedKeys, element, entryKey));
            }
          }
        }
      }
    };
  }

  private static class NavigateToDuplicatesFix extends LocalQuickFixAndIntentionActionOnPsiElement {
    @NotNull private final Collection<SmartPsiElementPointer> mySameNamedKeys;
    @NotNull private final String myEntryKey;

    private NavigateToDuplicatesFix(@NotNull Collection<PsiElement> sameNamedKeys, @NotNull PsiElement element, @NotNull String entryKey) {
      super(element);
      mySameNamedKeys = ContainerUtil.map(sameNamedKeys, k -> SmartPointerManager.createPointer(k));
      myEntryKey = entryKey;
    }

    @NotNull
    @Override
    public String getText() {
      return "Navigate to duplicates";
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return getText();
    }

    @Override
    public void invoke(@NotNull Project project,
                       @NotNull PsiFile file,
                       @Nullable Editor editor,
                       @NotNull PsiElement startElement,
                       @NotNull PsiElement endElement) {
      if (editor == null) return;

      if (mySameNamedKeys.size() == 2) {
        final Iterator<SmartPsiElementPointer> iterator = mySameNamedKeys.iterator();
        final PsiElement next = iterator.next().getElement();
        PsiElement toNavigate = next != startElement ? next : iterator.next().getElement();
        if (toNavigate == null) return;
        navigateTo(editor, toNavigate);
      }
      else {
        final List<PsiElement> allElements =
          mySameNamedKeys.stream().map(k -> k.getElement()).filter(k -> k != startElement).collect(Collectors.toList());
        JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<PsiElement>("Duplicates of '" + myEntryKey + "'", allElements) {
          @NotNull
          @Override
          public Icon getIconFor(PsiElement aValue) {
            return AllIcons.Nodes.Property;
          }

          @NotNull
          @Override
          public String getTextFor(PsiElement value) {
            return "'" + myEntryKey + "' at line #" + editor.getDocument().getLineNumber(value.getTextOffset());
          }

          @Override
          public int getDefaultOptionIndex() {
            return 0;
          }

          @Nullable
          @Override
          public PopupStep onChosen(PsiElement selectedValue, boolean finalChoice) {
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
