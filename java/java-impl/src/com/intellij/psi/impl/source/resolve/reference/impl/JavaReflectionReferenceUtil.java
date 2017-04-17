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
package com.intellij.psi.impl.source.resolve.reference.impl;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.JavaLookupElementBuilder;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PlatformIcons;
import com.siyeh.ig.psiutils.DeclarationSearchUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.function.Function;

/**
 * @author Pavel.Dolgov
 */
public class JavaReflectionReferenceUtil {
  // MethodHandle (Java 7) and VarHandle (Java 9) infrastructure
  public static final String JAVA_LANG_INVOKE_METHOD_HANDLES_LOOKUP = "java.lang.invoke.MethodHandles.Lookup";
  public static final String JAVA_LANG_INVOKE_METHOD_TYPE = "java.lang.invoke.MethodType";

  public static final String METHOD_TYPE = "methodType";
  public static final String GENERIC_METHOD_TYPE = "genericMethodType";

  public static final String FIND_VIRTUAL = "findVirtual";
  public static final String FIND_STATIC = "findStatic";
  public static final String FIND_SPECIAL = "findSpecial";

  public static final String FIND_GETTER = "findGetter";
  public static final String FIND_SETTER = "findSetter";
  public static final String FIND_STATIC_GETTER = "findStaticGetter";
  public static final String FIND_STATIC_SETTER = "findStaticSetter";

  public static final String FIND_VAR_HANDLE = "findVarHandle";
  public static final String FIND_STATIC_VAR_HANDLE = "findStaticVarHandle";

  public static final String FIND_CONSTRUCTOR = "findConstructor";

  public static final String[] HANDLE_FACTORY_METHOD_NAMES = {
    FIND_VIRTUAL, FIND_STATIC, FIND_SPECIAL,
    FIND_GETTER, FIND_SETTER,
    FIND_STATIC_GETTER, FIND_STATIC_SETTER,
    FIND_VAR_HANDLE, FIND_STATIC_VAR_HANDLE};

  // Classic reflection infrastructure
  public static final String GET_FIELD = "getField";
  public static final String GET_DECLARED_FIELD = "getDeclaredField";
  public static final String GET_METHOD = "getMethod";
  public static final String GET_DECLARED_METHOD = "getDeclaredMethod";
  public static final String GET_CONSTRUCTOR = "getConstructor";
  public static final String GET_DECLARED_CONSTRUCTOR = "getDeclaredConstructor";

  public static final String JAVA_LANG_CLASS_LOADER = "java.lang.ClassLoader";
  public static final String FOR_NAME = "forName";
  public static final String LOAD_CLASS = "loadClass";
  public static final String GET_CLASS = "getClass";

  private static final RecursionGuard ourGuard = RecursionManager.createGuard("JavaLangClassMemberReference");

  @Nullable
  public static ReflectiveType getReflectiveType(@Nullable PsiExpression context) {
    context = ParenthesesUtils.stripParentheses(context);
    if (context == null) {
      return null;
    }
    if (context instanceof PsiClassObjectAccessExpression) { // special case for JDK 1.4
      final PsiTypeElement operand = ((PsiClassObjectAccessExpression)context).getOperand();
      return ReflectiveType.create(operand.getType(), context);
    }

    if (context instanceof PsiMethodCallExpression) {
      final PsiMethodCallExpression methodCall = (PsiMethodCallExpression)context;
      final String methodReferenceName = methodCall.getMethodExpression().getReferenceName();
      if (FOR_NAME.equals(methodReferenceName)) {
        final PsiMethod method = methodCall.resolveMethod();
        if (method != null && isJavaLangClass(method.getContainingClass())) {
          final PsiExpression[] expressions = methodCall.getArgumentList().getExpressions();
          if (expressions.length == 1) {
            final PsiExpression argument = findDefinition(ParenthesesUtils.stripParentheses(expressions[0]));
            final String className = computeConstantExpression(argument, String.class);
            if (className != null) {
              return ReflectiveType.create(findClass(className, context));
            }
          }
        }
      }
      else if (GET_CLASS.equals(methodReferenceName) && methodCall.getArgumentList().getExpressions().length == 0) {
        final PsiMethod method = methodCall.resolveMethod();
        if (method != null && isJavaLangObject(method.getContainingClass())) {
          final PsiExpression qualifier = ParenthesesUtils.stripParentheses(methodCall.getMethodExpression().getQualifierExpression());
          if (qualifier instanceof PsiReferenceExpression) {
            final PsiExpression definition = findVariableDefinition((PsiReferenceExpression)qualifier);
            if (definition != null) {
              return ReflectiveType.create(definition.getType(), context);
            }
          }
          //TODO type of the qualifier may be a supertype of the actual value - need to compute the type of the actual value
          // otherwise getDeclaredField and getDeclaredMethod may work not reliably
          if (qualifier != null) {
            return ReflectiveType.create(qualifier.getType(), context);
          }
        }
      }
    }
    final PsiType type = context.getType();
    if (type instanceof PsiClassType) {
      final PsiClassType.ClassResolveResult resolveResult = ((PsiClassType)type).resolveGenerics();
      if (!isJavaLangClass(resolveResult.getElement())) return null;
      final PsiTypeParameter[] parameters = resolveResult.getElement().getTypeParameters();
      if (parameters.length == 1) {
        PsiType typeArgument = resolveResult.getSubstitutor().substitute(parameters[0]);
        if (typeArgument instanceof PsiCapturedWildcardType) {
          typeArgument = ((PsiCapturedWildcardType)typeArgument).getUpperBound();
        }
        final PsiClass argumentClass = PsiTypesUtil.getPsiClass(typeArgument);
        if (argumentClass != null && !isJavaLangObject(argumentClass)) {
          return ReflectiveType.create(argumentClass);
        }
      }
    }
    if (context instanceof PsiReferenceExpression) {
      final PsiElement resolved = ((PsiReferenceExpression)context).resolve();
      if (resolved instanceof PsiVariable) {
        final PsiExpression definition = findVariableDefinition((PsiReferenceExpression)context, (PsiVariable)resolved);
        if (definition != null) {
          return ourGuard.doPreventingRecursion(resolved, false, () -> getReflectiveType(definition));
        }
      }
    }
    return null;
  }

