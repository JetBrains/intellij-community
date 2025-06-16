// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

public final class PsiTypesUtil {
  private static final Logger LOG = Logger.getInstance(PsiTypesUtil.class);
  private static final @NonNls Map<String, String> ourUnboxedTypes = new HashMap<>();
  private static final @NonNls Map<String, String> ourBoxedTypes = new HashMap<>();

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

  private static final @NonNls String GET_CLASS_METHOD = "getClass";

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

  /**
   * @param type type to get default value for (null, 0, or false)
   * @return a string representing an expression for default value of a given type
   */
  public static @NotNull String getDefaultValueOfType(@Nullable PsiType type) {
    return getDefaultValueOfType(type, false);
  }

  /**
   * @param type type to return default value for
   * @param customDefaultValues if true, non-null values for object types could be returned that represent an absent value 
   *                            for a specific type (e.g., empty string, empty list, etc.) 
   * @return a string representing an expression for default value of a given type
   */
  public static @NotNull String getDefaultValueOfType(@Nullable PsiType type, boolean customDefaultValues) {
    if (type instanceof PsiPrimitiveType) {
      return PsiTypes.booleanType().equals(type) ? JavaKeywords.FALSE : "0";
    }
    if (customDefaultValues) {
      if (type instanceof PsiArrayType) {
        int count = type.getArrayDimensions() - 1;
        PsiType componentType = type.getDeepComponentType();

        if (componentType instanceof PsiClassType) {
          final PsiClassType classType = (PsiClassType)componentType;
          if (classType.resolve() instanceof PsiTypeParameter) {
            return JavaKeywords.NULL;
          }
        }

        PsiType erasedComponentType = TypeConversionUtil.erasure(componentType);
        StringBuilder buffer = new StringBuilder();
        buffer.append(JavaKeywords.NEW);
        buffer.append(" ");
        buffer.append(erasedComponentType.getCanonicalText());
        buffer.append("[0]");
        for (int i = 0; i < count; i++) {
          buffer.append("[]");
        }
        return buffer.toString();
      }

      PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(type);
      if (psiClass != null) {
        String typeText = psiClass.getQualifiedName();
        if (typeText != null) {
          switch (typeText) {
            case CommonClassNames.JAVA_UTIL_OPTIONAL:
            case "java.util.OptionalInt":
            case "java.util.OptionalLong":
            case "java.util.OptionalDouble":
            case CommonClassNames.JAVA_UTIL_STREAM_STREAM:
            case CommonClassNames.JAVA_UTIL_STREAM_INT_STREAM:
            case CommonClassNames.JAVA_UTIL_STREAM_LONG_STREAM:
            case CommonClassNames.JAVA_UTIL_STREAM_DOUBLE_STREAM:
              return typeText + ".empty()";
            case CommonClassNames.JAVA_LANG_STRING:
              return "\"\"";
            case CommonClassNames.JAVA_LANG_LONG:
              return "0L";
            case CommonClassNames.JAVA_LANG_INTEGER:
            case CommonClassNames.JAVA_LANG_SHORT:
            case CommonClassNames.JAVA_LANG_BYTE:
              return "0";
            case CommonClassNames.JAVA_LANG_FLOAT:
              return "0f";
            case CommonClassNames.JAVA_LANG_DOUBLE:
              return "0.0";
            case CommonClassNames.JAVA_UTIL_SET:
              return PsiUtil.isAvailable(JavaFeature.COLLECTION_FACTORIES, psiClass) ? "java.util.Set.of()" : "java.util.Collections.emptySet()";
            case CommonClassNames.JAVA_UTIL_COLLECTION:
            case CommonClassNames.JAVA_UTIL_LIST:
              return PsiUtil.isAvailable(JavaFeature.COLLECTION_FACTORIES, psiClass) ? "java.util.List.of()" : "java.util.Collections.emptyList()";
            case CommonClassNames.JAVA_UTIL_MAP:
              return PsiUtil.isAvailable(JavaFeature.COLLECTION_FACTORIES, psiClass) ? "java.util.Map.of()" : "java.util.Collections.emptyMap()";
          }
        }
      }
    }
    return JavaKeywords.NULL;
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
  public static @Nullable String boxIfPossible(@Nullable String type) {
    if (type == null) return null;
    final String s = ourBoxedTypes.get(type);
    return s == null ? type : s;
  }

  public static @Nullable PsiClass getPsiClass(@Nullable PsiType psiType) {
    return psiType instanceof PsiClassType? ((PsiClassType)psiType).resolve() : null;
  }

  public static @NotNull PsiClassType getClassType(@NotNull PsiClass psiClass) {
    return JavaPsiFacade.getElementFactory(psiClass.getProject()).createType(psiClass);
  }

  public static @Nullable PsiClassType getLowestUpperBoundClassType(final @NotNull PsiDisjunctionType type) {
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
    if (JavaFeature.GENERICS.isSufficient(languageLevel) && isGetClass(method)) {
      PsiExpression qualifier = methodExpression.getQualifierExpression();
      PsiType qualifierType = null;
      final Project project = call.getProject();
      if (qualifier != null) {
        qualifierType = TypeConversionUtil.erasure(qualifier.getType());
      }
      else {
        PsiElement parent = call.getContext();
        while (parent != null && condition.value(parent instanceof StubBasedPsiElement ? ((StubBasedPsiElement<?>)parent).getIElementType()
                                                                                       : parent.getNode().getElementType())) {
          parent = parent.getContext();
        }
        if (parent != null) {
          qualifierType = JavaPsiFacade.getElementFactory(project).createType((PsiClass)parent);
        }
      }
      if (PsiTypes.nullType().equals(qualifierType)) {
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

  public static @Nullable PsiType createJavaLangClassType(@NotNull PsiElement context, @Nullable PsiType qualifierType, boolean captureTopLevelWildcards) {
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
  public static @Nullable PsiType getExpectedTypeByParent(@NotNull PsiElement element) {
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
        return !PsiTypes.nullType().equals(type) ? type : null;
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
      return PsiTypes.booleanType();
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
  public static @Nullable PsiType getMethodReturnType(@NotNull PsiElement element) {
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
  @Contract("null, _ -> false")
  public static boolean isDenotableType(@Nullable PsiType type, @NotNull PsiElement context) {
    if (type == null || type instanceof PsiWildcardType || type instanceof PsiCapturedWildcardType || type instanceof PsiIntersectionType) return false;
    type = type.getDeepComponentType();
    if (type instanceof PsiPrimitiveType) {
      return !PsiTypes.nullType().equals(type);
    }
    if (type instanceof PsiClassType) {
      String className = ((PsiClassType)type).getClassName();
      if (className == null) return false;
      LanguageLevel level = PsiUtil.getLanguageLevel(context);
      if (PsiUtil.isKeyword(className, level) || PsiUtil.isSoftKeyword(className, level)) return false;
      for (PsiType parameter : ((PsiClassType)type).getParameters()) {
        if (parameter instanceof PsiWildcardType) {
          parameter = ((PsiWildcardType)parameter).getBound();
        }
        if (parameter != null && !isDenotableType(parameter, context)) return false;
      }
      return true;
    }
    return false;
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
      @Override
      public @Nullable Boolean visitClassType(@NotNull PsiClassType classType) {
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

      @Override
      public @Nullable Boolean visitArrayType(@NotNull PsiArrayType arrayType) {
        return arrayType.getComponentType().accept(this);
      }

      @Override
      public @NotNull Boolean visitWildcardType(@NotNull PsiWildcardType wildcardType) {
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
  public static @NotNull PsiType getParameterType(PsiParameter @NotNull [] parameters, int i, boolean varargs) {
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
    return ContainerUtil.and(parameters, parameter -> isAccessibleAt(parameter, context));
  }

  public static @NotNull PsiType createArrayType(@NotNull PsiType newType, int arrayDim) {
    for(int i = 0; i < arrayDim; i++){
      newType = newType.createArrayType();
    }
    return newType;
  }

  /**
   * @return null if type can't be explicitly specified
   */
  public static @Nullable PsiTypeElement replaceWithExplicitType(PsiTypeElement typeElement) {
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
  public static boolean mentionsTypeParameters(@Nullable PsiType type, @NotNull Set<? extends PsiTypeParameter> typeParameters) {
    return mentionsTypeParametersOrUnboundedWildcard(type, typeParameters::contains);
  }

  public static boolean mentionsTypeParameters(@Nullable PsiType type, @NotNull Predicate<? super PsiTypeParameter> wantedTypeParameter) {
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
                                                                   final Predicate<? super PsiTypeParameter> wantedTypeParameter) {
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

  /**
   * @return class types which are probably wrapped inside captured wildcard or inside intersection type
   */
  public static @NotNull List<? extends PsiClassType> getClassTypeComponents(PsiType type) {
    if (type instanceof PsiClassType) return Collections.singletonList((PsiClassType)type);
    if (type instanceof PsiCapturedWildcardType) {
      return getClassTypeComponents(((PsiCapturedWildcardType)type).getUpperBound());
    }
    if (type instanceof PsiIntersectionType) {
      List<PsiClassType> classTypes = new ArrayList<>();
      for (PsiType conjunct : ((PsiIntersectionType)type).getConjuncts()) {
        classTypes.addAll(getClassTypeComponents(conjunct));
      }
      return classTypes;
    }
    return Collections.emptyList();
  }

  /**
   * @param type input type
   * @return type with removed external annotations (if any); on any level of depth
   */
  public static @NotNull PsiType removeExternalAnnotations(@NotNull PsiType type) {
    PsiAnnotation[] annotations = type.getAnnotations();
    if (annotations.length > 0) {
      List<PsiAnnotation> newAnnotations = ContainerUtil.filter(
        annotations, annotation -> !ExternalAnnotationsManager.getInstance(annotation.getProject()).isExternalAnnotation(annotation));
      if (newAnnotations.size() < annotations.length) {
        type = type.annotate(TypeAnnotationProvider.Static.create(newAnnotations.toArray(PsiAnnotation.EMPTY_ARRAY)));
      }
    }
    if (type instanceof PsiClassType) {
      PsiClassType classType = (PsiClassType)type;
      PsiClass psiClass = classType.resolve();
      if (psiClass != null) {
        PsiType[] parameters = classType.getParameters();
        boolean changed = false;
        for (int i = 0; i < parameters.length; i++) {
          PsiType parameter = parameters[i];
          PsiType updatedParameter = removeExternalAnnotations(parameter);
          parameters[i] = updatedParameter;
          changed |= updatedParameter != parameter;
        }
        if (changed) {
          return JavaPsiFacade.getElementFactory(psiClass.getProject()).createType(psiClass, parameters);
        }
      }
      return type;
    }
    else if (type instanceof PsiArrayType) {
      PsiArrayType arrayType = (PsiArrayType)type;
      PsiType origComponentType = arrayType.getComponentType();
      PsiType componentType = removeExternalAnnotations(origComponentType);
      return componentType == origComponentType ? type : componentType.createArrayType();
    }
    else if (type instanceof PsiWildcardType) {
      PsiWildcardType wildcardType = (PsiWildcardType)type;
      PsiType bound = wildcardType.getBound();
      return bound == null ? wildcardType : 
             wildcardType.isExtends() ? PsiWildcardType.createExtends(wildcardType.getManager(), removeExternalAnnotations(bound)) :
             wildcardType.isSuper() ? PsiWildcardType.createSuper(wildcardType.getManager(), removeExternalAnnotations(bound)) : 
             wildcardType;
    }
    else if (type instanceof PsiIntersectionType) {
      PsiIntersectionType intersectionType = (PsiIntersectionType)type;
      PsiType[] conjuncts = intersectionType.getConjuncts();
      PsiType[] newConjuncts = new PsiType[conjuncts.length];
      for (int i = 0; i < conjuncts.length; i++) {
        newConjuncts[i] = removeExternalAnnotations(conjuncts[i]);
      }
      return PsiIntersectionType.createIntersection(newConjuncts);
    }
    return type;
  }

  /**
   * @param type type to check
   * @return true if a given type is a valid return type for an annotation method
   */
  public static boolean isValidAnnotationMethodType(@NotNull PsiType type) {
    if (type instanceof PsiArrayType) {
      PsiArrayType arrayType = (PsiArrayType)type;
      if (arrayType.getArrayDimensions() != 1) return false;
      type = arrayType.getComponentType();
    }
    if (type instanceof PsiPrimitiveType) {
      return !PsiTypes.voidType().equals(type) && !PsiTypes.nullType().equals(type);
    }
    if (type instanceof PsiClassType) {
      PsiClassType classType = (PsiClassType)type;
      if (classType.getParameters().length > 0) {
        return classNameEquals(classType, CommonClassNames.JAVA_LANG_CLASS);
      }
      if (classType.equalsToText(CommonClassNames.JAVA_LANG_CLASS) || classType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
        return true;
      }

      PsiClass aClass = classType.resolve();
      return aClass != null && (aClass.isAnnotationType() || aClass.isEnum());
    }
    return false;
  }

  /**
   * @param typeName name of the type to test
   * @param level language level
   * @return true if a given name cannot be used as a type name at a given language level
   * @see PsiUtil#isSoftKeyword(CharSequence, LanguageLevel) - this method is similar but some soft keywords still can be types 
    * (e.g. 'when')
   */
  public static boolean isRestrictedIdentifier(@Nullable String typeName, @NotNull LanguageLevel level) {
    return JavaKeywords.VAR.equals(typeName) && JavaFeature.LVTI.isSufficient(level) ||
           JavaKeywords.YIELD.equals(typeName) && JavaFeature.SWITCH_EXPRESSION.isSufficient(level) ||
           JavaKeywords.RECORD.equals(typeName) && JavaFeature.RECORDS.isSufficient(level) ||
           (JavaKeywords.SEALED.equals(typeName) || JavaKeywords.PERMITS.equals(typeName)) && JavaFeature.SEALED_CLASSES.isSufficient(level) ||
           JavaKeywords.VALUE.equals(typeName) && JavaFeature.VALHALLA_VALUE_CLASSES.isSufficient(level);
  }

  public static class TypeParameterSearcher extends PsiTypeVisitor<Boolean> {
    private final Set<PsiTypeParameter> myTypeParams = new HashSet<>();

    public @NotNull Set<PsiTypeParameter> getTypeParameters() {
      return myTypeParams;
    }

    @Override
    public Boolean visitType(final @NotNull PsiType type) {
      return false;
    }

    @Override
    public Boolean visitArrayType(final @NotNull PsiArrayType arrayType) {
      return arrayType.getComponentType().accept(this);
    }

    @Override
    public Boolean visitClassType(final @NotNull PsiClassType classType) {
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
    public Boolean visitWildcardType(final @NotNull PsiWildcardType wildcardType) {
      final PsiType bound = wildcardType.getBound();
      if (bound != null) {
        bound.accept(this);
      }
      return false;
    }
  }
}
