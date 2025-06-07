// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.light.LightRecordField;
import com.intellij.psi.util.*;
import com.intellij.refactoring.JavaRefactoringFactory;
import com.intellij.refactoring.changeSignature.JavaChangeSignatureDialog;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class VariableTypeFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  private static final Logger LOG = Logger.getInstance(VariableTypeFix.class);

  private final PsiType myReturnType;
  protected final String myName;

  protected VariableTypeFix(@NotNull PsiVariable variable, @NotNull PsiType toReturn) {
    super(variable);
    myReturnType = PsiTypesUtil.removeExternalAnnotations(GenericsUtil.getVariableTypeByExpressionType(toReturn));
    myName = variable.getName();
  }

  @Override
  public @NotNull String getText() {
    PsiType type = getReturnType();
    PsiElement startElement = getStartElement();
    String typeName = startElement == null ? "?" : JavaElementKind.fromElement(startElement).lessDescriptive().subject();
    return QuickFixBundle.message("fix.variable.type.text",
                                  typeName,
                                  myName,
                                  type == null || !type.isValid() ? "???" : type.getPresentableText());
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("fix.variable.type.family");
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile psiFile,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    final PsiVariable myVariable = (PsiVariable)startElement;
    PsiType type = getReturnType();
    return myVariable.getTypeElement() != null
           && BaseIntentionAction.canModify(myVariable)
           && type != null && type.isValid()
           && !LambdaUtil.notInferredType(type)
           && !TypeConversionUtil.isNullType(type)
           && !TypeConversionUtil.isVoidType(type);
  }

  @Override
  public void invoke(final @NotNull Project project,
                     final @NotNull PsiFile psiFile,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    final PsiVariable myVariable = (PsiVariable)startElement;
    if (changeMethodSignatureIfNeeded(myVariable)) return;
    WriteCommandAction.writeCommandAction(project, psiFile).withName(getText()).run(() -> {
      try {
        myVariable.normalizeDeclaration();
        final PsiTypeElement typeElement = myVariable.getTypeElement();
        LOG.assertTrue(typeElement != null, myVariable.getClass());
        final PsiTypeElement newTypeElement =
          JavaPsiFacade.getElementFactory(psiFile.getProject()).createTypeElement(getReturnType());
        typeElement.replace(newTypeElement);
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(myVariable);
        UndoUtil.markPsiFileForUndo(psiFile);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    });
  }

  private boolean changeMethodSignatureIfNeeded(PsiVariable myVariable) {
    PsiParameter myParameter = null;
    PsiMethod method = null;
    if (myVariable instanceof PsiParameter) {
      myParameter = (PsiParameter)myVariable;
      method = ObjectUtils.tryCast(((PsiParameter)myVariable).getDeclarationScope(), PsiMethod.class);
    }
    else if (myVariable instanceof PsiField) {
      PsiRecordComponent component = JavaPsiRecordUtil.getComponentForField((PsiField)myVariable);
      PsiClass aClass = ((PsiField)myVariable).getContainingClass();
      if (component != null && aClass != null) {
        method = JavaPsiRecordUtil.findCanonicalConstructor(aClass);
        if (method != null) {
          int index = ArrayUtil.indexOf(aClass.getRecordComponents(), component);
          PsiParameter parameter = method.getParameterList().getParameter(index);
          if (parameter != null && parameter.getName().equals(myVariable.getName())) {
            myParameter = parameter;
          }
        }
      }
    }
    if (method == null || myParameter == null) return false;
    changeSignature(myVariable, myParameter, method);
    return true;
  }

  private void changeSignature(PsiVariable myVariable, PsiParameter myParameter, PsiMethod method) {
    final PsiMethod psiMethod = SuperMethodWarningUtil.checkSuperMethod(method);
    if (psiMethod == null) return;
    final int parameterIndex = method.getParameterList().getParameterIndex(myParameter);
    if (!FileModificationService.getInstance().prepareFileForWrite(psiMethod.getContainingFile())) return;
    final ArrayList<ParameterInfoImpl> infos = new ArrayList<>();
    int i = 0;
    for (PsiParameter parameter : psiMethod.getParameterList().getParameters()) {
      final boolean changeType = i == parameterIndex;
      infos.add(ParameterInfoImpl.create(i++)
                  .withName(parameter.getName())
                  .withType(changeType ? getReturnType() : parameter.getType()));
    }

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      final JavaChangeSignatureDialog dialog = new JavaChangeSignatureDialog(psiMethod.getProject(), psiMethod, false, myVariable);
      dialog.setParameterInfos(infos);
      dialog.show();
    }
    else {
      ParameterInfoImpl @NotNull [] parameterInfo = infos.toArray(new ParameterInfoImpl[0]);
      var processor = JavaRefactoringFactory.getInstance(psiMethod.getProject())
        .createChangeSignatureProcessor(psiMethod, false, null, psiMethod.getName(), psiMethod.getReturnType(), parameterInfo, null, null,
                                        null, null);
      processor.run();
    }
  }

  protected PsiType getReturnType() {
    return myReturnType;
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    PsiVariable variable = (PsiVariable)getStartElement();
    if (variable instanceof LightRecordField) {
      variable = ((LightRecordField)variable).getRecordComponent();
    }
    PsiFile containingFile = variable.getContainingFile();
    if (containingFile == psiFile.getOriginalFile()) {
      PsiVariable varCopy = PsiTreeUtil.findSameElementInCopy(variable, psiFile);
      PsiTypeElement typeElement = varCopy.getTypeElement();
      if (typeElement != null) {
        typeElement.replace(PsiElementFactory.getInstance(project).createTypeElement(myReturnType));
        return IntentionPreviewInfo.DIFF;
      }
    }
    PsiType oldType = variable.getType();
    PsiModifierList modifiers = variable.getModifierList();
    String modifiersText = modifiers == null ? "" : StreamEx.of(PsiModifier.MODIFIERS).filter(modifiers::hasExplicitModifier)
      .map(mod -> mod + " ").joining();
    String oldTypeText = oldType.getPresentableText() + " ";
    String newTypeText = myReturnType.getPresentableText() + " ";
    String name = variable.getName();
    String initializer = variable.hasInitializer() ? " = ...;" : ";";
    String origText = modifiersText + oldTypeText + name + initializer;
    String newText = modifiersText + newTypeText + name + initializer;
    return new IntentionPreviewInfo.CustomDiff(JavaFileType.INSTANCE, containingFile.getName(), origText, newText);
  }
}
