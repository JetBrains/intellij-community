package com.intellij.refactoring.typeMigration.intentions;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.AllowedApiFilterExtension;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptor;
import com.intellij.refactoring.typeMigration.TypeEvaluator;
import com.intellij.refactoring.typeMigration.TypeMigrationReplacementUtil;
import com.intellij.refactoring.typeMigration.rules.AtomicConversionRule;
import com.intellij.refactoring.typeMigration.usageInfo.TypeMigrationUsageInfo;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Query;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.*;

import static com.intellij.psi.util.TypeConversionUtil.isBinaryOperatorApplicable;
import static com.intellij.util.ObjectUtils.assertNotNull;

/**
 * @author anna
 * @since 26-Aug-2009
 */
public class ConvertFieldToAtomicIntention extends PsiElementBaseIntentionAction implements LowPriorityAction {
  private static final Logger LOG = Logger.getInstance("#" + ConvertFieldToAtomicIntention.class.getName());

  private final Map<PsiType, String> myFromToMap = ContainerUtil.newHashMap();
  {
    myFromToMap.put(PsiType.INT, AtomicInteger.class.getName());
    myFromToMap.put(PsiType.LONG, AtomicLong.class.getName());
    myFromToMap.put(PsiType.BOOLEAN, AtomicBoolean.class.getName());
    myFromToMap.put(PsiType.INT.createArrayType(), AtomicIntegerArray.class.getName());
    myFromToMap.put(PsiType.LONG.createArrayType(), AtomicLongArray.class.getName());
  }

  @NotNull
  @Override
  public String getText() {
    return "Convert to atomic";
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    PsiVariable psiVariable = getVariable(element);
    if (psiVariable == null || psiVariable instanceof PsiResourceVariable) return false;
    if (psiVariable.getLanguage() != JavaLanguage.INSTANCE) return false;
    if (psiVariable.getTypeElement() == null) return false;
    if (!PsiUtil.isLanguageLevel5OrHigher(psiVariable)) return false;
    final PsiType psiType = psiVariable.getType();
    final PsiClass psiTypeClass = PsiUtil.resolveClassInType(psiType);
    if (psiTypeClass != null) {
      final String qualifiedName = psiTypeClass.getQualifiedName();
      if (qualifiedName != null) { //is already atomic
        if (myFromToMap.values().contains(qualifiedName) ||
            qualifiedName.equals(AtomicReference.class.getName()) ||
            qualifiedName.equals(AtomicReferenceArray.class.getName())) {
          return false;
        }
      }
    }
    else if (!myFromToMap.containsKey(psiType)) {
      return false;
    }
    return AllowedApiFilterExtension.isClassAllowed(AtomicReference.class.getName(), element);
  }

