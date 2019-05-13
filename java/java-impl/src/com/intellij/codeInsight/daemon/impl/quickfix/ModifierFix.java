// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.PsiElementProcessorAdapter;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
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
  private volatile String myName;
  private final SmartPsiElementPointer<PsiVariable> myVariable;

  public ModifierFix(PsiModifierList modifierList,
                     @PsiModifier.ModifierConstant @NotNull String modifier,
                     boolean shouldHave,
                     boolean showContainingClass) {
    super(modifierList);
    myModifier = modifier;
    myShouldHave = shouldHave;
    myShowContainingClass = showContainingClass;
    myName = format(null, modifierList, myShowContainingClass);
    myVariable = null;
  }

  public ModifierFix(@NotNull PsiModifierListOwner owner,
                     @PsiModifier.ModifierConstant @NotNull String modifier,
                     boolean shouldHave,
                     boolean showContainingClass) {
    super(owner.getModifierList());
    myModifier = modifier;
    myShouldHave = shouldHave;
    myShowContainingClass = showContainingClass;
    PsiVariable variable = owner instanceof PsiVariable ? (PsiVariable)owner : null;
    myName = format(variable, owner.getModifierList(), myShowContainingClass);
    myVariable = variable == null ? null : SmartPointerManager.getInstance(owner.getProject()).createSmartPsiElementPointer(variable);
  }

  private String format(PsiVariable variable, PsiModifierList modifierList, boolean showContainingClass) {
    String name = null;
    PsiElement parent = variable != null ? variable : modifierList != null ? modifierList.getParent() : null;
    if (parent instanceof PsiClass) {
      name = ((PsiClass)parent).getName();
    }
    else if (parent instanceof PsiJavaModule) {
      name = ((PsiJavaModule)parent).getName();
    }
    else if (parent instanceof PsiMethod) {
      int options = PsiFormatUtilBase.SHOW_NAME | (showContainingClass ? PsiFormatUtilBase.SHOW_CONTAINING_CLASS : 0);
      name = PsiFormatUtil.formatMethod((PsiMethod)parent, PsiSubstitutor.EMPTY, options, 0);
    }
    else if (parent instanceof PsiVariable) {
      int options = PsiFormatUtilBase.SHOW_NAME | (showContainingClass ? PsiFormatUtilBase.SHOW_CONTAINING_CLASS : 0);
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
    else if (parent instanceof PsiRequiresStatement) {
      name = "requires " + ((PsiRequiresStatement)parent).getModuleName();
    }

    String modifierText = VisibilityUtil.toPresentableText(myModifier);
    return QuickFixBundle.message(myShouldHave ? "add.modifier.fix" : "remove.modifier.fix", name, modifierText);
  }

  @NotNull
  @Override
  public String getText() {
    return myName;
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("fix.modifiers.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @Nullable Editor editor,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    final PsiModifierList modifierList = (PsiModifierList)startElement;
    PsiVariable variable = myVariable == null ? null : myVariable.getElement();
    boolean isAvailable = BaseIntentionAction.canModify(modifierList) &&
                          modifierList.hasExplicitModifier(myModifier) != myShouldHave &&
                          (variable == null || variable.isValid());

    if (isAvailable && myShowContainingClass && editor != null) {
      PsiElement elementUnderCaret = file.findElementAt(editor.getCaretModel().getOffset());
      if (elementUnderCaret != null) {
        if (PsiTreeUtil.isAncestor(modifierList.getParent(), elementUnderCaret, false)) {
          myName = format(variable, modifierList, false);
        }
      }
    }

    return isAvailable;
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    return isAvailable(project, file, null, startElement, endElement);
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
    PsiModifierList modifierList;
    final PsiVariable variable = myVariable == null ? null : myVariable.getElement();
    if (variable != null && variable.isValid()) {
      if (!FileModificationService.getInstance().preparePsiElementForWrite(variable)) return;
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
    } else {
      modifierList = (PsiModifierList)startElement;
    }
    if (!FileModificationService.getInstance().preparePsiElementForWrite(modifierList)) return;
    PsiElement owner = modifierList.getParent();
    if (owner instanceof PsiMethod) {
      PsiModifierList copy = (PsiModifierList)modifierList.copy();
      changeModifierList(copy);
      final int accessLevel = PsiUtil.getAccessLevel(copy);
      if (accessLevel != PsiUtil.getAccessLevel(modifierList)) {
        final List<PsiModifierList> modifierLists = new ArrayList<>();
        ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
          OverridingMethodsSearch.search((PsiMethod)owner, owner.getResolveScope(), true).forEach(
            new PsiElementProcessorAdapter<>(new PsiElementProcessor<PsiMethod>() {
              @Override
              public boolean execute(@NotNull PsiMethod inheritor) {
                PsiModifierList list = inheritor.getModifierList();
                if (BaseIntentionAction.canModify(inheritor) && PsiUtil.getAccessLevel(list) < accessLevel) {
                  modifierLists.add(list);
                }
                return true;
              }
            }));
        }, InspectionsBundle.message("psi.search.overriding.progress"), true, project);
        if (!modifierLists.isEmpty() && Messages.showYesNoDialog(project,
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
    }

    ApplicationManager.getApplication().runWriteAction(() -> {
      changeModifierList(modifierList);
      if (myShouldHave && owner instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod)owner;
        if (PsiModifier.ABSTRACT.equals(myModifier)) {
          final PsiClass aClass = method.getContainingClass();
          if (aClass != null && !aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
            PsiModifierList classModifierList = aClass.getModifierList();
            if (classModifierList != null) {
              changeModifierList(classModifierList);
            }
          }
        }
        else if (PsiModifier.PUBLIC.equals(myModifier) &&
                 method.getBody() != null &&
                 !method.hasModifierProperty(PsiModifier.STATIC)) {
          PsiClass containingClass = method.getContainingClass();
          if (containingClass != null && containingClass.isInterface()) {
            modifierList.setModifierProperty(PsiModifier.DEFAULT, true);
          }
        }
        else if (PsiModifier.STATIC.equals(myModifier)) {
          if (method.hasModifierProperty(PsiModifier.DEFAULT)) {
            modifierList.setModifierProperty(PsiModifier.DEFAULT, false);
          }
          else if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
            PsiUtil.setModifierProperty(method, PsiModifier.ABSTRACT, false);
            CreateFromUsageUtils.setupMethodBody(method);
          }
        }
      }
      UndoUtil.markPsiFileForUndo(modifierList.getContainingFile());
    });
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}