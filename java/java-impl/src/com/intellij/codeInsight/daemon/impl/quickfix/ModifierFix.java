/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.IntentionAndQuickFixAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.PsiElementProcessorAdapter;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ModifierFix extends IntentionAndQuickFixAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.ModifierFix");

  private final PsiModifierList myModifierList;
  @Modifier private final String myModifier;
  private final boolean myShouldHave;
  private final boolean myShowContainingClass;
  private PsiVariable myVariable;

  public ModifierFix(PsiModifierList modifierList, @Modifier @NotNull String modifier, boolean shouldHave, boolean showContainingClass) {
    myModifierList = modifierList;
    myModifier = modifier;
    myShouldHave = shouldHave;
    myShowContainingClass = showContainingClass;
  }
  public ModifierFix(@NotNull PsiModifierListOwner owner, @Modifier @NotNull String modifier, boolean shouldHave, boolean showContainingClass) {
    this(owner.getModifierList(), modifier, shouldHave, showContainingClass);
    if (owner instanceof PsiVariable) {
      myVariable = (PsiVariable)owner;
    }
  }

  @NotNull
  public String getName() {
    String name = null;
    PsiElement parent = myVariable == null ? myModifierList.getParent() : myVariable;
    if (parent instanceof PsiClass) {
      name = ((PsiClass)parent).getName();
    }
    else {
      int options = PsiFormatUtil.SHOW_NAME | (myShowContainingClass ? PsiFormatUtil.SHOW_CONTAINING_CLASS : 0);
      if (parent instanceof PsiMethod) {
        name = PsiFormatUtil.formatMethod((PsiMethod)parent, PsiSubstitutor.EMPTY, options, 0);
      }
      else if (parent instanceof PsiVariable) {
        name = PsiFormatUtil.formatVariable((PsiVariable)parent, options, PsiSubstitutor.EMPTY);
      }
      else if (parent instanceof PsiClassInitializer) {
        PsiClass containingClass = ((PsiClassInitializer)parent).getContainingClass();
        String className = containingClass instanceof PsiAnonymousClass
                           ? QuickFixBundle.message("anonymous.class.presentation", ((PsiAnonymousClass)containingClass).getBaseClassType().getPresentableText())
                           : containingClass.getName();
        name = QuickFixBundle.message("class.initializer.presentation", className);
      }
    }

    String modifierText = VisibilityUtil.toPresentableText(myModifier);

    return QuickFixBundle.message(myShouldHave ? "add.modifier.fix" : "remove.modifier.fix", name, modifierText);
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("fix.modifiers.family");
  }

  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    return myModifierList != null &&
           myModifierList.isValid() &&
           myModifierList.getManager().isInProject(myModifierList) &&
           myModifierList.hasModifierProperty(myModifier) != myShouldHave &&
           (myVariable == null || myVariable.isValid());
  }

  private void changeModifierList (PsiModifierList modifierList) {
    try {
      modifierList.setModifierProperty(myModifier, myShouldHave);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public void applyFix(final Project project, final PsiFile file, @Nullable final Editor editor) {
    if (!CodeInsightUtilBase.preparePsiElementForWrite(myModifierList)) return;
    final List<PsiModifierList> modifierLists = new ArrayList<PsiModifierList>();
    final PsiFile containingFile = myModifierList.getContainingFile();
    final PsiModifierList modifierList;
    if (myVariable != null && myVariable.isValid()) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          try {
            myVariable.normalizeDeclaration();
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      });
      modifierList = myVariable.getModifierList();
      assert modifierList != null;
    }
    else {
      modifierList = myModifierList;
    }
    PsiElement owner = modifierList.getParent();
    if (owner instanceof PsiMethod) {
      PsiModifierList copy = (PsiModifierList)myModifierList.copy();
      changeModifierList(copy);
      final int accessLevel = PsiUtil.getAccessLevel(copy);

      OverridingMethodsSearch.search((PsiMethod)owner, owner.getResolveScope(), true).forEach(new PsiElementProcessorAdapter<PsiMethod>(new PsiElementProcessor<PsiMethod>() {
          public boolean execute(PsiMethod inheritor) {
            PsiModifierList list = inheritor.getModifierList();
            if (inheritor.getManager().isInProject(inheritor) && PsiUtil.getAccessLevel(list) < accessLevel) {
              modifierLists.add(list);
            }
            return true;
          }
        }));
    }

    if (!CodeInsightUtilBase.prepareFileForWrite(containingFile)) return;

    if (!modifierLists.isEmpty()) {
      if (Messages.showYesNoDialog(project,
                                   QuickFixBundle.message("change.inheritors.visibility.warning.text"),
                                   QuickFixBundle.message("change.inheritors.visibility.warning.title"),
                                   Messages.getQuestionIcon()) == DialogWrapper.OK_EXIT_CODE) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            if (!CodeInsightUtilBase.preparePsiElementsForWrite(modifierLists)) {
              return;
            }

            for (final PsiModifierList modifierList : modifierLists) {
              changeModifierList(modifierList);
            }
          }
        });
      }
    }

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        changeModifierList(modifierList);
        UndoUtil.markPsiFileForUndo(containingFile);
      }
    });
  }

  public boolean startInWriteAction() {
    return false;
  }

}
