// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.actions.IntentionActionWithFixAllOption;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
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
import com.intellij.psi.util.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ModifierFix extends LocalQuickFixAndIntentionActionOnPsiElement implements IntentionActionWithFixAllOption {
  private static final Logger LOG = Logger.getInstance(ModifierFix.class);

  @PsiModifier.ModifierConstant private final String myModifier;
  private final boolean myShouldHave;
  private final boolean myShowContainingClass;
  private volatile @IntentionName String myName;
  private final boolean myStartInWriteAction;

  public ModifierFix(@NotNull PsiModifierList modifierList,
                     @PsiModifier.ModifierConstant @NotNull String modifier,
                     boolean shouldHave,
                     boolean showContainingClass) {
    super(ObjectUtils.tryCast(modifierList.getParent(), PsiModifierListOwner.class));
    myModifier = modifier;
    myShouldHave = shouldHave;
    myShowContainingClass = showContainingClass;
    myName = format(null, modifierList, myShowContainingClass);
    myStartInWriteAction = !(modifierList.getParent() instanceof PsiMethod) || AccessModifier.fromPsiModifier(modifier) == null;
  }

  public ModifierFix(@NotNull PsiModifierListOwner owner,
                     @PsiModifier.ModifierConstant @NotNull String modifier,
                     boolean shouldHave,
                     boolean showContainingClass) {
    super(owner);
    myModifier = modifier;
    myShouldHave = shouldHave;
    myShowContainingClass = showContainingClass;
    PsiVariable variable = owner instanceof PsiVariable ? (PsiVariable)owner : null;
    myName = format(variable, owner.getModifierList(), myShowContainingClass);
    myStartInWriteAction = !(owner instanceof PsiMethod) || AccessModifier.fromPsiModifier(modifier) == null;
  }

  private @IntentionName @NotNull String format(PsiVariable variable, PsiModifierList modifierList, boolean showContainingClass) {
    String name = null;
    PsiElement parent = variable != null ? variable : modifierList != null ? modifierList.getParent() : null;
    if (parent instanceof PsiClass psiClass) {
      name = psiClass.getName();
    }
    else if (parent instanceof PsiJavaModule module) {
      name = module.getName();
    }
    else if (parent instanceof PsiMethod method) {
      int options = PsiFormatUtilBase.SHOW_NAME | (showContainingClass ? PsiFormatUtilBase.SHOW_CONTAINING_CLASS : 0);
      name = PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, options, 0);
    }
    else if (parent instanceof PsiVariable var) {
      int options = PsiFormatUtilBase.SHOW_NAME | (showContainingClass ? PsiFormatUtilBase.SHOW_CONTAINING_CLASS : 0);
      name = PsiFormatUtil.formatVariable(var, options, PsiSubstitutor.EMPTY);
    }
    else if (parent instanceof PsiClassInitializer initializer) {
      PsiClass containingClass = initializer.getContainingClass();
      String className = containingClass instanceof PsiAnonymousClass
                         ? QuickFixBundle.message("anonymous.class.presentation",
                                                  ((PsiAnonymousClass)containingClass).getBaseClassType().getPresentableText())
                         : containingClass != null ? containingClass.getName() : "unknown";
      name = QuickFixBundle.message("class.initializer.presentation", className);
    }
    else if (parent instanceof PsiRequiresStatement requiresStatement) {
      name = "requires " + requiresStatement.getModuleName();
    }

    String modifierText = VisibilityUtil.toPresentableText(myModifier);
    return QuickFixBundle.message(myShouldHave ? "add.modifier.fix" : "remove.modifier.fix", name, modifierText);
  }

  @NotNull
  @Override
  public String getText() {
    return myName;
  }

  @Override
  public boolean belongsToMyFamily(@NotNull IntentionActionWithFixAllOption action) {
    return action instanceof ModifierFix modifierFix &&
           modifierFix.myModifier.equals(myModifier) &&
           modifierFix.myShouldHave == myShouldHave;
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return myShouldHave ? QuickFixBundle.message("add.modifier.fix.family", myModifier)
                        : QuickFixBundle.message("remove.modifier.fix.family", myModifier);
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @Nullable Editor editor,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    final PsiModifierList modifierList = ((PsiModifierListOwner)startElement).getModifierList();
    if (modifierList == null) return false;
    PsiFile containingFile = modifierList.getContainingFile();
    if (containingFile == null) return false;
    if (containingFile.getVirtualFile() == null) return false;
    PsiVariable variable = ObjectUtils.tryCast(startElement, PsiVariable.class);
    boolean isAvailable = BaseIntentionAction.canModify(modifierList) &&
                          modifierList.hasExplicitModifier(myModifier) != myShouldHave &&
                          !(variable instanceof SyntheticElement) &&
                          (variable == null || variable.isValid());

    if (isAvailable && myShowContainingClass && editor != null) {
      PsiElement elementUnderCaret = file.findElementAt(editor.getCaretModel().getOffset());
      if (elementUnderCaret != null) {
        if (PsiTreeUtil.isAncestor(startElement, elementUnderCaret, false)) {
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
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    PsiModifierListOwner owner = PsiTreeUtil.findSameElementInCopy((PsiModifierListOwner)getStartElement(), file);
    updateModifier(owner);
    return IntentionPreviewInfo.DIFF;
  }

  @Override
  public @Nullable PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
    return getStartElement().getContainingFile();
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    PsiModifierListOwner owner = (PsiModifierListOwner)startElement;
    if (myStartInWriteAction) {
      updateModifier(owner);
      return;
    }
    if (!FileModificationService.getInstance().preparePsiElementForWrite(startElement)) return;
    PsiModifierList modifierList = owner.getModifierList();
    assert modifierList != null;
    updateAccessInHierarchy(project, modifierList, owner);
    ApplicationManager.getApplication().runWriteAction(() -> {
      updateModifier(owner);
      UndoUtil.markPsiFileForUndo(modifierList.getContainingFile());
    });
  }

  protected void updateModifier(PsiModifierListOwner owner) {
    PsiVariable variable = ObjectUtils.tryCast(owner, PsiVariable.class);
    PsiModifierList modifierList;
    if (variable != null && variable.isValid()) {
      variable.normalizeDeclaration();
      modifierList = variable.getModifierList();
    } else {
      modifierList = owner.getModifierList();
    }
    if (modifierList == null) return;
    changeModifierList(modifierList);
    if (myShouldHave && owner instanceof final PsiMethod method) {
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
  }

  private void updateAccessInHierarchy(@NotNull Project project, PsiModifierList modifierList, PsiElement owner) {
    if (owner instanceof PsiMethod method) {
      PsiModifierList copy = (PsiModifierList)modifierList.copy();
      changeModifierList(copy);
      final int accessLevel = PsiUtil.getAccessLevel(copy);
      if (accessLevel != PsiUtil.getAccessLevel(modifierList)) {
        final List<PsiModifierList> modifierLists = new ArrayList<>();
        ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
          ReadAction.run(() -> {
            OverridingMethodsSearch.search(method, method.getResolveScope(), true).forEach(
              new PsiElementProcessorAdapter<>(new PsiElementProcessor<>() {
                @Override
                public boolean execute(@NotNull PsiMethod inheritor) {
                  PsiModifierList list = inheritor.getModifierList();
                  if (BaseIntentionAction.canModify(inheritor) && PsiUtil.getAccessLevel(list) < accessLevel) {
                    modifierLists.add(list);
                  }
                  return true;
                }
              }));
          });
        }, JavaBundle.message("psi.search.overriding.progress"), true, project);
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
  }

  @Override
  public boolean startInWriteAction() {
    return myStartInWriteAction;
  }
}