  @Nullable
  public static <T> T computeConstantExpression(@Nullable PsiExpression expression, @NotNull Class<T> expectedType) {
    expression = ParenthesesUtils.stripParentheses(expression);
    final Object computed = JavaConstantExpressionEvaluator.computeConstantExpression(expression, false);
    return ObjectUtils.tryCast(computed, expectedType);
  }

  @Nullable
  public static PsiClass getReflectiveClass(PsiExpression context) {
    final ReflectiveType reflectiveType = getReflectiveType(context);
    return reflectiveType != null ? reflectiveType.myPsiClass : null;
  }

  @Nullable
  public static PsiExpression findDefinition(@Nullable PsiExpression expression) {
    int preventEndlessLoop = 5;
    while (expression instanceof PsiReferenceExpression) {
      if (--preventEndlessLoop == 0) return null;
      expression = findVariableDefinition((PsiReferenceExpression)expression);
    }
    return expression;
  }

  @Nullable
  private static PsiExpression findVariableDefinition(@NotNull PsiReferenceExpression referenceExpression) {
    final PsiElement resolved = referenceExpression.resolve();
    return resolved instanceof PsiVariable ? findVariableDefinition(referenceExpression, (PsiVariable)resolved) : null;
  }

  @Nullable
  private static PsiExpression findVariableDefinition(@NotNull PsiReferenceExpression referenceExpression, @NotNull PsiVariable variable) {
    if (variable.hasModifierProperty(PsiModifier.FINAL)) {
      final PsiExpression initializer = variable.getInitializer();
      if (initializer != null) {
        return initializer;
      }
    }
    return DeclarationSearchUtils.findDefinition(referenceExpression, variable);
  }

  private static PsiClass findClass(@NotNull String qualifiedName, @NotNull PsiElement context) {
    final Project project = context.getProject();
    return JavaPsiFacade.getInstance(project).findClass(qualifiedName, GlobalSearchScope.allScope(project));
  }

  static boolean isJavaLangClass(@Nullable PsiClass aClass) {
    return aClass != null && CommonClassNames.JAVA_LANG_CLASS.equals(aClass.getQualifiedName());
  }

  static boolean isJavaLangObject(@Nullable PsiClass aClass) {
    return aClass != null && CommonClassNames.JAVA_LANG_OBJECT.equals(aClass.getQualifiedName());
  }

  @Contract("null -> false")
  static boolean isRegularMethod(@Nullable PsiMethod method) {
    return method != null && !method.isConstructor();
  }

  static boolean isPublic(@NotNull PsiMember member) {
    return member.hasModifierProperty(PsiModifier.PUBLIC);
  }

  @Nullable
  static String getParameterTypesText(@NotNull PsiMethod method) {
    final StringJoiner joiner = new StringJoiner(", ");
    for (PsiParameter parameter : method.getParameterList().getParameters()) {
      final String typeText = getTypeText(parameter.getType(), method);
      if (typeText == null) return null;
      joiner.add(typeText + ".class");
    }
    return joiner.toString();
  }

  static void shortenArgumentsClassReferences(@NotNull InsertionContext context) {
    final PsiElement parameter = PsiUtilCore.getElementAtOffset(context.getFile(), context.getStartOffset());
    final PsiExpressionList parameterList = PsiTreeUtil.getParentOfType(parameter, PsiExpressionList.class);
    if (parameterList != null && parameterList.getParent() instanceof PsiMethodCallExpression) {
      JavaCodeStyleManager.getInstance(context.getProject()).shortenClassReferences(parameterList);
    }
  }

