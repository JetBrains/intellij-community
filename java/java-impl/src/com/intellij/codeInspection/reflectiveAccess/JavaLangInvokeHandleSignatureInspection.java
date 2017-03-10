/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.reflectiveAccess;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInspection.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.psi.impl.source.resolve.reference.impl.JavaLangInvokeHandleReference.*;
import static com.intellij.psi.impl.source.resolve.reference.impl.JavaReflectionReferenceUtil.*;

/**
 * @author Pavel.Dolgov
 */
public class JavaLangInvokeHandleSignatureInspection extends BaseJavaBatchLocalInspectionTool {
  private static final String METHOD_TYPE = "methodType";
  private static final String GENERIC_METHOD_TYPE = "genericMethodType";

  private static final String FIND_CONSTRUCTOR = "findConstructor";
  private static final Set<String> KNOWN_METHOD_NAMES = Collections.unmodifiableSet(
    ContainerUtil.union(Arrays.asList(HANDLE_FACTORY_METHOD_NAMES), Collections.singletonList(FIND_CONSTRUCTOR)));

  private static final List<String> NO_ARGUMENT_CONSTRUCTOR_SIGNATURE = Collections.singletonList(PsiKeyword.VOID);

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression callExpression) {
        super.visitMethodCallExpression(callExpression);

        final PsiReferenceExpression methodExpression = callExpression.getMethodExpression();
        final String methodName = methodExpression.getReferenceName();
        if (methodName != null && KNOWN_METHOD_NAMES.contains(methodName)) {
          final PsiMethod method = callExpression.resolveMethod();
          final PsiClass psiClass = method != null ? method.getContainingClass() : null;
          if (psiClass != null && JAVA_LANG_INVOKE_METHOD_HANDLES_LOOKUP.equals(psiClass.getQualifiedName())) {
            final PsiExpression[] arguments = callExpression.getArgumentList().getExpressions();
            checkHandlerFactory(methodName, methodExpression, arguments, holder);
          }
        }
      }
    };
  }

  private static void checkHandlerFactory(@NotNull String factoryMethodName,
                                          @NotNull PsiReferenceExpression factoryMethodExpression,
                                          @NotNull PsiExpression[] arguments,
                                          @NotNull ProblemsHolder holder) {
    if (arguments.length == 2) {
      if (FIND_CONSTRUCTOR.equals(factoryMethodName)) {
        final PsiClass ownerClass = getReflectiveClass(arguments[0]);
        if (ownerClass != null) {
          final PsiExpression typeExpression = ParenthesesUtils.stripParentheses(arguments[1]);
          checkConstructor(ownerClass, typeExpression, holder);
        }
      }
    }
    else if (arguments.length >= 3) {
      final PsiClass ownerClass = getReflectiveClass(arguments[0]);
      if (ownerClass != null) {
        final PsiExpression nameExpression = ParenthesesUtils.stripParentheses(arguments[1]);
        final PsiExpression nameDefinition = findDefinition(nameExpression);
        final Object value = JavaConstantExpressionEvaluator.computeConstantExpression(nameDefinition, false);
        final String name = ObjectUtils.tryCast(value, String.class);
        if (!StringUtil.isEmpty(name)) {
          final PsiExpression typeExpression = ParenthesesUtils.stripParentheses(arguments[2]);

          switch (factoryMethodName) {
            case FIND_GETTER:
            case FIND_SETTER:
            case FIND_VAR_HANDLE:
              checkField(ownerClass, name, nameExpression, typeExpression, false, factoryMethodExpression, holder);
              break;
            case FIND_STATIC_GETTER:
            case FIND_STATIC_SETTER:
            case FIND_STATIC_VAR_HANDLE:
              checkField(ownerClass, name, nameExpression, typeExpression, true, factoryMethodExpression, holder);
              break;

            case FIND_VIRTUAL:
              checkMethod(ownerClass, name, nameExpression, typeExpression, false, factoryMethodExpression, holder);
              break;

            case FIND_STATIC:
              checkMethod(ownerClass, name, nameExpression, typeExpression, true, factoryMethodExpression, holder);
              break;

            case FIND_SPECIAL:
              checkMethod(ownerClass, name, nameExpression, typeExpression, false, factoryMethodExpression, holder);
              break;
          }
        }
      }
    }
  }

  private static void checkConstructor(@NotNull PsiClass ownerClass,
                                       @NotNull PsiExpression typeExpression,
                                       @NotNull ProblemsHolder holder) {
    final List<String> methodSignature = extractMethodSignature(typeExpression);
    if (methodSignature != null) {
      final List<PsiMethod> constructors = ContainerUtil.filter(ownerClass.getMethods(), PsiMethod::isConstructor);
      LocalQuickFix[] fixes = null;
      if (constructors.isEmpty()) {
        if (!methodSignature.equals(NO_ARGUMENT_CONSTRUCTOR_SIGNATURE)) {
          final LocalQuickFix fix = ReplaceSignatureQuickFix.createConstructorSignatureFix(ownerClass, NO_ARGUMENT_CONSTRUCTOR_SIGNATURE);
          fixes = fix != null ? new LocalQuickFix[]{fix} : LocalQuickFix.EMPTY_ARRAY;
        }
      }
      else if (!matchMethodSignature(constructors, methodSignature)) {
        fixes = constructors.stream()
          .map(constructor -> ReplaceSignatureQuickFix.createConstructorSignatureFix(ownerClass, constructor))
          .filter(Objects::nonNull)
          .toArray(LocalQuickFix[]::new);
      }
      if (fixes != null) {
        final String declarationText = getConstructorDeclarationText(ownerClass, methodSignature);
        if (declarationText != null) {
          holder.registerProblem(typeExpression, JavaErrorMessages.message("cannot.resolve.constructor", declarationText), fixes);
        }
      }
    }
  }

  private static void checkField(@NotNull PsiClass ownerClass,
                                 @NotNull String name,
                                 @NotNull PsiExpression nameExpression,
                                 @NotNull PsiExpression typeExpression,
                                 boolean isStatic,
                                 @NotNull PsiReferenceExpression factoryMethodExpression,
                                 @NotNull ProblemsHolder holder) {
    final PsiField field = ownerClass.findFieldByName(name, true);
    if (field == null) {
      holder.registerProblem(nameExpression, InspectionsBundle.message("inspection.handle.signature.field.cannot.resolve", name));
      return;
    }

    if (field.hasModifierProperty(PsiModifier.STATIC) != isStatic) {
      final String factoryMethodName = factoryMethodExpression.getReferenceName();
      final PsiElement factoryMethodNameElement = factoryMethodExpression.getReferenceNameElement();
      if (factoryMethodName != null && factoryMethodNameElement != null) {
        final LocalQuickFix fix = SwitchStaticnessQuickFix.createFix(factoryMethodName, isStatic);
        final String message = InspectionsBundle.message(
          isStatic ? "inspection.handle.signature.field.static" : "inspection.handle.signature.field.not.static", name);
        holder.registerProblem(factoryMethodNameElement, message, fix != null ? new LocalQuickFix[]{fix} : LocalQuickFix.EMPTY_ARRAY);
        return;
      }
    }

    final ReflectiveType reflectiveType = getReflectiveType(typeExpression);
    if (reflectiveType != null && !reflectiveType.isEqualTo(field.getType())) {
      final String expectedTypeText = getTypeText(field.getType());
      if (expectedTypeText != null) {
        final String message = InspectionsBundle.message("inspection.handle.signature.field.type", name, expectedTypeText);
        holder.registerProblem(typeExpression, message, new FieldTypeQuickFix(expectedTypeText));
      }
    }
  }

  private static void checkMethod(@NotNull PsiClass ownerClass,
                                  @NotNull String name,
                                  @NotNull PsiExpression nameExpression,
                                  @NotNull PsiExpression typeExpression,
                                  boolean isStatic,
                                  @NotNull PsiReferenceExpression factoryMethodExpression,
                                  @NotNull ProblemsHolder holder) {

    final PsiMethod[] methods = ownerClass.findMethodsByName(name, true);
    if (methods.length == 0) {
      holder.registerProblem(nameExpression, JavaErrorMessages.message("cannot.resolve.method", name));
      return;
    }

    final List<PsiMethod> filteredMethods =
      ContainerUtil.filter(methods, method -> method.hasModifierProperty(PsiModifier.STATIC) == isStatic);
    if (filteredMethods.isEmpty()) {
      final String factoryMethodName = factoryMethodExpression.getReferenceName();
      final PsiElement factoryMethodNameElement = factoryMethodExpression.getReferenceNameElement();
      if (factoryMethodName != null && factoryMethodNameElement != null) {
        final LocalQuickFix fix = SwitchStaticnessQuickFix.createFix(factoryMethodName, isStatic);
        final String message = InspectionsBundle.message(
          isStatic ? "inspection.handle.signature.method.static" : "inspection.handle.signature.method.not.static", name);
        holder.registerProblem(factoryMethodNameElement, message, fix != null ? new LocalQuickFix[]{fix} : LocalQuickFix.EMPTY_ARRAY);
        return;
      }
    }

    final List<String> methodSignature = extractMethodSignature(typeExpression);
    if (methodSignature != null && !matchMethodSignature(filteredMethods, methodSignature)) {
      final String declarationText = getMethodDeclarationText(name, methodSignature);
      if (declarationText != null) {
        final LocalQuickFix[] fixes = filteredMethods.stream()
          .map(ReplaceSignatureQuickFix::createMethodSignatureFix)
          .filter(Objects::nonNull)
          .toArray(LocalQuickFix[]::new);

        holder.registerProblem(typeExpression, JavaErrorMessages.message("cannot.resolve.method", declarationText), fixes);
      }
    }
  }

  @Nullable
  private static String getMethodDeclarationText(@NotNull String name, @NotNull List<String> methodSignature) {
    if (methodSignature.isEmpty()) {
      return null;
    }
    final String argumentTypes = methodSignature.stream().skip(1).collect(Collectors.joining(", "));
    return methodSignature.get(0) + " " + name + "(" + argumentTypes + ")";
  }

  @Nullable
  private static String getConstructorDeclarationText(@NotNull PsiClass ownerClass, List<String> methodSignature) {
    final String name = ownerClass.getName();
    if (name == null || methodSignature.isEmpty()) {
      return null;
    }
    // Return type of the constructor should be 'void'. If it isn't so let's make that mistake more noticeable.
    final String returnType = methodSignature.get(0);
    final String fakeReturnType = !PsiKeyword.VOID.equals(returnType) ? returnType + " " : "";
    final String argumentTypes = methodSignature.stream().skip(1).collect(Collectors.joining(", "));
    return fakeReturnType + name + "(" + argumentTypes + ")";
  }

  private static boolean matchMethodSignature(@NotNull List<PsiMethod> methods, @NotNull List<String> expectedMethodSignature) {
    return methods.stream()
      .map(JavaLangInvokeHandleSignatureInspection::extractMethodSignature)
      .anyMatch(expectedMethodSignature::equals);
  }

  /**
   * Extract the types from arguments of MethodType.methodType(Class...) and MethodType.genericMethodType(int, boolean?)
   */
  private static List<String> extractMethodSignature(@Nullable PsiExpression typeExpression) {
    final PsiExpression typeDefinition = findDefinition(typeExpression);
    if (typeDefinition instanceof PsiMethodCallExpression) {
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)typeDefinition;
      final String referenceName = methodCallExpression.getMethodExpression().getReferenceName();
      final boolean isGeneric;
      if (METHOD_TYPE.equals(referenceName)) {
        isGeneric = false;
      }
      else if (GENERIC_METHOD_TYPE.equals(referenceName)) {
        isGeneric = true;
      }
      else {
        return null;
      }
      final PsiMethod method = methodCallExpression.resolveMethod();
      if (method != null) {
        final PsiClass psiClass = method.getContainingClass();
        if (psiClass != null && JAVA_LANG_INVOKE_METHOD_TYPE.equals(psiClass.getQualifiedName())) {
          final PsiExpression[] arguments = methodCallExpression.getArgumentList().getExpressions();
          return isGeneric ? extractGenericMethodSignature(arguments) : extractMethodSignature(arguments);
        }
      }
    }
    return null;
  }

  @Nullable
  private static List<String> extractMethodSignature(PsiExpression[] arguments) {
    final List<String> typeNames = Arrays.stream(arguments)
      .map(JavaLangInvokeHandleSignatureInspection::getTypeText)
      .collect(Collectors.toList());
    return !typeNames.isEmpty() && !typeNames.contains(null) ? typeNames : null;
  }

  private static List<String> extractGenericMethodSignature(PsiExpression[] arguments) {
    if (arguments.length == 0 || arguments.length > 2) {
      return null;
    }

    final PsiExpression countArgument = ParenthesesUtils.stripParentheses(arguments[0]);
    final Object countArgumentValue = JavaConstantExpressionEvaluator.computeConstantExpression(countArgument, false);
    if (!(countArgumentValue instanceof Integer)) {
      return null;
    }
    final int objectArgCount = (int)countArgumentValue;
    if (objectArgCount < 0 || objectArgCount > 255) {
      return null;
    }

    boolean finalArray = false;
    if (arguments.length == 2) {
      final PsiExpression hasArrayArgument = ParenthesesUtils.stripParentheses(arguments[1]);
      final Object hasArrayArgumentValue = JavaConstantExpressionEvaluator.computeConstantExpression(hasArrayArgument, false);
      if (!(hasArrayArgumentValue instanceof Boolean)) {
        return null;
      }
      finalArray = (boolean)hasArrayArgumentValue;
      if (finalArray && objectArgCount > 254) {
        return null;
      }
    }

    final List<String> typeNames = new ArrayList<>();
    typeNames.add(CommonClassNames.JAVA_LANG_OBJECT); // return type
    for (int i = 0; i < objectArgCount; i++) {
      typeNames.add(CommonClassNames.JAVA_LANG_OBJECT);
    }
    if (finalArray) {
      typeNames.add(CommonClassNames.JAVA_LANG_OBJECT + "[]");
    }
    return typeNames;
  }

  @Contract("null -> null")
  @Nullable
  private static List<String> extractMethodSignature(@Nullable PsiMethod method) {
    if (method != null) {
      final List<String> types = new ArrayList<>();
      final PsiType returnType = !method.isConstructor() ? method.getReturnType() : PsiType.VOID;
      types.add(getTypeText(returnType));
      for (PsiParameter parameter : method.getParameterList().getParameters()) {
        types.add(getTypeText(parameter.getType()));
      }
      if (!types.contains(null)) {
        return types;
      }
    }
    return null;
  }

  @Nullable
  private static String getTypeText(@Nullable PsiExpression argument) {
    final ReflectiveType reflectiveType = getReflectiveType(argument);
    return reflectiveType != null ? reflectiveType.getQualifiedName() : null;
  }

  @Nullable
  private static String getTypeText(@Nullable PsiType type) {
    PsiType erased = TypeConversionUtil.erasure(type);
    if (erased instanceof PsiEllipsisType) {
      erased = ((PsiEllipsisType)erased).toArrayType();
    }
    return erased != null ? erased.getCanonicalText() : null;
  }

  private static class FieldTypeQuickFix implements LocalQuickFix {
    private final String myFieldTypeText;

    public FieldTypeQuickFix(String fieldTypeText) {myFieldTypeText = fieldTypeText;}

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.handle.signature.change.type.fix.name", myFieldTypeText);
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
      final PsiExpression typeExpression = factory.createExpressionFromText(myFieldTypeText + ".class", element);
      final JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(project);
      styleManager.shortenClassReferences(element.replace(typeExpression));
    }
  }

  private static class SwitchStaticnessQuickFix implements LocalQuickFix {
    private static final Map<String, String> STATIC_TO_NON_STATIC = ContainerUtil.<String, String>immutableMapBuilder()
      .put(FIND_STATIC_GETTER, FIND_GETTER)
      .put(FIND_STATIC_SETTER, FIND_SETTER)
      .put(FIND_STATIC_VAR_HANDLE, FIND_VAR_HANDLE)
      .put(FIND_STATIC, FIND_VIRTUAL)
      .build();
    private static final Map<String, String> NON_STATIC_TO_STATIC = ContainerUtil.<String, String>immutableMapBuilder()
      .put(FIND_GETTER, FIND_STATIC_GETTER)
      .put(FIND_SETTER, FIND_STATIC_SETTER)
      .put(FIND_VAR_HANDLE, FIND_STATIC_VAR_HANDLE)
      .put(FIND_VIRTUAL, FIND_STATIC)
      .build();

    private final String myReplacementName;

    public SwitchStaticnessQuickFix(@NotNull String replacementName) {
      myReplacementName = replacementName;
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.handle.signature.replace.with.fix.name", myReplacementName);
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
      final PsiIdentifier identifier = factory.createIdentifier(myReplacementName);
      final JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(project);
      styleManager.shortenClassReferences(element.replace(identifier));
    }

    @Nullable
    public static LocalQuickFix createFix(@NotNull String methodName, boolean isStatic) {
      final String replacementName = isStatic ? STATIC_TO_NON_STATIC.get(methodName) : NON_STATIC_TO_STATIC.get(methodName);
      return replacementName != null ? new SwitchStaticnessQuickFix(replacementName) : null;
    }
  }

  private static class ReplaceSignatureQuickFix implements LocalQuickFix {
    private final String myFixName;
    private final List<String> myMethodSignature;

    public ReplaceSignatureQuickFix(@NotNull String fixName, @NotNull List<String> methodSignature) {
      myFixName = fixName;
      myMethodSignature = methodSignature;
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return myFixName;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();

      final String types = myMethodSignature.stream()
        .map(text -> text + ".class")
        .collect(Collectors.joining(", "));
      final String text = JAVA_LANG_INVOKE_METHOD_TYPE + "." + METHOD_TYPE + "(" + types + ")";

      final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
      final PsiExpression replacement = factory.createExpressionFromText(text, element);
      final JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(project);
      styleManager.shortenClassReferences(element.replace(replacement));
    }

    @Nullable
    public static LocalQuickFix createMethodSignatureFix(@Nullable PsiMethod method) {
      final List<String> methodSignature = extractMethodSignature(method);
      if (methodSignature != null) {
        final String declarationText = getMethodDeclarationText(method.getName(), methodSignature);
        if (declarationText != null) {
          final String message = InspectionsBundle.message("inspection.handle.signature.use.method.fix.name", declarationText);
          return new ReplaceSignatureQuickFix(message, methodSignature);
        }
      }
      return null;
    }

    @Nullable
    public static LocalQuickFix createConstructorSignatureFix(@NotNull PsiClass ownerClass, @Nullable PsiMethod constructor) {
      final List<String> methodSignature = extractMethodSignature(constructor);
      return methodSignature != null ? createConstructorSignatureFix(ownerClass, methodSignature) : null;
    }

    @Nullable
    public static LocalQuickFix createConstructorSignatureFix(@NotNull PsiClass ownerClass, @NotNull List<String> methodSignature) {
      final String declarationText = getConstructorDeclarationText(ownerClass, methodSignature);
      if (declarationText != null) {
        final String message = InspectionsBundle.message("inspection.handle.signature.use.constructor.fix.name", declarationText);
        return new ReplaceSignatureQuickFix(message, methodSignature);
      }
      return null;
    }
  }
}
