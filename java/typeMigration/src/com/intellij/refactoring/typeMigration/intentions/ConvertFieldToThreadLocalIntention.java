package com.intellij.refactoring.typeMigration.intentions;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.impl.AllowedApiFilterExtension;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.typeMigration.TypeMigrationVariableTypeFixProvider;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class ConvertFieldToThreadLocalIntention extends PsiElementBaseIntentionAction implements LowPriorityAction {
  private static final Logger LOG = Logger.getInstance(ConvertFieldToThreadLocalIntention.class);

  @NotNull
  @Override
  public String getText() {
    //noinspection DialogTitleCapitalization
    return getFamilyName();
  }

  @NotNull
  @Override
  public String getFamilyName() {
    //noinspection DialogTitleCapitalization
    return "Convert to ThreadLocal";
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    if (!(element instanceof PsiIdentifier)) return false;
    final PsiField psiField = PsiTreeUtil.getParentOfType(element, PsiField.class);
    if (psiField == null) return false;
    if (psiField.getLanguage() != JavaLanguage.INSTANCE) return false;
    if (psiField.getTypeElement() == null) return false;
    final PsiType fieldType = psiField.getType();
    final PsiClass fieldTypeClass = PsiUtil.resolveClassInType(fieldType);
    if (fieldType instanceof PsiPrimitiveType && !PsiType.VOID.equals(fieldType) || fieldType instanceof PsiArrayType) return true;
    return fieldTypeClass != null && !Comparing.strEqual(fieldTypeClass.getQualifiedName(), ThreadLocal.class.getName())
           && AllowedApiFilterExtension.isClassAllowed(ThreadLocal.class.getName(), element);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
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

  @Nullable
  private static PsiClassType getMigrationTargetType(@NotNull PsiType fromType, @NotNull Project project, @NotNull PsiElement context) {
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final PsiClass threadLocalClass = psiFacade.findClass(ThreadLocal.class.getName(), GlobalSearchScope.allScope(project));
    if (threadLocalClass == null) {//show warning
      return null;
    }
    final Map<PsiTypeParameter, PsiType> substitutor = ContainerUtil.newHashMap();
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