  private static PsiVariable getVariable(PsiElement element) {
    if (element instanceof PsiIdentifier) {
      final PsiElement parent = element.getParent();
      if (parent instanceof PsiLocalVariable || parent instanceof PsiField) {
        return (PsiVariable)parent;
      }
    }
    return null;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    final PsiVariable psiVariable = getVariable(element);
    LOG.assertTrue(psiVariable != null);

    final Query<PsiReference> refs = ReferencesSearch.search(psiVariable);

    final Set<PsiElement> elements = new HashSet<>();
    elements.add(element);
    for (PsiReference reference : refs) {
      elements.add(reference.getElement());
    }
    if (!FileModificationService.getInstance().preparePsiElementsForWrite(elements)) return;

    psiVariable.normalizeDeclaration();

    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    final PsiType fromType = psiVariable.getType();
    PsiClassType toType;
    final String atomicQualifiedName = myFromToMap.get(fromType);
    if (atomicQualifiedName != null) {
      final PsiClass atomicClass = psiFacade.findClass(atomicQualifiedName, GlobalSearchScope.allScope(project));
      if (atomicClass == null) {//show warning
        return;
      }
      toType = factory.createType(atomicClass);
    }
    else if (fromType instanceof PsiArrayType) {
      final PsiClass atomicReferenceArrayClass =
        psiFacade.findClass(AtomicReferenceArray.class.getName(), GlobalSearchScope.allScope(project));
      if (atomicReferenceArrayClass == null) {//show warning
        return;
      }
      final Map<PsiTypeParameter, PsiType> substitutor = ContainerUtil.newHashMap();
      final PsiTypeParameter[] typeParameters = atomicReferenceArrayClass.getTypeParameters();
      if (typeParameters.length == 1) {
        PsiType componentType = ((PsiArrayType)fromType).getComponentType();
        if (componentType instanceof PsiPrimitiveType) componentType = ((PsiPrimitiveType)componentType).getBoxedType(element);
        substitutor.put(typeParameters[0], componentType);
      }
      toType = factory.createType(atomicReferenceArrayClass, factory.createSubstitutor(substitutor));
    }
    else {
      final PsiClass atomicReferenceClass = psiFacade.findClass(AtomicReference.class.getName(), GlobalSearchScope.allScope(project));
      if (atomicReferenceClass == null) {//show warning
        return;
      }
      final Map<PsiTypeParameter, PsiType> substitutor = ContainerUtil.newHashMap();
      final PsiTypeParameter[] typeParameters = atomicReferenceClass.getTypeParameters();
      if (typeParameters.length == 1) {
        PsiType type = fromType;
        if (type instanceof PsiPrimitiveType) type = ((PsiPrimitiveType)fromType).getBoxedType(element);
        substitutor.put(typeParameters[0], type);
      }
      toType = factory.createType(atomicReferenceClass, factory.createSubstitutor(substitutor));
    }

    try {
      for (PsiReference reference : refs) {
        PsiElement psiElement = reference.getElement();
        if (psiElement instanceof PsiExpression) {
          final PsiElement parent = psiElement.getParent();
          if (parent instanceof PsiExpression && !(parent instanceof PsiReferenceExpression || parent instanceof PsiPolyadicExpression)) {
            psiElement = parent;
          }
          if (psiElement instanceof PsiBinaryExpression) {
            PsiBinaryExpression binary = (PsiBinaryExpression)psiElement;
            if (isBinaryOperatorApplicable(binary.getOperationTokenType(), binary.getLOperand(), binary.getROperand(), true)) {
              continue;
            }
          }
          else if (psiElement instanceof PsiAssignmentExpression) {
            final PsiAssignmentExpression assignment = (PsiAssignmentExpression)psiElement;
            final IElementType opSign = TypeConversionUtil.convertEQtoOperation(assignment.getOperationTokenType());
            if (opSign != null && isBinaryOperatorApplicable(opSign, assignment.getLExpression(), assignment.getRExpression(), true)) {
              continue;
            }
          }
          final TypeConversionDescriptor directConversion = AtomicConversionRule.findDirectConversion(psiElement, toType, fromType);
          if (directConversion != null) {
            TypeMigrationReplacementUtil.replaceExpression((PsiExpression)psiElement, project, directConversion, new TypeEvaluator(null, null));
          }
        }
      }

      PsiExpression initializer = psiVariable.getInitializer();
      if (initializer != null) {
        if (initializer instanceof PsiArrayInitializerExpression) {
          PsiExpression normalizedExpr =
            RefactoringUtil.createNewExpressionFromArrayInitializer((PsiArrayInitializerExpression)initializer, psiVariable.getType());
          initializer = (PsiExpression)initializer.replace(normalizedExpr);
        }
        final TypeConversionDescriptor directConversion = AtomicConversionRule.wrapWithNewExpression(toType, fromType, initializer, element);
        if (directConversion != null) {
          TypeMigrationReplacementUtil.replaceExpression(initializer, project, directConversion, new TypeEvaluator(null, null));
        }
      }
      else if (!assertNotNull(psiVariable.getModifierList()).hasModifierProperty(PsiModifier.FINAL)) {
        final PsiExpression newInitializer = factory.createExpressionFromText("new " + toType.getCanonicalText() + "()", psiVariable);
        if (psiVariable instanceof PsiLocalVariable) {
          ((PsiLocalVariable)psiVariable).setInitializer(newInitializer);
        }
        else if (psiVariable instanceof PsiField) {
          ((PsiField)psiVariable).setInitializer(newInitializer);
        }
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(psiVariable.getInitializer());
      }

      PsiElement replaced = assertNotNull(psiVariable.getTypeElement()).replace(factory.createTypeElement(toType));
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(replaced);

      if (psiVariable instanceof PsiField || CodeStyleSettingsManager.getSettings(project).GENERATE_FINAL_LOCALS) {
        final PsiModifierList modifierList = assertNotNull(psiVariable.getModifierList());
        modifierList.setModifierProperty(PsiModifier.FINAL, true);
        modifierList.setModifierProperty(PsiModifier.VOLATILE, false);
      }
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
