/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.PsiElementProcessorAdapter;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ModifierFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.ModifierFix");

  @PsiModifier.ModifierConstant private final String myModifier;
  private final boolean myShouldHave;
  private final boolean myShowContainingClass;
  private final String myName;
  private final SmartPsiElementPointer<PsiVariable> myVariable;

  public ModifierFix(PsiModifierList modifierList, @PsiModifier.ModifierConstant @NotNull String modifier, boolean shouldHave, boolean showContainingClass) {
    super(modifierList);
    myModifier = modifier;
    myShouldHave = shouldHave;
    myShowContainingClass = showContainingClass;
    myName = format(null, modifierList);
    myVariable = null;
  }

  public ModifierFix(@NotNull PsiModifierListOwner owner, @PsiModifier.ModifierConstant @NotNull String modifier, boolean shouldHave, boolean showContainingClass) {
    super(owner.getModifierList());
    myModifier = modifier;
    myShouldHave = shouldHave;
    myShowContainingClass = showContainingClass;
    PsiVariable variable = owner instanceof PsiVariable ? (PsiVariable)owner : null;
    myName = format(variable, owner.getModifierList());

    myVariable = variable == null ? null : SmartPointerManager.getInstance(owner.getProject()).createSmartPsiElementPointer(variable);
  }

  @NotNull
  @Override
  public String getText() {
    return myName;
  }

  private String format(PsiVariable variable, PsiModifierList modifierList) {
    String name = null;
    PsiElement parent = variable != null ? variable : modifierList != null ? modifierList.getParent() : null;
    if (parent instanceof PsiClass) {
      name = ((PsiClass)parent).getName();
    }
    else if (parent instanceof PsiJavaModule) {
      name = ((PsiJavaModule)parent).getName();
    }
    else {
      int options = PsiFormatUtilBase.SHOW_NAME | (myShowContainingClass ? PsiFormatUtilBase.SHOW_CONTAINING_CLASS : 0);
      if (parent instanceof PsiMethod) {
        name = PsiFormatUtil.formatMethod((PsiMethod)parent, PsiSubstitutor.EMPTY, options, 0);
      }
      else if (parent instanceof PsiVariable) {
        name = PsiFormatUtil.formatVariable((PsiVariable)parent, options, PsiSubstitutor.EMPTY);
      }
      else if (parent instanceof PsiClassInitializer) {
        PsiClass containingClass = ((PsiClassInitializer)parent).getContainingClass();
        String className = containingClass instanceof PsiAnonymousClass
                           ? QuickFixBundle.message("anonymous.class.presentation",
                                                    ((PsiAnonymousClass)containingClass).getBaseClassType().getPresentableText())
                           : containingClass != null ? containingClass.getName() : "unknown";
        name = QuickFixBundle.message("class.initializer.presentation", className);
      }
    }

    String modifierText = VisibilityUtil.toPresentableText(myModifier);

    return QuickFixBundle.message(myShouldHave ? "add.modifier.fix" : "remove.modifier.fix", name, modifierText);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("fix.modifiers.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    final PsiModifierList myModifierList = (PsiModifierList)startElement;
    PsiVariable variable = myVariable == null ? null : myVariable.getElement();
    return myModifierList.getManager().isInProject(myModifierList) &&
           myModifierList.hasExplicitModifier(myModifier) != myShouldHave &&
           (variable == null || variable.isValid());
  }

  private void changeModifierList (@NotNull PsiModifierList modifierList) {
    try {
      modifierList.setModifierProperty(myModifier, myShouldHave);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable("is null when called from inspection") Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    final PsiModifierList myModifierList = (PsiModifierList)startElement;
    final PsiVariable variable = myVariable == null ? null : myVariable.getElement();
    if (!FileModificationService.getInstance().preparePsiElementForWrite(myModifierList)) return;
    if (variable != null && !FileModificationService.getInstance().preparePsiElementForWrite(variable)) return;
    final List<PsiModifierList> modifierLists = new ArrayList<>();
    final PsiFile containingFile = myModifierList.getContainingFile();
    final PsiModifierList modifierList;
    if (variable != null && variable.isValid()) {
      ApplicationManager.getApplication().runWriteAction(() -> {
        try {
          variable.normalizeDeclaration();
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      });
      modifierList = variable.getModifierList();
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

      OverridingMethodsSearch.search((PsiMethod)owner, owner.getResolveScope(), true).forEach(
        new PsiElementProcessorAdapter<>(new PsiElementProcessor<PsiMethod>() {
          @Override
          public boolean execute(@NotNull PsiMethod inheritor) {
            PsiModifierList list = inheritor.getModifierList();
            if (inheritor.getManager().isInProject(inheritor) && PsiUtil.getAccessLevel(list) < accessLevel) {
              modifierLists.add(list);
            }
            return true;
          }
        }));
    }

    if (!modifierLists.isEmpty()) {
      if (Messages.showYesNoDialog(project,
                                   QuickFixBundle.message("change.inheritors.visibility.warning.text"),
                                   QuickFixBundle.message("change.inheritors.visibility.warning.title"),
                                   Messages.getQuestionIcon()) == Messages.YES) {
        ApplicationManager.getApplication().runWriteAction(() -> {
          if (!FileModificationService.getInstance().preparePsiElementsForWrite(modifierLists)) {
            return;
          }

          for (final PsiModifierList modifierList1 : modifierLists) {
            changeModifierList(modifierList1);
          }
        });
      }
    }

    ApplicationManager.getApplication().runWriteAction(() -> {
      changeModifierList(modifierList);
      if (myShouldHave && owner instanceof PsiMethod) {
        if (PsiModifier.ABSTRACT.equals(myModifier)) {
          final PsiMethod method = (PsiMethod)owner;
          final PsiClass aClass = method.getContainingClass();
          if (aClass != null && !aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
            PsiModifierList classModifierList = aClass.getModifierList();
            if (classModifierList != null) {
              changeModifierList(classModifierList);
            }
          }
        }
        else if (PsiModifier.PUBLIC.equals(myModifier) &&
                 ((PsiMethod)owner).getBody() != null &&
                 !((PsiMethod)owner).hasModifierProperty(PsiModifier.STATIC)) {
          PsiClass containingClass = ((PsiMethod)owner).getContainingClass();
          if (containingClass != null && containingClass.isInterface()) {
            modifierList.setModifierProperty(PsiModifier.DEFAULT, true);
          }
        }
      }
      UndoUtil.markPsiFileForUndo(containingFile);
    });
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

}
