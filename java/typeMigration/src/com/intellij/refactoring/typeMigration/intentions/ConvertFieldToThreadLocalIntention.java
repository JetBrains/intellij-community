// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.typeMigration.intentions;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.BaseElementAtCaretIntentionAction;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.AllowedApiFilterExtension;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.typeMigration.TypeMigrationBundle;
import com.intellij.refactoring.typeMigration.TypeMigrationVariableTypeFixProvider;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class ConvertFieldToThreadLocalIntention extends BaseElementAtCaretIntentionAction implements LowPriorityAction {
  private static final Logger LOG = Logger.getInstance(ConvertFieldToThreadLocalIntention.class);

  @Override
  public @NotNull String getText() {
    return getFamilyName();
  }

  @Override
  public @NotNull String getFamilyName() {
    return TypeMigrationBundle.message("convert.to.threadlocal.family.name");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, @NotNull Editor editor, @NotNull PsiElement element) {
    if (!(element instanceof PsiIdentifier)) return false;
    PsiElement parent = element.getParent();
    if (!(parent instanceof PsiField field)) return false;
    if (field.getLanguage() != JavaLanguage.INSTANCE) return false;
    if (field.getTypeElement() == null) return false;
    final PsiType fieldType = field.getType();
    final PsiClass fieldTypeClass = PsiUtil.resolveClassInType(fieldType);
    if (fieldType instanceof PsiPrimitiveType && !PsiTypes.voidType().equals(fieldType) || fieldType instanceof PsiArrayType) return true;
    return fieldTypeClass != null && !Comparing.strEqual(fieldTypeClass.getQualifiedName(), ThreadLocal.class.getName())
           && AllowedApiFilterExtension.isClassAllowed(ThreadLocal.class.getName(), element);
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    final PsiField psiField = PsiTreeUtil.getParentOfType(element, PsiField.class);
    LOG.assertTrue(psiField != null);

    final PsiType fromType = psiField.getType();

    final PsiClassType toType = getMigrationTargetType(fromType, project, element);
    if (toType == null) return;

    if (!FileModificationService.getInstance().preparePsiElementsForWrite(psiField)) return;
    ConvertFieldToAtomicIntention.addExplicitInitializer(psiField);
    String toTypeCanonicalText = toType.getCanonicalText();
    TypeMigrationVariableTypeFixProvider.runTypeMigrationOnVariable(psiField, toType, editor, false, false);
    ConvertFieldToAtomicIntention.postProcessVariable(psiField, toTypeCanonicalText);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    final PsiField psiField = PsiTreeUtil.getParentOfType(getElement(editor, psiFile), PsiField.class);
    if (psiField == null) return IntentionPreviewInfo.EMPTY;
    PsiType type = psiField.getType();
    if (type == PsiTypes.nullType()) return IntentionPreviewInfo.EMPTY;
    String fieldName = psiField.getName();
    String presentableText = type.getPresentableText();
    String genericArg = presentableText;
    if (type instanceof PsiPrimitiveType) {
      genericArg = StringUtil.getShortName(Objects.requireNonNull(((PsiPrimitiveType)type).getBoxedTypeName()));
    }
    return new IntentionPreviewInfo.CustomDiff(JavaFileType.INSTANCE, null,
                                               presentableText + " " + fieldName,
                                               "ThreadLocal<" + genericArg + "> " + fieldName + " = ThreadLocal.withInitial(...)");
  }

  private static @Nullable PsiClassType getMigrationTargetType(@NotNull PsiType fromType, @NotNull Project project, @NotNull PsiElement context) {
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final PsiClass threadLocalClass = psiFacade.findClass(ThreadLocal.class.getName(), GlobalSearchScope.allScope(project));
    if (threadLocalClass == null) {//show warning
      return null;
    }
    final Map<PsiTypeParameter, PsiType> substitutor = new HashMap<>();
    final PsiTypeParameter[] typeParameters = threadLocalClass.getTypeParameters();
    if (typeParameters.length == 1) {
      PsiType type = fromType;
      if (fromType instanceof PsiPrimitiveType) type = ((PsiPrimitiveType)fromType).getBoxedType(context);
      substitutor.put(typeParameters[0], type);
    }
    PsiElementFactory factory = psiFacade.getElementFactory();
    return factory.createType(threadLocalClass, factory.createSubstitutor(substitutor));
  }
}
