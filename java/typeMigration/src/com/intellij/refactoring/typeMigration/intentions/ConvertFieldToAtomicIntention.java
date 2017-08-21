package com.intellij.refactoring.typeMigration.intentions;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.impl.AllowedApiFilterExtension;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.typeMigration.TypeMigrationVariableTypeFixProvider;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.atomic.*;

import static com.intellij.util.ObjectUtils.assertNotNull;

/**
 * @author anna
 * @since 26-Aug-2009
 */
public class ConvertFieldToAtomicIntention extends PsiElementBaseIntentionAction implements LowPriorityAction {
  private static final Logger LOG = Logger.getInstance(ConvertFieldToAtomicIntention.class);

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
    final PsiVariable var = getVariable(element);
    LOG.assertTrue(var != null);

    final PsiType fromType = var.getType();
    PsiClassType toType = getMigrationTargetType(project, element, fromType);
    if (toType == null) return;

    if (!FileModificationService.getInstance().preparePsiElementsForWrite(var)) return;
    addExplicitInitializer(var);
    String toTypeCanonicalText = toType.getCanonicalText();
    TypeMigrationVariableTypeFixProvider.runTypeMigrationOnVariable(var, toType, editor, false, false);
    postProcessVariable(var, toTypeCanonicalText);
  }

  static void addExplicitInitializer(@NotNull PsiVariable var) {
    PsiExpression currentInitializer = var.getInitializer();
    if (currentInitializer != null) return;
    final PsiType type = var.getType();
    String initializerText = null;
    if (PsiType.BOOLEAN.equals(type)) {
      initializerText = "false";
    }
    else if (type instanceof PsiPrimitiveType) {
      initializerText = "0";
    }
    if (initializerText != null) {
      String finalInitializerText = initializerText;
      WriteAction.run(() -> {
        PsiExpression initializer = JavaPsiFacade.getElementFactory(var.getProject()).createExpressionFromText(finalInitializerText, var);
        if (var instanceof PsiLocalVariable) {
          ((PsiLocalVariable)var).setInitializer(initializer);
        }
        else if (var instanceof PsiField) {
          ((PsiField)var).setInitializer(initializer);
        }
      });
    }
  }

  static void postProcessVariable(@NotNull PsiVariable var, @NotNull String toType) {

    Project project = var.getProject();
    if (var instanceof PsiField || CodeStyleSettingsManager.getSettings(project).getCustomSettings(JavaCodeStyleSettings.class).GENERATE_FINAL_LOCALS) {
      PsiModifierList modifierList = assertNotNull(var.getModifierList());
      WriteAction.run(() -> {
        if (var.getInitializer() == null) {
          final PsiExpression newInitializer = JavaPsiFacade.getElementFactory(project).createExpressionFromText("new " + toType + "()", var);
          if (var instanceof PsiLocalVariable) {
            ((PsiLocalVariable)var).setInitializer(newInitializer);
          }
          else if (var instanceof PsiField) {
            ((PsiField)var).setInitializer(newInitializer);
          }
          JavaCodeStyleManager.getInstance(var.getProject()).shortenClassReferences(var.getInitializer());
        }

        modifierList.setModifierProperty(PsiModifier.FINAL, true);
        modifierList.setModifierProperty(PsiModifier.VOLATILE, false);

        JavaCodeStyleManager.getInstance(project).shortenClassReferences(var);
        CodeStyleManager.getInstance(project).reformat(var);
      });
    }
  }

  @Nullable
  private PsiClassType getMigrationTargetType(@NotNull Project project,
                                              @NotNull PsiElement element,
                                              @NotNull PsiType fromType) {
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    PsiElementFactory factory = psiFacade.getElementFactory();
    final String atomicQualifiedName = myFromToMap.get(fromType);
    if (atomicQualifiedName != null) {
      final PsiClass atomicClass = psiFacade.findClass(atomicQualifiedName, GlobalSearchScope.allScope(project));
      if (atomicClass == null) {//show warning
        return null;
      }
      return factory.createType(atomicClass);
    }
    else if (fromType instanceof PsiArrayType) {
      final PsiClass atomicReferenceArrayClass =
        psiFacade.findClass(AtomicReferenceArray.class.getName(), GlobalSearchScope.allScope(project));
      if (atomicReferenceArrayClass == null) {//show warning
        return null;
      }
      final Map<PsiTypeParameter, PsiType> substitutor = ContainerUtil.newHashMap();
      final PsiTypeParameter[] typeParameters = atomicReferenceArrayClass.getTypeParameters();
      if (typeParameters.length == 1) {
        PsiType componentType = ((PsiArrayType)fromType).getComponentType();
        if (componentType instanceof PsiPrimitiveType) componentType = ((PsiPrimitiveType)componentType).getBoxedType(element);
        substitutor.put(typeParameters[0], componentType);
      }
      return factory.createType(atomicReferenceArrayClass, factory.createSubstitutor(substitutor));
    }
    else {
      final PsiClass atomicReferenceClass = psiFacade.findClass(AtomicReference.class.getName(), GlobalSearchScope.allScope(project));
      if (atomicReferenceClass == null) {//show warning
        return null;
      }
      final Map<PsiTypeParameter, PsiType> substitutor = ContainerUtil.newHashMap();
      final PsiTypeParameter[] typeParameters = atomicReferenceClass.getTypeParameters();
      if (typeParameters.length == 1) {
        PsiType type = fromType;
        if (type instanceof PsiPrimitiveType) type = ((PsiPrimitiveType)fromType).getBoxedType(element);
        substitutor.put(typeParameters[0], type);
      }
      return factory.createType(atomicReferenceClass, factory.createSubstitutor(substitutor));
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
