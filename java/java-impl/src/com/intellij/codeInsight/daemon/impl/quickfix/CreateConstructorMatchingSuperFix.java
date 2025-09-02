// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.generation.*;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.ide.util.MemberChooser;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;


public class CreateConstructorMatchingSuperFix extends BaseIntentionAction {
  private static final Logger LOG = Logger.getInstance(CreateConstructorMatchingSuperFix.class);

  private final PsiClass myClass;

  public CreateConstructorMatchingSuperFix(@NotNull PsiClass aClass) {
    myClass = aClass;
    setText(QuickFixBundle.message("create.constructor.matching.super"));
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("create.constructor.matching.super");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    if (!myClass.isValid() || !canModify(myClass)) return false;
    PsiClass base = myClass.getSuperClass();
    if (base == null) return false;
    PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(base, myClass, PsiSubstitutor.EMPTY);
    for (PsiMethod baseConstructor: base.getConstructors()) {
      if (PsiUtil.isAccessible(baseConstructor, myClass, null)) {
        PsiMethod derived = GenerateMembersUtil.substituteGenericMethod(baseConstructor, substitutor, myClass);
        String className = myClass.getName();
        LOG.assertTrue(className != null);
        derived.setName(className);
        if (myClass.findMethodBySignature(derived, false) == null) {
          return true;
        }
      }
    }
    return false;
    
  }

  @Override
  public void invoke(final @NotNull Project project, final Editor editor, PsiFile psiFile) {
    if (!FileModificationService.getInstance().prepareFileForWrite(myClass.getContainingFile())) return;
    chooseConstructor2Delegate(project, editor, myClass);
  }
  
  private static PsiMethodMember @NotNull[] calculateConstructors(PsiClass aClass) {
    PsiClass baseClass = aClass.getSuperClass();
    LOG.assertTrue(baseClass != null);
    PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(baseClass, aClass, PsiSubstitutor.EMPTY);
    List<PsiMethodMember> baseConstructors = new ArrayList<>();
    PsiMethod[] baseConstrs = baseClass.getConstructors();
    for (PsiMethod baseConstr : baseConstrs) {
      if (PsiUtil.isAccessible(baseConstr, aClass, aClass)) baseConstructors.add(new PsiMethodMember(baseConstr, substitutor));
    }

    PsiMethodMember[] constructors = baseConstructors.toArray(new PsiMethodMember[0]);
    if (constructors.length == 0) {
      constructors = new PsiMethodMember[baseConstrs.length];
      for (int i = 0; i < baseConstrs.length; i++) {
        constructors[i] = new PsiMethodMember(baseConstrs[i], substitutor);
      }
    }
    return constructors;
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    PsiClass targetClass = PsiTreeUtil.findSameElementInCopy(myClass, psiFile);
    insertConstructor(project, editor, targetClass, false, calculateConstructors(targetClass));
    return IntentionPreviewInfo.DIFF;
  }

  public static void chooseConstructor2Delegate(final Project project,
                                                final Editor editor,
                                                final PsiClass targetClass) {
    PsiMethodMember[] constructors = calculateConstructors(targetClass);
    LOG.assertTrue(constructors.length >=1); // Otherwise we won't have been messing with all this stuff
    boolean isCopyJavadoc;
    if (constructors.length > 1 && !ApplicationManager.getApplication().isUnitTestMode()) {
      MemberChooser<PsiMethodMember> chooser = new MemberChooser<>(constructors, false, true, project);
      chooser.setTitle(QuickFixBundle.message("super.class.constructors.chooser.title"));
      chooser.show();
      if (chooser.getExitCode() != DialogWrapper.OK_EXIT_CODE) return;
      constructors = chooser.getSelectedElements(new PsiMethodMember[0]);
      isCopyJavadoc = chooser.isCopyJavadoc();
    }
    else {
      isCopyJavadoc = true;
    }

    final PsiMethodMember[] constructors1 = constructors;
    
    ApplicationManager.getApplication().runWriteAction (
      () -> {
        insertConstructor(project, editor, targetClass, isCopyJavadoc, constructors1);

        UndoUtil.markPsiFileForUndo(targetClass.getContainingFile());
      }
    );
  }

  private static void insertConstructor(Project project,
                                        Editor editor,
                                        PsiClass targetClass,
                                        boolean isCopyJavadoc,
                                        PsiMethodMember[] constructors) {
    try {
      if (targetClass.getLBrace() == null) {
        PsiClass psiClass = JavaPsiFacade.getElementFactory(targetClass.getProject()).createClass("X");
        targetClass.addRangeAfter(psiClass.getLBrace(), psiClass.getRBrace(), targetClass.getLastChild());
      }
      JVMElementFactory factory = JVMElementFactories.getFactory(targetClass.getLanguage(), project);
      CodeStyleManager formatter = CodeStyleManager.getInstance(project);
      PsiMethod derived = null;
      for (PsiMethodMember candidate : constructors) {
        PsiMethod base = candidate.getElement();
        derived = GenerateMembersUtil.substituteGenericMethod(base, candidate.getSubstitutor(), targetClass);

        if (!isCopyJavadoc) {
          final PsiDocComment docComment = derived.getDocComment();
          if (docComment != null) {
            docComment.delete();
          }
        }

        final String targetClassName = targetClass.getName();
        LOG.assertTrue(targetClassName != null, targetClass);
        derived.setName(targetClassName);

        ConstructorBodyGenerator generator = ConstructorBodyGenerator.INSTANCE.forLanguage(derived.getLanguage());
        if (generator != null) {
          StringBuilder buffer = new StringBuilder();
          generator.start(buffer, derived.getName(), PsiParameter.EMPTY_ARRAY);
          generator.generateSuperCallIfNeeded(buffer, derived.getParameterList().getParameters());
          generator.finish(buffer);
          PsiMethod stub = factory.createMethodFromText(buffer.toString(), targetClass);
          derived.getBody().replace(stub.getBody());
        }
        derived = (PsiMethod)formatter.reformat(derived);
        derived = (PsiMethod)JavaCodeStyleManager.getInstance(project).shortenClassReferences(derived);
        if (targetClass.hasModifier(JvmModifier.FINAL) && derived.hasModifier(JvmModifier.PROTECTED)) {
          derived.getModifierList().setModifierProperty(PsiModifier.PROTECTED, false);
        }
        PsiGenerationInfo<PsiMethod> info = OverrideImplementUtil.createGenerationInfo(derived);
        info.insert(targetClass, null, true);
        derived = info.getPsiMember();
      }
      if (derived != null) {
        editor.getCaretModel().moveToOffset(derived.getTextRange().getStartOffset());
        editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
