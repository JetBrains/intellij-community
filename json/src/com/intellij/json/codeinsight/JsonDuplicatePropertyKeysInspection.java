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
import com.intellij.codeInspection.LocalQuickFixBase;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.json.JsonBundle;
import com.intellij.json.psi.JsonElementVisitor;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.PsiEditorUtil;
import com.intellij.util.containers.MultiMap;
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
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return JsonBundle.message("inspection.duplicate.keys.name");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
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
          if (sameNamedKeys.size() > 1) {
            for (PsiElement element : sameNamedKeys) {
              holder.registerProblem(element, JsonBundle.message("inspection.duplicate.keys.msg.duplicate.keys", entryKey),
                                     new NavigateToDuplicatesFix(sameNamedKeys, element, entryKey));
            }
          }
        }
      }
    };
  }

  private static class NavigateToDuplicatesFix extends LocalQuickFixBase {
    @NotNull private final Collection<SmartPsiElementPointer> mySameNamedKeys;
    @NotNull private final SmartPsiElementPointer myElement;
    @NotNull private final String myEntryKey;

    private NavigateToDuplicatesFix(@NotNull Collection<PsiElement> sameNamedKeys, @NotNull PsiElement element, @NotNull String entryKey) {
      super("Navigate to duplicates");
      mySameNamedKeys = sameNamedKeys.stream().map(k -> SmartPointerManager.createPointer(k)).collect(Collectors.toList());
      myElement = SmartPointerManager.createPointer(element);
      myEntryKey = entryKey;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final Editor editor =
        PsiEditorUtil.Service.getInstance().findEditorByPsiElement(descriptor.getPsiElement());
      if (editor == null) return;
      applyFix(editor);
    }


    public void applyFix(@NotNull Editor editor) {
      final PsiElement currentElement = myElement.getElement();
      if (mySameNamedKeys.size() == 2) {
        final Iterator<SmartPsiElementPointer> iterator = mySameNamedKeys.iterator();
        final PsiElement next = iterator.next().getElement();
        PsiElement toNavigate = next != currentElement ? next : iterator.next().getElement();
        if (toNavigate == null) return;
        navigateTo(editor, toNavigate);
      }
      else {
        JBPopupFactory.getInstance().createListPopup(new ListPopupStep() {
          @NotNull
          @Override
          public List getValues() {
            return mySameNamedKeys.stream().map(k -> k.getElement()).filter(k -> k != currentElement).collect(Collectors.toList());
          }

          @Override
          public boolean isSelectable(Object value) {
            return true;
          }

          @Nullable
          @Override
          public Icon getIconFor(Object aValue) {
            return aValue instanceof PsiElement ? ((PsiElement)aValue).getIcon(0) : null;
          }

          @NotNull
          @Override
          public String getTextFor(Object value) {
            return "'" + myEntryKey + "' at line #" + editor.getDocument().getLineNumber(((PsiElement)value).getTextOffset());
          }

          @Nullable
          @Override
          public ListSeparator getSeparatorAbove(Object value) {
            return null;
          }

          @Override
          public int getDefaultOptionIndex() {
            return 0;
          }

          @NotNull
          @Override
          public String getTitle() {
            return "Duplicates of '" + myEntryKey + "'";
          }

          @Nullable
          @Override
          public PopupStep onChosen(Object selectedValue, boolean finalChoice) {
            navigateTo(editor, (PsiElement)selectedValue);
            return PopupStep.FINAL_CHOICE;
          }

          @Override
          public boolean hasSubstep(Object selectedValue) {
            return false;
          }

          @Override
          public void canceled() {
          }

          @Override
          public boolean isMnemonicsNavigationEnabled() {
            return false;
          }

          @Nullable
          @Override
          public MnemonicNavigationFilter getMnemonicNavigationFilter() {
            return null;
          }

          @Override
          public boolean isSpeedSearchEnabled() {
            return true;
          }

          @Nullable
          @Override
          public SpeedSearchFilter getSpeedSearchFilter() {
            return null;
          }

          @Override
          public boolean isAutoSelectionEnabled() {
            return false;
          }

          @Nullable
          @Override
          public Runnable getFinalRunnable() {
            return null;
          }
        }).showInBestPositionFor(editor);
      }
    }

    private static void navigateTo(@NotNull Editor editor, PsiElement toNavigate) {
      editor.getCaretModel().moveToOffset(toNavigate.getTextOffset());
      editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    }
  }
}