  @NotNull
  static LookupElement withPriority(@NotNull LookupElement lookupElement, boolean hasPriority) {
    return hasPriority ? lookupElement : PrioritizedLookupElement.withPriority(lookupElement, -1);
  }

  @NotNull
  static LookupElement withPriority(@NotNull LookupElement lookupElement, int priority) {
    return priority == 0 ? lookupElement : PrioritizedLookupElement.withPriority(lookupElement, priority);
  }

  static int getMethodSortOrder(@NotNull PsiMethod method) {
    return isJavaLangObject(method.getContainingClass()) ? 1 : isPublic(method) ? -1 : 0;
  }

  @Nullable
  static String getMemberType(@Nullable PsiElement element) {
    final PsiMethodCallExpression methodCall = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
    return methodCall != null ? methodCall.getMethodExpression().getReferenceName() : null;
  }

  @NotNull
  static LookupElement lookupField(@NotNull PsiField field) {
    return JavaLookupElementBuilder.forField(field);
  }

  static void replaceText(@NotNull InsertionContext context, @NotNull String text) {
    final PsiElement newElement = PsiUtilCore.getElementAtOffset(context.getFile(), context.getStartOffset());
    final PsiElement params = newElement.getParent().getParent();
    final int end = params.getTextRange().getEndOffset() - 1;
    final int start = Math.min(newElement.getTextRange().getEndOffset(), end);

    context.getDocument().replaceString(start, end, text);
    context.commitDocument();
    shortenArgumentsClassReferences(context);
  }

  @Nullable
  public static String getTypeText(@Nullable PsiType type, @NotNull PsiElement context) {
    final ReflectiveType reflectiveType = ReflectiveType.create(type, context);
    return reflectiveType != null ? reflectiveType.getQualifiedName() : null;
  }

  @Nullable
  public static String getTypeText(@Nullable PsiExpression argument) {
    final ReflectiveType reflectiveType = getReflectiveType(argument);
    return reflectiveType != null ? reflectiveType.getQualifiedName() : null;
  }

  @Contract("null -> null")
  @Nullable
  public static ReflectiveSignature getMethodSignature(@Nullable PsiMethod method) {
    if (method != null) {
      final List<String> types = new ArrayList<>();
      final PsiType returnType = !method.isConstructor() ? method.getReturnType() : PsiType.VOID;
      types.add(getTypeText(returnType, method));

      for (PsiParameter parameter : method.getParameterList().getParameters()) {
        types.add(getTypeText(parameter.getType(), method));
      }
      final Icon icon = method.getIcon(Iconable.ICON_FLAG_VISIBILITY);
      return ReflectiveSignature.create(icon, types);
    }
    return null;
  }

  @NotNull
  public static String getMethodTypeExpressionText(@NotNull ReflectiveSignature signature) {
    final String types = signature.getText(true, type -> type + ".class");
    return JAVA_LANG_INVOKE_METHOD_TYPE + "." + METHOD_TYPE + types;
  }


  public static class ReflectiveType {
    final PsiClass myPsiClass;
    final PsiPrimitiveType myPrimitiveType;
    final int myArrayDimensions;

    public ReflectiveType(PsiClass psiClass, PsiPrimitiveType primitiveType, int arrayDimensions) {
      myPsiClass = psiClass;
      myPrimitiveType = primitiveType;
      myArrayDimensions = arrayDimensions;
    }

    @Nullable
    public String getQualifiedName() {
      String text = null;
      if (myPrimitiveType != null) {
        text = myPrimitiveType.getCanonicalText();
      }
      else if (myPsiClass != null) {
        text = myPsiClass.getQualifiedName();
      }
      if (myArrayDimensions == 0 || text == null) {
        return text;
      }
      final StringBuilder sb = new StringBuilder(text);
      for (int i = 0; i < myArrayDimensions; i++) {
        sb.append("[]");
      }
      return sb.toString();
    }

    @Override
    public String toString() {
      final String name = getQualifiedName();
      return name != null ? name : "null";
    }

    public boolean isEqualTo(@Nullable PsiType otherType) {
      if (otherType == null || myArrayDimensions != otherType.getArrayDimensions()) {
        return false;
      }
      final PsiType otherComponentType = otherType.getDeepComponentType();
      if (myPrimitiveType != null) {
        return myPrimitiveType.equals(otherComponentType);
      }
      if (myPsiClass != null) {
        final PsiClass otherClass = PsiUtil.resolveClassInType(otherComponentType);
        if (otherClass != null) {
          final String otherClassName = otherClass instanceof PsiTypeParameter
                                        ? CommonClassNames.JAVA_LANG_OBJECT : otherClass.getQualifiedName();
          if (otherClassName != null) {
            return otherClassName.equals(myPsiClass.getQualifiedName());
          }
        }
      }
      return false;
    }

