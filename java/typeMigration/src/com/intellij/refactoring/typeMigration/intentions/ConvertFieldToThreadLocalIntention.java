/*
 * User: anna
 * Date: 26-Aug-2009
 */
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
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.AllowedApiFilterExtension;
import com.intellij.psi.impl.PsiDiamondTypeUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptor;
import com.intellij.refactoring.typeMigration.TypeMigrationLabeler;
import com.intellij.refactoring.typeMigration.TypeMigrationReplacementUtil;
import com.intellij.refactoring.typeMigration.TypeMigrationRules;
import com.intellij.refactoring.typeMigration.rules.ThreadLocalConversionRule;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Query;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.intellij.util.ObjectUtils.assertNotNull;

public class ConvertFieldToThreadLocalIntention extends PsiElementBaseIntentionAction implements LowPriorityAction {
  private static final Logger LOG = Logger.getInstance("#" + ConvertFieldToThreadLocalIntention.class.getName());

  @NotNull
  @Override
  public String getText() {
    return "Convert to ThreadLocal";
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return getText();
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
    if (fieldType instanceof PsiPrimitiveType && fieldType != PsiType.VOID || fieldType instanceof PsiArrayType) return true;
    return fieldTypeClass != null && !Comparing.strEqual(fieldTypeClass.getQualifiedName(), ThreadLocal.class.getName())
           && AllowedApiFilterExtension.isClassAllowed(ThreadLocal.class.getName(), element);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    final PsiField psiField = PsiTreeUtil.getParentOfType(element, PsiField.class);
    LOG.assertTrue(psiField != null);
    final Query<PsiReference> refs = ReferencesSearch.search(psiField);

    final Set<PsiElement> elements = new HashSet<PsiElement>();
    elements.add(element);
    for (PsiReference reference : refs) {
      elements.add(reference.getElement());
    }
    if (!FileModificationService.getInstance().preparePsiElementsForWrite(elements)) return;
    psiField.normalizeDeclaration();

    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    final PsiType fromType = psiField.getType();

    final PsiClass threadLocalClass = psiFacade.findClass(ThreadLocal.class.getName(), GlobalSearchScope.allScope(project));
    if (threadLocalClass == null) {//show warning
      return;
    }
    final Map<PsiTypeParameter, PsiType> substitutor = ContainerUtil.newHashMap();
    final PsiTypeParameter[] typeParameters = threadLocalClass.getTypeParameters();
    if (typeParameters.length == 1) {
      PsiType type = fromType;
      if (fromType instanceof PsiPrimitiveType) type = ((PsiPrimitiveType)fromType).getBoxedType(element);
      substitutor.put(typeParameters[0], type);
    }
    final PsiClassType toType = factory.createType(threadLocalClass, factory.createSubstitutor(substitutor));

    try {
      final TypeMigrationRules rules = new TypeMigrationRules(fromType);
      rules.setMigrationRootType(toType);
      rules.setBoundScope(GlobalSearchScope.fileScope(element.getContainingFile()));
      final TypeMigrationLabeler labeler = new TypeMigrationLabeler(rules);
      labeler.getMigratedUsages(false, psiField);
      for (PsiReference reference : refs) {
        PsiElement psiElement = reference.getElement();
        if (psiElement instanceof PsiExpression) {
          final PsiElement parent = psiElement.getParent();
          if (parent instanceof PsiExpression && !(parent instanceof PsiReferenceExpression || parent instanceof PsiPolyadicExpression)) {
            psiElement = parent;
          }
          final TypeConversionDescriptor conversion = ThreadLocalConversionRule.findDirectConversion(psiElement, toType, fromType, labeler);
          if (conversion != null) {
            TypeMigrationReplacementUtil.replaceExpression((PsiExpression)psiElement, project, conversion);
          }
        }
      }

      PsiExpression initializer = psiField.getInitializer();
      if (initializer == null) {
        final PsiType type = psiField.getType();
        String initializerText = null;
        if (type == PsiType.BOOLEAN) {
          initializerText = "false";
        }
        else if (type instanceof PsiPrimitiveType) {
          initializerText = "0";
        }
        if (initializerText != null) {
          psiField.setInitializer(factory.createExpressionFromText(initializerText, psiField));
          initializer = psiField.getInitializer();
        }
      }
      if (initializer != null) {
        if (initializer instanceof PsiArrayInitializerExpression) {
          PsiExpression normalizedExpr =
            RefactoringUtil.createNewExpressionFromArrayInitializer((PsiArrayInitializerExpression)initializer, psiField.getType());
          initializer = (PsiExpression)initializer.replace(normalizedExpr);
        }
        final TypeConversionDescriptor conversion = ThreadLocalConversionRule.wrapWithNewExpression(toType, fromType, initializer);
        TypeMigrationReplacementUtil.replaceExpression(initializer, project, conversion);
        CodeStyleManager.getInstance(project).reformat(psiField);
      }
      else if (!assertNotNull(psiField.getModifierList()).hasModifierProperty(PsiModifier.FINAL)) {
        final String text = "new " + PsiDiamondTypeUtil.getCollapsedType(toType, psiField) + "()";
        final PsiExpression newInitializer = factory.createExpressionFromText(text, psiField);
        psiField.setInitializer(newInitializer);
      }

      assertNotNull(psiField.getTypeElement()).replace(factory.createTypeElement(toType));

      final PsiModifierList modifierList = assertNotNull(psiField.getModifierList());
      modifierList.setModifierProperty(PsiModifier.FINAL, true);
      modifierList.setModifierProperty(PsiModifier.VOLATILE, false);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
