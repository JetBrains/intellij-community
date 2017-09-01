/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.util;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import gnu.trove.THashMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PsiTypesUtil {
  @NonNls private static final Map<String, String> ourUnboxedTypes = new THashMap<>();
  @NonNls private static final Map<String, String> ourBoxedTypes = new THashMap<>();

  static {
    ourUnboxedTypes.put(CommonClassNames.JAVA_LANG_BOOLEAN, "boolean");
    ourUnboxedTypes.put(CommonClassNames.JAVA_LANG_BYTE, "byte");
    ourUnboxedTypes.put(CommonClassNames.JAVA_LANG_SHORT, "short");
    ourUnboxedTypes.put(CommonClassNames.JAVA_LANG_INTEGER, "int");
    ourUnboxedTypes.put(CommonClassNames.JAVA_LANG_LONG, "long");
    ourUnboxedTypes.put(CommonClassNames.JAVA_LANG_FLOAT, "float");
    ourUnboxedTypes.put(CommonClassNames.JAVA_LANG_DOUBLE, "double");
    ourUnboxedTypes.put(CommonClassNames.JAVA_LANG_CHARACTER, "char");

    ourBoxedTypes.put("boolean", CommonClassNames.JAVA_LANG_BOOLEAN);
    ourBoxedTypes.put("byte", CommonClassNames.JAVA_LANG_BYTE);
    ourBoxedTypes.put("short", CommonClassNames.JAVA_LANG_SHORT);
    ourBoxedTypes.put("int", CommonClassNames.JAVA_LANG_INTEGER);
    ourBoxedTypes.put("long", CommonClassNames.JAVA_LANG_LONG);
    ourBoxedTypes.put("float", CommonClassNames.JAVA_LANG_FLOAT);
    ourBoxedTypes.put("double", CommonClassNames.JAVA_LANG_DOUBLE);
    ourBoxedTypes.put("char", CommonClassNames.JAVA_LANG_CHARACTER);
  }

  @NonNls private static final String GET_CLASS_METHOD = "getClass";

  private PsiTypesUtil() { }

  public static Object getDefaultValue(PsiType type) {
    if (!(type instanceof PsiPrimitiveType)) return null;
    switch (type.getCanonicalText()) {
      case "boolean":
        return false;
      case "byte":
        return (byte)0;
      case "char":
        return '\0';
      case "short":
        return (short)0;
      case "int":
        return 0;
      case "long":
        return 0L;
      case "float":
        return 0F;
      case "double":
        return 0D;
      default:
        return null;
    }
  }

  @NotNull
  public static String getDefaultValueOfType(PsiType type) {
    return getDefaultValueOfType(type, false);
  }

  @NotNull
  public static String getDefaultValueOfType(PsiType type, boolean customDefaultValues) {
    if (type instanceof PsiArrayType) {
      int count = type.getArrayDimensions() - 1;
      PsiType componentType = type.getDeepComponentType();

      if (componentType instanceof PsiClassType) {
        final PsiClassType classType = (PsiClassType)componentType;
        if (classType.resolve() instanceof PsiTypeParameter) {
          return PsiKeyword.NULL;
        }
      }

      PsiType erasedComponentType = TypeConversionUtil.erasure(componentType);
      StringBuilder buffer = new StringBuilder();
      buffer.append(PsiKeyword.NEW);
      buffer.append(" ");
      buffer.append(erasedComponentType.getCanonicalText());
      buffer.append("[0]");
      for (int i = 0; i < count; i++) {
        buffer.append("[]");
      }
      return buffer.toString();
    }
    if (type instanceof PsiPrimitiveType) {
      return PsiType.BOOLEAN.equals(type) ? PsiKeyword.FALSE : "0";
    }
    if (customDefaultValues) {
      PsiType rawType = type instanceof PsiClassType ? ((PsiClassType)type).rawType() : null;
      if (rawType != null && rawType.equalsToText(CommonClassNames.JAVA_UTIL_OPTIONAL)) {
        return CommonClassNames.JAVA_UTIL_OPTIONAL + ".empty()";
      }
    }
    return PsiKeyword.NULL;
  }

  /**
   * Returns the unboxed type name or parameter.
   * @param type boxed java type name
   * @return unboxed type name if available; same value otherwise
   */
  @Contract("null -> null; !null -> !null")
  @Nullable
  public static String unboxIfPossible(final String type) {
    if (type == null) return null;
    final String s = ourUnboxedTypes.get(type);
    return s == null? type : s;
  }

  /**
   * Returns the boxed type name or parameter.
   * @param type primitive java type name
   * @return boxed type name if available; same value otherwise
   */
  @Contract("null -> null; !null -> !null")
  @Nullable
  public static String boxIfPossible(final String type) {
    if (type == null) return null;
    final String s = ourBoxedTypes.get(type);
    return s == null ? type : s;
  }

  @Nullable
  public static PsiClass getPsiClass(@Nullable PsiType psiType) {
    return psiType instanceof PsiClassType? ((PsiClassType)psiType).resolve() : null;
  }

  public static PsiClassType getClassType(@NotNull PsiClass psiClass) {
    return JavaPsiFacade.getElementFactory(psiClass.getProject()).createType(psiClass);
  }

  @Nullable
  public static PsiClassType getLowestUpperBoundClassType(@NotNull final PsiDisjunctionType type) {
    final PsiType lub = type.getLeastUpperBound();
    if (lub instanceof PsiClassType) {
      return (PsiClassType)lub;
    }
    else if (lub instanceof PsiIntersectionType) {
      for (PsiType subType : ((PsiIntersectionType)lub).getConjuncts()) {
        if (subType instanceof PsiClassType) {
          final PsiClass aClass = ((PsiClassType)subType).resolve();
          if (aClass != null && !aClass.isInterface()) {
            return (PsiClassType)subType;
          }
        }
      }
    }
    return null;
  }

  public static PsiType patchMethodGetClassReturnType(@NotNull PsiMethodReferenceExpression methodExpression,
                                                      @NotNull PsiMethod method) {
    if (isGetClass(method)) {
      final PsiType qualifierType = PsiMethodReferenceUtil.getQualifierType(methodExpression);
      return qualifierType != null ? createJavaLangClassType(methodExpression, qualifierType, true) : null;
    }
    return null;
  }
  
  public static PsiType patchMethodGetClassReturnType(@NotNull PsiExpression call,
                                                      @NotNull PsiReferenceExpression methodExpression,
                                                      @NotNull PsiMethod method,
                                                      @Nullable Condition<IElementType> condition,
                                                      @NotNull LanguageLevel languageLevel) {
    //JLS3 15.8.2
    if (languageLevel.isAtLeast(LanguageLevel.JDK_1_5) && isGetClass(method)) {
      PsiExpression qualifier = methodExpression.getQualifierExpression();
      PsiType qualifierType = null;
      final Project project = call.getProject();
      if (qualifier != null) {
        qualifierType = TypeConversionUtil.erasure(qualifier.getType());
      }
      else if (condition != null) {
        ASTNode parent = call.getNode().getTreeParent();
        while (parent != null && condition.value(parent.getElementType())) {
          parent = parent.getTreeParent();
        }
        if (parent != null) {
          qualifierType = JavaPsiFacade.getInstance(project).getElementFactory().createType((PsiClass)parent.getPsi());
        }
      }
      return createJavaLangClassType(methodExpression, qualifierType, true);
    }
    return null;
  }

  public static boolean isGetClass(PsiMethod method) {
    if (GET_CLASS_METHOD.equals(method.getName())) {
      PsiClass aClass = method.getContainingClass();
      return aClass != null && CommonClassNames.JAVA_LANG_OBJECT.equals(aClass.getQualifiedName());
    }
    return false;
  }

  @Nullable
  public static PsiType createJavaLangClassType(@NotNull PsiElement context, @Nullable PsiType qualifierType, boolean captureTopLevelWildcards) {
    if (qualifierType != null) {
      PsiUtil.ensureValidType(qualifierType);
      JavaPsiFacade facade = JavaPsiFacade.getInstance(context.getProject());
      PsiClass javaLangClass = facade.findClass(CommonClassNames.JAVA_LANG_CLASS, context.getResolveScope());
      if (javaLangClass != null && javaLangClass.getTypeParameters().length == 1) {
        PsiSubstitutor substitutor = PsiSubstitutor.EMPTY.
          put(javaLangClass.getTypeParameters()[0], PsiWildcardType.createExtends(context.getManager(), qualifierType));
        final PsiClassType classType = facade.getElementFactory().createType(javaLangClass, substitutor, PsiUtil.getLanguageLevel(context));
        return captureTopLevelWildcards ? PsiUtil.captureToplevelWildcards(classType, context) : classType;
      }
    }
    return null;
  }

  /**
   * Return type explicitly declared in parent
   */
  @Nullable
  public static PsiType getExpectedTypeByParent(@NotNull PsiElement element) {
    final PsiElement parent = PsiUtil.skipParenthesizedExprUp(element.getParent());
    if (parent instanceof PsiVariable) {
      if (PsiUtil.checkSameExpression(element, ((PsiVariable)parent).getInitializer())) {
        return ((PsiVariable)parent).getType();
      }
    }
    else if (parent instanceof PsiAssignmentExpression) {
      if (PsiUtil.checkSameExpression(element, ((PsiAssignmentExpression)parent).getRExpression())) {
        return ((PsiAssignmentExpression)parent).getLExpression().getType();
      }
    }
    else if (parent instanceof PsiReturnStatement) {
      final PsiElement psiElement = PsiTreeUtil.getParentOfType(parent, PsiLambdaExpression.class, PsiMethod.class);
      if (psiElement instanceof PsiLambdaExpression) {
        return null;
      }
      else if (psiElement instanceof PsiMethod){
        return ((PsiMethod)psiElement).getReturnType();
      }
    }
    else if (PsiUtil.isCondition(element, parent)) {
      return PsiType.BOOLEAN.getBoxedType(parent);
    } 
    else if (parent instanceof PsiArrayInitializerExpression) {
      final PsiElement gParent = parent.getParent();
      if (gParent instanceof PsiNewExpression) {
        final PsiType type = ((PsiNewExpression)gParent).getType();
        if (type instanceof PsiArrayType) {
          return ((PsiArrayType)type).getComponentType();
        }
      }
      else if (gParent instanceof PsiVariable) {
        final PsiType type = ((PsiVariable)gParent).getType();
        if (type instanceof PsiArrayType) {
          return ((PsiArrayType)type).getComponentType();
        }
      }
      else if (gParent instanceof PsiArrayInitializerExpression) {
        final PsiType expectedTypeByParent = getExpectedTypeByParent(parent);
        return expectedTypeByParent != null && expectedTypeByParent instanceof PsiArrayType
               ? ((PsiArrayType)expectedTypeByParent).getComponentType() : null;
      }
    }
    return null;
  }

  /**
   * Returns the return type for enclosing method or lambda
   *
   * @param element element inside method or lambda to determine the return type of
   * @return the return type or null if cannot be determined
   */
  @Nullable
  public static PsiType getMethodReturnType(PsiElement element) {
    final PsiElement methodOrLambda = PsiTreeUtil.getParentOfType(element, PsiMethod.class, PsiLambdaExpression.class);
    return methodOrLambda instanceof PsiMethod
           ? ((PsiMethod)methodOrLambda).getReturnType()
           : methodOrLambda instanceof PsiLambdaExpression ? LambdaUtil.getFunctionalInterfaceReturnType((PsiLambdaExpression)methodOrLambda) : null;
  }

  public static boolean compareTypes(PsiType leftType, PsiType rightType, boolean ignoreEllipsis) {
    if (ignoreEllipsis) {
      if (leftType instanceof PsiEllipsisType) {
        leftType = ((PsiEllipsisType)leftType).toArrayType();
      }
      if (rightType instanceof PsiEllipsisType) {
        rightType = ((PsiEllipsisType)rightType).toArrayType();
      }
    }
    return Comparing.equal(leftType, rightType);
  }

  public static boolean isDenotableType(PsiType type) {
    if (type instanceof PsiWildcardType || type instanceof PsiCapturedWildcardType) {
      return false;
    }
    return true;
  }
  
  public static boolean hasUnresolvedComponents(@NotNull PsiType type) {
    return type.accept(new PsiTypeVisitor<Boolean>() {
      @Nullable
      @Override
      public Boolean visitClassType(PsiClassType classType) {
        PsiClassType.ClassResolveResult resolveResult = classType.resolveGenerics();
        final PsiClass psiClass = resolveResult.getElement();
        if (psiClass == null) {
          return true;
        }
        PsiSubstitutor substitutor = resolveResult.getSubstitutor();
        for (PsiTypeParameter param : PsiUtil.typeParametersIterable(psiClass)) {
          PsiType psiType = substitutor.substitute(param);
          if (psiType != null && psiType.accept(this)) {
            return true;
          }
        }
        return super.visitClassType(classType);
      }

      @Nullable
      @Override
      public Boolean visitArrayType(PsiArrayType arrayType) {
        return arrayType.getComponentType().accept(this);
      }

      @NotNull
      @Override
      public Boolean visitWildcardType(PsiWildcardType wildcardType) {
        final PsiType bound = wildcardType.getBound();
        return bound != null && bound.accept(this);
      }

      @Override
      public Boolean visitType(PsiType type) {
        return false;
      }
    });
  }

  public static PsiType getParameterType(PsiParameter[] parameters, int i, boolean varargs) {
    final PsiParameter parameter = parameters[i < parameters.length ? i : parameters.length - 1];
    PsiType parameterType = parameter.getType();
    if (parameterType instanceof PsiEllipsisType && varargs) {
      parameterType = ((PsiEllipsisType)parameterType).getComponentType();
    }
    if (!parameterType.isValid()) {
      PsiUtil.ensureValidType(parameterType, "Invalid type of parameter " + parameter + " of " + parameter.getClass());
    }
    return parameterType;
  }

  @NotNull
  public static PsiTypeParameter[] filterUnusedTypeParameters(@NotNull PsiTypeParameter[] typeParameters,
                                                              final PsiType... types) {
    if (typeParameters.length == 0) return PsiTypeParameter.EMPTY_ARRAY;

    TypeParameterSearcher searcher = new TypeParameterSearcher();
    for (PsiType type : types) {
      type.accept(searcher);
    }
    return searcher.getTypeParameters().toArray(PsiTypeParameter.EMPTY_ARRAY);
  }

  @NotNull
  public static PsiTypeParameter[] filterUnusedTypeParameters(final PsiType superReturnTypeInBaseClassType,
                                                              @NotNull PsiTypeParameter[] typeParameters) {
    return filterUnusedTypeParameters(typeParameters, superReturnTypeInBaseClassType);
  }

  public static class TypeParameterSearcher extends PsiTypeVisitor<Boolean> {
    private final Set<PsiTypeParameter> myTypeParams = new HashSet<>();

    public Set<PsiTypeParameter> getTypeParameters() {
      return myTypeParams;
    }

    public Boolean visitType(final PsiType type) {
      return false;
    }

    public Boolean visitArrayType(final PsiArrayType arrayType) {
      return arrayType.getComponentType().accept(this);
    }

    public Boolean visitClassType(final PsiClassType classType) {
      PsiClassType.ClassResolveResult resolveResult = classType.resolveGenerics();
      final PsiClass aClass = resolveResult.getElement();
      if (aClass instanceof PsiTypeParameter) {
        myTypeParams.add((PsiTypeParameter)aClass);
      }

      if (aClass != null) {
        PsiSubstitutor substitutor = resolveResult.getSubstitutor();
        for (final PsiTypeParameter parameter : PsiUtil.typeParametersIterable(aClass)) {
          PsiType psiType = substitutor.substitute(parameter);
          if (psiType != null) {
            psiType.accept(this);
          }
        }
      }
      return false;
    }

    public Boolean visitWildcardType(final PsiWildcardType wildcardType) {
      final PsiType bound = wildcardType.getBound();
      if (bound != null) {
        bound.accept(this);
      }
      return false;
    }
  }
}