    public boolean isAssignableFrom(@NotNull PsiType type) {
      if (type.equals(PsiType.NULL)) {
        return myPsiClass != null || myArrayDimensions != 0;
      }
      PsiType otherType = type;
      for (int i = 0; i < myArrayDimensions; i++) {
        if (!(otherType instanceof PsiArrayType)) {
          return false;
        }
        otherType = ((PsiArrayType)otherType).getComponentType();
      }
      if (myPrimitiveType != null) {
        return myPrimitiveType.isAssignableFrom(otherType);
      }
      final PsiElementFactory factory = JavaPsiFacade.getInstance(myPsiClass.getProject()).getElementFactory();
      return factory.createType(myPsiClass).isAssignableFrom(otherType);
    }

    @Nullable
    public static ReflectiveType create(@Nullable PsiType originalType, @NotNull PsiElement context) {
      if (originalType == null) {
        return null;
      }
      final int arrayDimensions = originalType.getArrayDimensions();
      final PsiType type = originalType.getDeepComponentType();
      if (type instanceof PsiPrimitiveType) {
        return new ReflectiveType(null, (PsiPrimitiveType)type, arrayDimensions);
      }
      PsiClass psiClass = PsiUtil.resolveClassInType(type);
      if (psiClass instanceof PsiTypeParameter) {
        psiClass = findClass(CommonClassNames.JAVA_LANG_OBJECT, context);
      }
      if (psiClass != null) {
        return new ReflectiveType(psiClass, null, arrayDimensions);
      }
      return null;
    }

    @Nullable
    public static ReflectiveType create(@Nullable PsiClass psiClass) {
      return psiClass != null ? new ReflectiveType(psiClass, null, 0) : null;
    }
  }

  public static class ReflectiveSignature implements Comparable<ReflectiveSignature> {
    public static final ReflectiveSignature NO_ARGUMENT_CONSTRUCTOR_SIGNATURE =
      new ReflectiveSignature(null, PsiKeyword.VOID, ArrayUtil.EMPTY_STRING_ARRAY);

    private final Icon myIcon;
    @NotNull private final String myReturnType;
    @NotNull private final String[] myArgumentTypes;

    @Nullable
    public static ReflectiveSignature create(@NotNull List<String> typeTexts) {
      return create(null, typeTexts);
    }

    @Nullable
    public static ReflectiveSignature create(@Nullable Icon icon, @NotNull List<String> typeTexts) {
      if (!typeTexts.isEmpty() && !typeTexts.contains(null)) {
        final String[] argumentTypes = ArrayUtil.toStringArray(typeTexts.subList(1, typeTexts.size()));
        return new ReflectiveSignature(icon, typeTexts.get(0), argumentTypes);
      }
      return null;
    }

    private ReflectiveSignature(@Nullable Icon icon, @NotNull String returnType, @NotNull String[] argumentTypes) {
      myIcon = icon;
      myReturnType = returnType;
      myArgumentTypes = argumentTypes;
    }

    public String getText(boolean withReturnType, @NotNull Function<String, String> transformation) {
      final StringJoiner joiner = new StringJoiner(", ", "(", ")");
      if (withReturnType) {
        joiner.add(transformation.apply(myReturnType));
      }
      for (String argumentType : myArgumentTypes) {
        joiner.add(transformation.apply(argumentType));
      }
      return joiner.toString();
    }

    @NotNull
    public String getShortReturnType() {
      return PsiNameHelper.getShortClassName(myReturnType);
    }

    @NotNull
    public String getShortArgumentTypes() {
      return getText(false, PsiNameHelper::getShortClassName);
    }

    @Nullable
    public Icon getIcon() {
      return myIcon != null ? myIcon : PlatformIcons.METHOD_ICON;
    }

    @Override
    public int compareTo(@NotNull ReflectiveSignature other) {
      int c = myArgumentTypes.length - other.myArgumentTypes.length;
      if (c != 0) return c;
      c = ArrayUtil.lexicographicCompare(myArgumentTypes, other.myArgumentTypes);
      if (c != 0) return c;
      return myReturnType.compareTo(other.myReturnType);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof ReflectiveSignature)) return false;
      final ReflectiveSignature other = (ReflectiveSignature)o;
      return Objects.equals(myReturnType, other.myReturnType) &&
             Arrays.equals(myArgumentTypes, other.myArgumentTypes);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myReturnType, myArgumentTypes);
    }

    @Override
    public String toString() {
      return myReturnType + " " + Arrays.toString(myArgumentTypes);
    }
  }
}
