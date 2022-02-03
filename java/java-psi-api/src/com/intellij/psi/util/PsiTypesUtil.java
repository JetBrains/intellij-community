// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public final class PsiTypesUtil {
  private static final Logger LOG = Logger.getInstance(PsiTypesUtil.class);
  @NonNls private static final Map<String, String> ourUnboxedTypes = new HashMap<>();
  @NonNls private static final Map<String, String> ourBoxedTypes = new HashMap<>();

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
  public static @Nullable @NonNls String unboxIfPossible(@Nullable @NonNls String type) {
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
  public static String boxIfPossible(@Nullable String type) {
    if (type == null) return null;
    final String s = ourBoxedTypes.get(type);
    return s == null ? type : s;
  }

  @Nullable
  public static PsiClass getPsiClass(@Nullable PsiType psiType) {
    return psiType instanceof PsiClassType? ((PsiClassType)psiType).resolve() : null;
  }

  @NotNull
  public static PsiClassType getClassType(@NotNull PsiClass psiClass) {
    return JavaPsiFacade.getElementFactory(psiClass.getProject()).createType(psiClass);
  }

  @Nullable
  public static PsiClassType getLowestUpperBoundClassType(@NotNull final PsiDisjunctionType type) {
    final PsiType lub = type.getLeastUpperBound();
    if (lub instanceof PsiClassType) {
      return (PsiClassType)lub;
    }
    if (lub instanceof PsiIntersectionType) {
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
                                                      @NotNull Condition<? super IElementType> condition,
                                                      @NotNull LanguageLevel languageLevel) {
    //JLS3 15.8.2
    if (languageLevel.isAtLeast(LanguageLevel.JDK_1_5) && isGetClass(method)) {
      PsiExpression qualifier = methodExpression.getQualifierExpression();
      PsiType qualifierType = null;
      final Project project = call.getProject();
      if (qualifier != null) {
        qualifierType = TypeConversionUtil.erasure(qualifier.getType());
      }
      else {
        PsiElement parent = call.getContext();
        while (parent != null && condition.value(parent instanceof StubBasedPsiElement ? ((StubBasedPsiElement<?>)parent).getElementType()
                                                                                       : parent.getNode().getElementType())) {
          parent = parent.getContext();
        }
        if (parent != null) {
          qualifierType = JavaPsiFacade.getElementFactory(project).createType((PsiClass)parent);
        }
      }
      if (PsiType.NULL.equals(qualifierType)) {
        LOG.error("Unexpected null qualifier", new Attachment("expression.txt", call.getText()));
      }
      return createJavaLangClassType(methodExpression, qualifierType, true);
    }
    return null;
  }

  public static boolean isGetClass(@NotNull PsiMethod method) {
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
        PsiTypeElement typeElement = ((PsiVariable)parent).getTypeElement();
        if (typeElement != null && typeElement.isInferredType()) {
          return null;
        }
        return ((PsiVariable)parent).getType();
      }
    }
    else if (parent instanceof PsiAssignmentExpression) {
      if (((PsiAssignmentExpression)parent).getOperationSign().getTokenType() == JavaTokenType.EQ &&
          PsiUtil.checkSameExpression(element, ((PsiAssignmentExpression)parent).getRExpression())) {
        PsiType type = ((PsiAssignmentExpression)parent).getLExpression().getType();
        return !PsiType.NULL.equals(type) ? type : null;
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
      return PsiType.BOOLEAN;
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
        return expectedTypeByParent instanceof PsiArrayType ? ((PsiArrayType)expectedTypeByParent).getComponentType() : null;
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
  public static PsiType getMethodReturnType(@NotNull PsiElement element) {
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

  /**
     * @param context in which type should be checked
     * @return false if type is null or has no explicit canonical type representation (e. g. intersection type)
     */
  public static boolean isDenotableType(@Nullable PsiType type, @NotNull PsiElement context) {
    if (type == null || type instanceof PsiWildcardType) return false;
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(context.getProject());
    try {
      PsiType typeAfterReplacement = elementFactory.createTypeElementFromText(type.getCanonicalText(), context).getType();
      return type.equals(typeAfterReplacement);
    } catch (IncorrectOperationException e) {
      return false;
    }
  }

  /**
   * @param type type to check
   * @return true if given type has a type annotation anywhere inside its declaration
   */
  public static boolean hasTypeAnnotation(@NotNull PsiType type) {
    return type.accept(new PsiTypeVisitor<Boolean>() {
      @Override
      public Boolean visitType(@NotNull PsiType type) {
        return type.getAnnotations().length > 0;
      }

      @Override
      public Boolean visitClassType(@NotNull PsiClassType classType) {
        if (super.visitClassType(classType)) {
          return true;
        }

        for (PsiType t1 : classType.getParameters()) {
          if (t1.accept(this)) {
            return true;
          }
        }
        return false;
      }

      @Override
      public Boolean visitArrayType(@NotNull PsiArrayType arrayType) {
        return super.visitArrayType(arrayType) || arrayType.getComponentType().accept(this);
      }

      @Override
      public Boolean visitWildcardType(@NotNull PsiWildcardType wildcardType) {
        return super.visitWildcardType(wildcardType) || wildcardType.getBound() != null && wildcardType.getBound().accept(this);
      }

      @Override
      public Boolean visitIntersectionType(@NotNull PsiIntersectionType intersectionType) {
        for (PsiType t1 : intersectionType.getConjuncts()) {
          if (t1.accept(this)) {
            return true;
          }
        }
        return false;
      }

      @Override
      public Boolean visitDisjunctionType(@NotNull PsiDisjunctionType disjunctionType) {
        for (PsiType t1 : disjunctionType.getDisjunctions()) {
          if (t1.accept(this)) {
            return true;
          }
        }
        return false;
      }
    });
  }

  public static boolean hasUnresolvedComponents(@NotNull PsiType type) {
    return type.accept(new PsiTypeVisitor<Boolean>() {
      @Nullable
      @Override
      public Boolean visitClassType(@NotNull PsiClassType classType) {
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
      public Boolean visitArrayType(@NotNull PsiArrayType arrayType) {
        return arrayType.getComponentType().accept(this);
      }

      @NotNull
      @Override
      public Boolean visitWildcardType(@NotNull PsiWildcardType wildcardType) {
        final PsiType bound = wildcardType.getBound();
        return bound != null && bound.accept(this);
      }

      @Override
      public Boolean visitType(@NotNull PsiType type) {
        return false;
      }
    });
  }

  /**
   * @return i's parameter type. 
   *         Never returns ellipsis type: in case of vararg usage, it returns the corresponding component type, otherwise an array type.
   */
  @NotNull
  public static PsiType getParameterType(PsiParameter @NotNull [] parameters, int i, boolean varargs) {
    final PsiParameter parameter = parameters[i < parameters.length ? i : parameters.length - 1];
    PsiType parameterType = parameter.getType();
    if (parameterType instanceof PsiEllipsisType) {
      if (varargs) {
        parameterType = ((PsiEllipsisType)parameterType).getComponentType();
      }
      else {
        parameterType = ((PsiEllipsisType)parameterType).toArrayType();
      }
    }
    if (!parameterType.isValid()) {
      PsiUtil.ensureValidType(parameterType, "Invalid type of parameter " + parameter + " of " + parameter.getClass());
    }
    return parameterType;
  }

  public static PsiTypeParameter @NotNull [] filterUnusedTypeParameters(PsiTypeParameter @NotNull [] typeParameters, PsiType @NotNull ... types) {
    if (typeParameters.length == 0) return PsiTypeParameter.EMPTY_ARRAY;

    TypeParameterSearcher searcher = new TypeParameterSearcher();
    for (PsiType type : types) {
      type.accept(searcher);
    }
    return searcher.getTypeParameters().toArray(PsiTypeParameter.EMPTY_ARRAY);
  }

  public static PsiTypeParameter @NotNull [] filterUnusedTypeParameters(@NotNull PsiType superReturnTypeInBaseClassType,
                                                                        PsiTypeParameter @NotNull [] typeParameters) {
    return filterUnusedTypeParameters(typeParameters, superReturnTypeInBaseClassType);
  }

  private static boolean isAccessibleAt(@NotNull PsiTypeParameter parameter, @NotNull PsiElement context) {
    PsiTypeParameterListOwner owner = parameter.getOwner();
    if(owner instanceof PsiMethod) {
      return PsiTreeUtil.isAncestor(owner, context, false);
    }
    if(owner instanceof PsiClass) {
      return PsiTreeUtil.isAncestor(owner, context, false) &&
             InheritanceUtil.hasEnclosingInstanceInScope((PsiClass)owner, context, false, false);
    }
    return false;
  }

  public static boolean allTypeParametersResolved(@NotNull PsiElement context, @NotNull PsiType targetType) {
    TypeParameterSearcher searcher = new TypeParameterSearcher();
    targetType.accept(searcher);
    Set<PsiTypeParameter> parameters = searcher.getTypeParameters();
    return parameters.stream().allMatch(parameter -> isAccessibleAt(parameter, context));
  }

  @NotNull
  public static PsiType createArrayType(@NotNull PsiType newType, int arrayDim) {
    for(int i = 0; i < arrayDim; i++){
      newType = newType.createArrayType();
    }
    return newType;
  }

  /**
   * @return null if type can't be explicitly specified
   */
  @Nullable
  public static PsiTypeElement replaceWithExplicitType(PsiTypeElement typeElement) {
    PsiType type = typeElement.getType();
    if (!isDenotableType(type, typeElement)) {
      return null;
    }
    Project project = typeElement.getProject();
    PsiTypeElement typeElementByExplicitType = JavaPsiFacade.getElementFactory(project).createTypeElement(type);
    PsiElement explicitTypeElement = typeElement.replace(typeElementByExplicitType);
    explicitTypeElement = JavaCodeStyleManager.getInstance(project).shortenClassReferences(explicitTypeElement);
    return (PsiTypeElement)CodeStyleManager.getInstance(project).reformat(explicitTypeElement);
  }

  public static PsiType getTypeByMethod(@NotNull PsiElement context,
                                        PsiExpressionList argumentList,
                                        PsiElement parentMethod,
                                        boolean varargs,
                                        PsiSubstitutor substitutor,
                                        boolean inferParent) {
    if (parentMethod instanceof PsiMethod) {
      final PsiParameter[] parameters = ((PsiMethod)parentMethod).getParameterList().getParameters();
      if (parameters.length == 0) return null;
      final PsiExpression[] args = argumentList.getExpressions();
      if (!((PsiMethod)parentMethod).isVarArgs() && parameters.length != args.length && !inferParent) return null;
      PsiElement arg = context;
      while (arg.getParent() instanceof PsiParenthesizedExpression) {
        arg = arg.getParent();
      }
      final int i = ArrayUtilRt.find(args, arg);
      if (i < 0) return null;
      final PsiType parameterType = substitutor != null ? substitutor.substitute(getParameterType(parameters, i, varargs)) : null;
      final boolean isRaw = substitutor != null && PsiUtil.isRawSubstitutor((PsiMethod)parentMethod, substitutor);
      return isRaw ? TypeConversionUtil.erasure(parameterType) : parameterType;
    }
    return null;
  }

  /**
   * Checks if {@code type} mentions type parameters from the passed {@code Set}
   * Implicit type arguments of types based on inner classes of generic outer classes are explicitly checked
   */
  public static boolean mentionsTypeParameters(@Nullable PsiType type, @NotNull Set<PsiTypeParameter> typeParameters) {
    return mentionsTypeParametersOrUnboundedWildcard(type, typeParameters::contains);
  }

  public static boolean mentionsTypeParameters(@Nullable PsiType type, @NotNull Predicate<PsiTypeParameter> wantedTypeParameter) {
    return mentionsTypeParametersOrUnboundedWildcard(type, wantedTypeParameter);
  }

  /**
   * Checks if {@code resolveResult} depicts unchecked method call
   */
  public static boolean isUncheckedCall(JavaResolveResult resolveResult) {
    final PsiElement element = resolveResult.getElement();
    if (element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)element;
      PsiSubstitutor substitutor = resolveResult.getSubstitutor();
      if (PsiUtil.isRawSubstitutor(method, substitutor)) {
        for (PsiParameter t : method.getParameterList().getParameters()) {
          PsiType type = t.getType().getDeepComponentType();
          if (type instanceof PsiClassType) {
            PsiClass aClass = ((PsiClassType)type).resolveGenerics().getElement();
            if (aClass instanceof PsiTypeParameter || 
                aClass != null && PsiUtil.typeParametersIterator(aClass).hasNext() && !((PsiClassType)type).isRaw()) {
              return true;
            }
          }
        }
        return false;
      }
    }
    return false;
  }

  private static boolean mentionsTypeParametersOrUnboundedWildcard(@Nullable PsiType type,
                                                                   final Predicate<PsiTypeParameter> wantedTypeParameter) {
    if (type == null) return false;
    return type.accept(new PsiTypeVisitor<Boolean>() {
      @Override
      public Boolean visitType(@NotNull PsiType type) {
        return false;
      }

      @Override
      public Boolean visitWildcardType(@NotNull PsiWildcardType wildcardType) {
        final PsiType bound = wildcardType.getBound();
        return bound != null ? bound.accept(this) 
                             : Boolean.valueOf(false);
      }

      @Override
      public Boolean visitClassType(@NotNull PsiClassType classType) {
        PsiClassType.ClassResolveResult result = classType.resolveGenerics();
        final PsiClass psiClass = result.getElement();
        if (psiClass != null) {
          PsiSubstitutor substitutor = result.getSubstitutor();
          for (PsiTypeParameter parameter : PsiUtil.typeParametersIterable(psiClass)) {
            PsiType type = substitutor.substitute(parameter);
            if (type != null && type.accept(this)) return true;
          }
        }
        return psiClass instanceof PsiTypeParameter && wantedTypeParameter.test((PsiTypeParameter)psiClass);
      }

      @Override
      public Boolean visitIntersectionType(@NotNull PsiIntersectionType intersectionType) {
        for (PsiType conjunct : intersectionType.getConjuncts()) {
          if (conjunct.accept(this)) return true;
        }
        return false;
      }

      @Override
      public Boolean visitMethodReferenceType(@NotNull PsiMethodReferenceType methodReferenceType) {
        return false;
      }

      @Override
      public Boolean visitLambdaExpressionType(@NotNull PsiLambdaExpressionType lambdaExpressionType) {
        return false;
      }

      @Override
      public Boolean visitArrayType(@NotNull PsiArrayType arrayType) {
        return arrayType.getComponentType().accept(this);
      }
    });
  }

  /**
   * @param type type to test
   * @param qualifiedClassName desired fully-qualified class name
   * @return true if given type is a class type that resolves to the specified class
   */
  @Contract("null, _ -> false")
  public static boolean classNameEquals(@Nullable PsiType type, @NotNull String qualifiedClassName) {
    if (!(type instanceof PsiClassType)) return false;
    PsiClassType classType = (PsiClassType)type;
    String className = classType.getClassName();
    if (className == null || !qualifiedClassName.endsWith(className)) return false;
    PsiClass psiClass = classType.resolve();
    if (psiClass == null) return false;
    return qualifiedClassName.equals(psiClass.getQualifiedName());
  }

  public static class TypeParameterSearcher extends PsiTypeVisitor<Boolean> {
    private final Set<PsiTypeParameter> myTypeParams = new HashSet<>();

    @NotNull
    public Set<PsiTypeParameter> getTypeParameters() {
      return myTypeParams;
    }

    @Override
    public Boolean visitType(@NotNull final PsiType type) {
      return false;
    }

    @Override
    public Boolean visitArrayType(@NotNull final PsiArrayType arrayType) {
      return arrayType.getComponentType().accept(this);
    }

    @Override
    public Boolean visitClassType(@NotNull final PsiClassType classType) {
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

    @Override
    public Boolean visitWildcardType(@NotNull final PsiWildcardType wildcardType) {
      final PsiType bound = wildcardType.getBound();
      if (bound != null) {
        bound.accept(this);
      }
      return false;
    }
  }
}
