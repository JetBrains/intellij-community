// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.typeMigration.intentions;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.impl.analysis.JavaErrorFixProvider;
import com.intellij.codeInsight.intention.BaseElementAtCaretIntentionAction;
import com.intellij.codeInsight.intention.CommonIntentionAction;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.codeserver.highlighting.errors.JavaCompilationError;
import com.intellij.java.codeserver.highlighting.errors.JavaErrorKinds;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.impl.AllowedApiFilterExtension;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.typeMigration.TypeMigrationBundle;
import com.intellij.refactoring.typeMigration.TypeMigrationVariableTypeFixProvider;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

/**
 * @author anna
 */
public class ConvertFieldToAtomicIntention extends BaseElementAtCaretIntentionAction implements PriorityAction {
  private static final Logger LOG = Logger.getInstance(ConvertFieldToAtomicIntention.class);
  private final Map<PsiType, String> myFromToMap = Map.of(
    PsiTypes.intType(), AtomicInteger.class.getName(),
    PsiTypes.longType(), AtomicLong.class.getName(),
    PsiTypes.booleanType(), AtomicBoolean.class.getName(),
    PsiTypes.intType().createArrayType(), AtomicIntegerArray.class.getName(),
    PsiTypes.longType().createArrayType(), AtomicLongArray.class.getName());

  @Override
  public @NotNull String getText() {
    return TypeMigrationBundle.message("convert.to.atomic.family.name");
  }

  @Override
  public @NotNull String getFamilyName() {
    return getText();
  }

  @Override
  public @NotNull Priority getPriority() {
    return Priority.LOW;
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    PsiVariable variable = getVariable(getElement(editor, psiFile));
    if (variable == null) return IntentionPreviewInfo.EMPTY;
    PsiType type = variable.getType();
    String toType = myFromToMap.get(type);
    String variableName = variable.getName();
    String modifiedText;
    if (toType == null) {
      Class<?> atomicClass;
      if (type instanceof PsiArrayType arrayType) {
        type = arrayType.getComponentType();
        atomicClass = AtomicReferenceArray.class;
      }
      else {
        atomicClass = AtomicReference.class;
      }
      String presentableText = StringUtil.getShortName(atomicClass.getName());
      modifiedText = presentableText + '<' + type.getPresentableText() + "> " + variableName + " = new " + presentableText + "<>(...)";
    }
    else {
      String presentableText = StringUtil.getShortName(toType);
      modifiedText = presentableText + " " + variableName + " = new " + presentableText + "(...)";
    }
    return new IntentionPreviewInfo.CustomDiff(JavaFileType.INSTANCE, type.getPresentableText() + " " + variableName, modifiedText);
  }

  @Override
  public boolean isAvailable(@NotNull Project project, @NotNull Editor editor, @NotNull PsiElement element) {
    PsiVariable psiVariable = getVariable(element);
    if (psiVariable == null || psiVariable instanceof PsiResourceVariable) return false;
    if (psiVariable.getLanguage() != JavaLanguage.INSTANCE) return false;
    if (psiVariable.getTypeElement() == null) return false;
    if (!PsiUtil.isLanguageLevel5OrHigher(psiVariable)) return false;
    PsiType psiType = psiVariable.getType();
    PsiClass psiTypeClass = PsiUtil.resolveClassInType(psiType);
    if (psiTypeClass != null) {
      String qualifiedName = psiTypeClass.getQualifiedName();
      if (qualifiedName != null) { //is already atomic
        if (myFromToMap.containsValue(qualifiedName) ||
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

  PsiVariable getVariable(PsiElement element) {
    if (element instanceof PsiIdentifier) {
      PsiElement parent = element.getParent();
      if (parent instanceof PsiLocalVariable || parent instanceof PsiField) {
        return (PsiVariable)parent;
      }
    }
    return null;
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    PsiVariable var = getVariable(element);
    LOG.assertTrue(var != null);

    PsiType fromType = var.getType();
    PsiClassType toType = getMigrationTargetType(element, fromType);
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
    PsiType type = var.getType();
    String initializerText = PsiTypesUtil.getDefaultValueOfType(type);
    if (!JavaKeywords.NULL.equals(initializerText)) {
      WriteAction.run(() -> {
        PsiExpression initializer = JavaPsiFacade.getElementFactory(var.getProject()).createExpressionFromText(initializerText, var);
        var.setInitializer(initializer);
      });
    }
  }

  static void postProcessVariable(@NotNull PsiVariable var, @NotNull String toType) {
    Project project = var.getProject();
    if (var instanceof PsiField || JavaCodeStyleSettings.getInstance(var.getContainingFile()).GENERATE_FINAL_LOCALS) {
      PsiModifierList modifierList = Objects.requireNonNull(var.getModifierList());
      WriteAction.run(() -> {
        if (var.getInitializer() == null) {
          PsiExpression newInitializer = JavaPsiFacade.getElementFactory(project).createExpressionFromText("new " + toType + "()", var);
          var.setInitializer(newInitializer);
        }

        modifierList.setModifierProperty(PsiModifier.FINAL, true);
        modifierList.setModifierProperty(PsiModifier.VOLATILE, false);

        JavaCodeStyleManager.getInstance(project).shortenClassReferences(var);
        CodeStyleManager.getInstance(project).reformat(var);
      });
    }
  }

  private @Nullable PsiClassType getMigrationTargetType(@NotNull PsiElement element, @NotNull PsiType fromType) {
    final Project project = element.getProject();
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    PsiElementFactory factory = psiFacade.getElementFactory();
    GlobalSearchScope scope = element.getResolveScope();
    String atomicQualifiedName = myFromToMap.get(fromType);
    if (atomicQualifiedName != null) {
      PsiClass atomicClass = psiFacade.findClass(atomicQualifiedName, scope);
      if (atomicClass == null) {//show warning
        return null;
      }
      return factory.createType(atomicClass);
    }
    else if (fromType instanceof PsiArrayType) {
      PsiClass atomicReferenceArrayClass =
        psiFacade.findClass(AtomicReferenceArray.class.getName(), scope);
      if (atomicReferenceArrayClass == null) {//show warning
        return null;
      }
      Map<PsiTypeParameter, PsiType> substitutor = new HashMap<>();
      PsiTypeParameter[] typeParameters = atomicReferenceArrayClass.getTypeParameters();
      if (typeParameters.length == 1) {
        PsiType componentType = ((PsiArrayType)fromType).getComponentType();
        if (componentType instanceof PsiPrimitiveType) componentType = ((PsiPrimitiveType)componentType).getBoxedType(element);
        substitutor.put(typeParameters[0], componentType);
      }
      return factory.createType(atomicReferenceArrayClass, factory.createSubstitutor(substitutor));
    }
    else {
      PsiClass atomicReferenceClass = psiFacade.findClass(AtomicReference.class.getName(), scope);
      if (atomicReferenceClass == null) {//show warning
        return null;
      }
      Map<PsiTypeParameter, PsiType> substitutor = new HashMap<>();
      PsiTypeParameter[] typeParameters = atomicReferenceClass.getTypeParameters();
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

  public static final class ConvertNonFinalLocalToAtomicFix extends ConvertFieldToAtomicIntention implements HighPriorityAction {
    private final PsiElement myContext;

    public ConvertNonFinalLocalToAtomicFix(PsiElement context) {
      myContext = context;
    }

    @Override
    public @NotNull Priority getPriority() {
      return Priority.HIGH;
    }

    @Override
    public boolean isAvailable(@NotNull Project project, @NotNull Editor editor, @NotNull PsiElement element) {
      return getVariable(element) != null;
    }

    @Override
    PsiVariable getVariable(PsiElement element) {
      if (myContext instanceof PsiReferenceExpression ref && myContext.isValid() && PsiUtil.isAccessedForWriting(ref)) {
        return ObjectUtils.tryCast(ref.resolve(), PsiLocalVariable.class);
      }
      return null;
    }
  }

  public static final class ConvertToAtomicFixProvider implements JavaErrorFixProvider {
    @Override
    public void registerFixes(@NotNull JavaCompilationError<?, ?> error, @NotNull Consumer<? super @NotNull CommonIntentionAction> sink) {
      error.psiForKind(JavaErrorKinds.VARIABLE_MUST_BE_EFFECTIVELY_FINAL_LAMBDA).map(ConvertNonFinalLocalToAtomicFix::new).ifPresent(sink);
    }
  }
}
