// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.resolve.reference.impl;

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.callMatcher.CallMapper;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.DeclarationSearchUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.invoke.MethodType;
import java.util.*;
import java.util.function.Function;

import static com.intellij.psi.CommonClassNames.*;
import static com.siyeh.ig.callMatcher.CallMatcher.anyOf;
import static com.siyeh.ig.callMatcher.CallMatcher.staticCall;

public final class JavaReflectionReferenceUtil {
  // MethodHandle (Java 7) and VarHandle (Java 9) infrastructure
  public static final String JAVA_LANG_INVOKE_METHOD_HANDLES_LOOKUP = "java.lang.invoke.MethodHandles.Lookup";
  public static final String JAVA_LANG_INVOKE_METHOD_TYPE = "java.lang.invoke.MethodType";
  public static final String JAVA_LANG_CONSTANT_CLASS_DESC = "java.lang.constant.ClassDesc";

  public static final String METHOD_TYPE = "methodType";
  public static final String GENERIC_METHOD_TYPE = "genericMethodType";
  private static final CallMatcher LIST_FACTORY = anyOf(
    staticCall(JAVA_UTIL_LIST, "of"),
    staticCall(JAVA_UTIL_ARRAYS, "asList")
  );
  private static final CallMatcher.Simple METHOD_TYPE_MATCHER = staticCall(JAVA_LANG_INVOKE_METHOD_TYPE, METHOD_TYPE);
  public static final CallMatcher METHOD_TYPE_WITH_METHOD_TYPE_MATCHER =
    METHOD_TYPE_MATCHER.parameterTypes(JAVA_LANG_CLASS, JAVA_LANG_INVOKE_METHOD_TYPE);
  public static final CallMatcher METHOD_TYPE_WITH_LIST_MATCHER =
    METHOD_TYPE_MATCHER.parameterTypes(JAVA_LANG_CLASS, JAVA_UTIL_LIST);
  public static final CallMatcher METHOD_TYPE_WITH_CLASSES_MATCHER = anyOf(
    METHOD_TYPE_MATCHER.parameterCount(3),
    METHOD_TYPE_MATCHER.parameterCount(1),
    METHOD_TYPE_MATCHER.parameterTypes(JAVA_LANG_CLASS, JAVA_LANG_CLASS)
  );
  public static final CallMatcher METHOD_TYPE_WITH_ARRAY_MATCHER =
    METHOD_TYPE_MATCHER.parameterTypes(JAVA_LANG_CLASS, JAVA_LANG_CLASS + "<?>[]");
  public static final CallMatcher GENERIC_METHOD_TYPE_MATCHER = staticCall(JAVA_LANG_INVOKE_METHOD_TYPE, GENERIC_METHOD_TYPE);
  private static final CallMapper<ReflectiveSignature> SIGNATURE_MAPPER = new CallMapper<ReflectiveSignature>()
    .register(METHOD_TYPE_WITH_CLASSES_MATCHER,
              call -> composeMethodSignatureFromTypes(call.getArgumentList().getExpressions()))
    .register(METHOD_TYPE_WITH_LIST_MATCHER,
              call -> composeMethodSignatureFromReturnTypeAndList(call.getArgumentList().getExpressions()))
    .register(METHOD_TYPE_WITH_ARRAY_MATCHER,
              call -> composeMethodSignatureFromReturnTypeAndArray(call.getArgumentList().getExpressions()))
    .register(METHOD_TYPE_WITH_METHOD_TYPE_MATCHER,
              call -> composeMethodSignatureFromReturnTypeAndMethodType(call.getArgumentList().getExpressions()))
    .register(GENERIC_METHOD_TYPE_MATCHER,
              call -> composeGenericMethodSignature(call.getArgumentList().getExpressions()));

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
  public static final String FIND_CLASS = "findClass";

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
  public static final String NEW_INSTANCE = "newInstance";
  public static final String TYPE = "TYPE";

  // Atomic field updaters
  public static final String NEW_UPDATER = "newUpdater";
  public static final String ATOMIC_LONG_FIELD_UPDATER = "java.util.concurrent.atomic.AtomicLongFieldUpdater";
  public static final String ATOMIC_INTEGER_FIELD_UPDATER = "java.util.concurrent.atomic.AtomicIntegerFieldUpdater";
  public static final String ATOMIC_REFERENCE_FIELD_UPDATER = "java.util.concurrent.atomic.AtomicReferenceFieldUpdater";

  private static final RecursionGuard<PsiElement> ourGuard = RecursionManager.createGuard("JavaLangClassMemberReference");

  @Contract("null -> null")
  public static ReflectiveType getReflectiveType(@Nullable PsiExpression context) {
    context = PsiUtil.skipParenthesizedExprDown(context);
    if (context == null) {
      return null;
    }
    if (context instanceof PsiClassObjectAccessExpression) {
      final PsiTypeElement operand = ((PsiClassObjectAccessExpression)context).getOperand();
      return ReflectiveType.create(operand.getType(), true);
    }

    if (context instanceof PsiMethodCallExpression methodCall) {
      final String methodReferenceName = methodCall.getMethodExpression().getReferenceName();
      if (FOR_NAME.equals(methodReferenceName)) {
        final PsiMethod method = methodCall.resolveMethod();
        if (method != null && isJavaLangClass(method.getContainingClass())) {
          final PsiExpression[] expressions = methodCall.getArgumentList().getExpressions();
          if (expressions.length == 1) {
            final PsiExpression argument = findDefinition(PsiUtil.skipParenthesizedExprDown(expressions[0]));
            final String className = computeConstantExpression(argument, String.class);
            if (className != null) {
              return ReflectiveType.create(findClass(className, context), true);
            }
          }
        }
      }
      else if (GET_CLASS.equals(methodReferenceName) && methodCall.getArgumentList().isEmpty()) {
        final PsiMethod method = methodCall.resolveMethod();
        if (method != null && isJavaLangObject(method.getContainingClass())) {
          final PsiExpression qualifier = PsiUtil.skipParenthesizedExprDown(methodCall.getMethodExpression().getQualifierExpression());
          if (qualifier instanceof PsiReferenceExpression) {
            final PsiExpression definition = findVariableDefinition((PsiReferenceExpression)qualifier);
            if (definition != null) {
              return getClassInstanceType(definition);
            }
          }
          //TODO type of the qualifier may be a supertype of the actual value - need to compute the type of the actual value
          // otherwise getDeclaredField and getDeclaredMethod may work not reliably
          if (qualifier != null) {
            return getClassInstanceType(qualifier);
          }
        }
      }
    }

    if (context instanceof PsiReferenceExpression reference &&
        reference.resolve() instanceof PsiVariable variable &&
        isJavaLangClass(PsiTypesUtil.getPsiClass(variable.getType()))) {
      final PsiExpression definition = findVariableDefinition(reference, variable);
      if (definition != null) {
        ReflectiveType result = ourGuard.doPreventingRecursion(variable, false, () -> getReflectiveType(definition));
        if (result != null) {
          return result;
        }
      }
    }

    if (context.getType() instanceof PsiClassType type) {
      final PsiClassType.ClassResolveResult resolveResult = type.resolveGenerics();
      final PsiClass resolvedElement = resolveResult.getElement();
      if (!isJavaLangClass(resolvedElement)) return null;

      if (context instanceof PsiReferenceExpression ref && TYPE.equals(ref.getReferenceName()) && 
          ref.resolve() instanceof PsiField field &&
          field.hasModifierProperty(PsiModifier.FINAL) && field.hasModifierProperty(PsiModifier.STATIC)) {
        final PsiType[] classTypeArguments = type.getParameters();
        final PsiPrimitiveType unboxedType = classTypeArguments.length == 1
                                             ? PsiPrimitiveType.getUnboxedType(classTypeArguments[0]) : null;
        if (unboxedType != null && field.getContainingClass() == PsiUtil.resolveClassInClassTypeOnly(classTypeArguments[0])) {
          return ReflectiveType.create(unboxedType, true);
        }
      }
      final PsiTypeParameter[] parameters = resolvedElement.getTypeParameters();
      if (parameters.length == 1) {
        final PsiType typeArgument = resolveResult.getSubstitutor().substitute(parameters[0]);
        final PsiType erasure = TypeConversionUtil.erasure(typeArgument);
        final PsiClass argumentClass = PsiTypesUtil.getPsiClass(erasure);
        if (argumentClass != null && !isJavaLangObject(argumentClass)) {
          return ReflectiveType.create(argumentClass, false);
        }
      }
    }
    return null;
  }

  private static @Nullable ReflectiveType getClassInstanceType(@Nullable PsiExpression expression) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (expression == null) {
      return null;
    }
    if (expression instanceof PsiMethodCallExpression methodCall) {
      final String methodReferenceName = methodCall.getMethodExpression().getReferenceName();

      if (NEW_INSTANCE.equals(methodReferenceName)) {
        final PsiMethod method = methodCall.resolveMethod();
        if (method != null) {
          final PsiExpression[] arguments = methodCall.getArgumentList().getExpressions();
          if (arguments.length == 0 && isClassWithName(method.getContainingClass(), CommonClassNames.JAVA_LANG_CLASS)) {
            final PsiExpression qualifier = methodCall.getMethodExpression().getQualifierExpression();
            if (qualifier != null) {
              return ourGuard.doPreventingRecursion(qualifier, false, () -> getReflectiveType(qualifier));
            }
          }
          else if (arguments.length > 1 && isClassWithName(method.getContainingClass(), CommonClassNames.JAVA_LANG_REFLECT_ARRAY)) {
            final PsiExpression typeExpression = arguments[0];
            if (typeExpression != null) {
              final ReflectiveType itemType =
                ourGuard.doPreventingRecursion(typeExpression, false, () -> getReflectiveType(typeExpression));
              return ReflectiveType.arrayOf(itemType);
            }
          }
        }
      }
    }
    return ReflectiveType.create(expression.getType(), false);
  }

  @Contract("null,_->null")
  public static @Nullable <T> T computeConstantExpression(@Nullable PsiExpression expression, @NotNull Class<T> expectedType) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    final Object computed = JavaConstantExpressionEvaluator.computeConstantExpression(expression, false);
    return ObjectUtils.tryCast(computed, expectedType);
  }

  public static @Nullable ReflectiveClass getReflectiveClass(PsiExpression context) {
    final ReflectiveType reflectiveType = getReflectiveType(context);
    return reflectiveType != null ? reflectiveType.getReflectiveClass() : null;
  }

  public static @Nullable PsiExpression findDefinition(@Nullable PsiExpression expression) {
    int preventEndlessLoop = 5;
    while (expression instanceof PsiReferenceExpression) {
      if (--preventEndlessLoop == 0) return null;
      expression = findVariableDefinition((PsiReferenceExpression)expression);
    }
    return expression;
  }

  private static @Nullable PsiExpression findVariableDefinition(@NotNull PsiReferenceExpression referenceExpression) {
    final PsiElement resolved = referenceExpression.resolve();
    return resolved instanceof PsiVariable ? findVariableDefinition(referenceExpression, (PsiVariable)resolved) : null;
  }

  private static @Nullable PsiExpression findVariableDefinition(@NotNull PsiReferenceExpression referenceExpression, @NotNull PsiVariable variable) {
    if (variable.hasModifierProperty(PsiModifier.FINAL)) {
      final PsiExpression initializer = variable.getInitializer();
      if (initializer != null) {
        return initializer;
      }
      if (variable instanceof PsiField) {
        return findFinalFieldDefinition(referenceExpression, (PsiField)variable);
      }
    }
    return DeclarationSearchUtils.findDefinition(referenceExpression, variable);
  }

  private static @Nullable PsiExpression findFinalFieldDefinition(@NotNull PsiReferenceExpression referenceExpression, @NotNull PsiField field) {
    if (!field.hasModifierProperty(PsiModifier.FINAL)) return null;
    final PsiClass psiClass = ObjectUtils.tryCast(field.getParent(), PsiClass.class);
    if (psiClass != null) {
      final boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
      final List<PsiClassInitializer> initializers =
        ContainerUtil.filter(psiClass.getInitializers(), initializer -> initializer.hasModifierProperty(PsiModifier.STATIC) == isStatic);
      for (PsiClassInitializer initializer : initializers) {
        final PsiExpression assignedExpression = getAssignedExpression(initializer, field);
        if (assignedExpression != null) {
          return assignedExpression;
        }
      }
      if (!isStatic) {
        final PsiMethod[] constructors = psiClass.getConstructors();
        if (constructors.length == 1) {
          return getAssignedExpression(constructors[0], field);
        }
        for (PsiMethod constructor : constructors) {
          if (PsiTreeUtil.isAncestor(constructor, referenceExpression, true)) {
            return getAssignedExpression(constructor, field);
          }
        }
      }
    }
    return null;
  }

  private static @Nullable PsiExpression getAssignedExpression(@NotNull PsiMember maybeContainsAssignment, @NotNull PsiField field) {
    final PsiAssignmentExpression assignment = SyntaxTraverser.psiTraverser(maybeContainsAssignment)
      .filter(PsiAssignmentExpression.class)
      .find(expression -> ExpressionUtils.isReferenceTo(expression.getLExpression(), field));
    return assignment != null ? assignment.getRExpression() : null;
  }

  private static PsiClass findClass(@NotNull String qualifiedName, @NotNull PsiElement context) {
    final Project project = context.getProject();
    return JavaPsiFacade.getInstance(project).findClass(qualifiedName, GlobalSearchScope.allScope(project));
  }

  @Contract("null -> false")
  static boolean isJavaLangClass(@Nullable PsiClass aClass) {
    return isClassWithName(aClass, CommonClassNames.JAVA_LANG_CLASS);
  }

  @Contract("null -> false")
  static boolean isJavaLangObject(@Nullable PsiClass aClass) {
    return isClassWithName(aClass, CommonClassNames.JAVA_LANG_OBJECT);
  }

  @Contract("null, _ -> false")
  public static boolean isClassWithName(@Nullable PsiClass aClass, @NotNull String name) {
    return aClass != null && name.equals(aClass.getQualifiedName());
  }

  @Contract("null -> false")
  static boolean isRegularMethod(@Nullable PsiMethod method) {
    return method != null && !method.isConstructor();
  }

  static boolean isPublic(@NotNull PsiMember member) {
    return member.hasModifierProperty(PsiModifier.PUBLIC);
  }

  static boolean isAtomicallyUpdateable(@NotNull PsiField field) {
    if (field.hasModifierProperty(PsiModifier.STATIC) || !field.hasModifierProperty(PsiModifier.VOLATILE)) {
      return false;
    }
    final PsiType type = field.getType();
    return !(type instanceof PsiPrimitiveType) || PsiTypes.intType().equals(type) || PsiTypes.longType().equals(type);
  }

  static @Nullable String getParameterTypesText(@NotNull PsiMethod method) {
    final StringJoiner joiner = new StringJoiner(", ");
    for (PsiParameter parameter : method.getParameterList().getParameters()) {
      final String typeText = getTypeText(parameter.getType());
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

  static @NotNull LookupElement withPriority(@NotNull LookupElement lookupElement, boolean hasPriority) {
    return hasPriority ? lookupElement : PrioritizedLookupElement.withPriority(lookupElement, -1);
  }

  static @Nullable LookupElement withPriority(@Nullable LookupElement lookupElement, int priority) {
    return priority == 0 || lookupElement == null ? lookupElement : PrioritizedLookupElement.withPriority(lookupElement, priority);
  }

  static int getMethodSortOrder(@NotNull PsiMethod method) {
    return isJavaLangObject(method.getContainingClass()) ? 1 : isPublic(method) ? -1 : 0;
  }

  static @Nullable String getMemberType(@Nullable PsiElement element) {
    final PsiMethodCallExpression methodCall = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
    return methodCall != null ? methodCall.getMethodExpression().getReferenceName() : null;
  }

  static @Nullable LookupElement lookupMethod(@NotNull PsiMethod method, @Nullable InsertHandler<LookupElement> insertHandler) {
    final ReflectiveSignature signature = getMethodSignature(method);
    return signature != null
           ? LookupElementBuilder.create(signature, method.getName())
             .withIcon(signature.getIcon())
             .withTailText(signature.getShortArgumentTypes())
             .withInsertHandler(insertHandler)
           : null;
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

  public static @NotNull String getTypeText(@NotNull PsiType type) {
    final ReflectiveType reflectiveType = ReflectiveType.create(type, false);
    return reflectiveType.getQualifiedName();
  }

  public static @Nullable String getTypeText(@Nullable PsiExpression argument) {
    final ReflectiveType reflectiveType = getReflectiveType(argument);
    return reflectiveType != null ? reflectiveType.getQualifiedName() : null;
  }

  @Contract("null -> null")
  public static @Nullable ReflectiveSignature getMethodSignature(@Nullable PsiMethod method) {
    if (method != null) {
      final List<String> types = new ArrayList<>();
      final PsiType returnType = method.getReturnType();
      types.add(getTypeText(returnType != null ? returnType : PsiTypes.voidType())); // null return type means it's a constructor

      for (PsiParameter parameter : method.getParameterList().getParameters()) {
        types.add(getTypeText(parameter.getType()));
      }
      final Icon icon = method.getIcon(Iconable.ICON_FLAG_VISIBILITY);
      return ReflectiveSignature.create(icon, types);
    }
    return null;
  }

  public static @NotNull String getMethodTypeExpressionText(@NotNull ReflectiveSignature signature) {
    final String types = signature.getText(true, type -> type + ".class");
    return JAVA_LANG_INVOKE_METHOD_TYPE + "." + METHOD_TYPE + types;
  }

  public static boolean isCallToMethod(@NotNull PsiMethodCallExpression methodCall, @NotNull String className, @NotNull String methodName) {
    return MethodCallUtils.isCallToMethod(methodCall, className, null, methodName, (PsiType[])null);
  }

  /**
   * Tries to unwrap array and find its components
   * @param maybeArray an array to unwrap
   * @return list of unwrapped array components, some or all of them could be null if unknown (but the length is known);
   * returns null if nothing is known.
   */
  public static @Nullable List<PsiExpression> getVarargs(@Nullable PsiExpression maybeArray) {
    if (ExpressionUtils.isNullLiteral(maybeArray)) {
      return Collections.emptyList();
    }
    if (isVarargAsArray(maybeArray)) {
      final PsiExpression argumentsDefinition = findDefinition(maybeArray);
      if (argumentsDefinition instanceof PsiArrayInitializerExpression) {
        return Arrays.asList(((PsiArrayInitializerExpression)argumentsDefinition).getInitializers());
      }
      if (argumentsDefinition instanceof PsiNewExpression) {
        final PsiArrayInitializerExpression arrayInitializer = ((PsiNewExpression)argumentsDefinition).getArrayInitializer();
        if (arrayInitializer != null) {
          return Arrays.asList(arrayInitializer.getInitializers());
        }
        final PsiExpression[] dimensions = ((PsiNewExpression)argumentsDefinition).getArrayDimensions();
        if (dimensions.length == 1) { // new Object[length] or new Class<?>[length]
          final Integer itemCount = computeConstantExpression(findDefinition(dimensions[0]), Integer.class);
          if (itemCount != null && itemCount >= 0 && itemCount < 256) {
            return Collections.nCopies(itemCount, null);
          }
        }
      }
    }
    return null;
  }

  public static @Nullable List<PsiExpression> getListComponents(@Nullable PsiExpression maybeList) {
    maybeList = PsiUtil.skipParenthesizedExprDown(maybeList);
    if (LIST_FACTORY.matches(maybeList) && maybeList instanceof PsiMethodCallExpression callExpression) {
      final PsiExpression[] expressions = callExpression.getArgumentList().getExpressions();
      if (expressions.length == 0) {
        return Collections.emptyList();
      }
      final PsiExpression firstArgument = PsiUtil.skipParenthesizedExprDown(expressions[0]);
      if (MethodCallUtils.isVarArgCall(callExpression)) {
        final List<PsiExpression> varargs = getVarargs(firstArgument);
        if (varargs != null) {
          return varargs;
        }
      }
      // Skip calls with explicit arrays, for example: List.of(new Class<?>[0])
      if (isVarargAsArray(firstArgument)) {
        return null;
      }
      return Arrays.asList(expressions);
    }
    return null;
  }

  @Contract("null -> false")
  public static boolean isVarargAsArray(@Nullable PsiExpression maybeArray) {
    final PsiType type = maybeArray != null ? maybeArray.getType() : null;
    return type instanceof PsiArrayType &&
           type.getArrayDimensions() == 1 &&
           type.getDeepComponentType() instanceof PsiClassType;
  }

  /**
   * Take method's return type and parameter types
   * from arguments of MethodType.methodType(Class...) and MethodType.genericMethodType(int, boolean?)
   */
  public static @Nullable ReflectiveSignature composeMethodSignature(@Nullable PsiExpression methodTypeExpression) {
    return composeMethodSignature(methodTypeExpression, true);
  }

  private static @Nullable ReflectiveSignature composeMethodSignature(@Nullable PsiExpression methodTypeExpression, boolean allowRecursion) {
    final PsiExpression typeDefinition = findDefinition(PsiUtil.skipParenthesizedExprDown(methodTypeExpression));
    if (METHOD_TYPE_WITH_METHOD_TYPE_MATCHER.matches(typeDefinition) && !allowRecursion) {
      return null;
    }
    if (typeDefinition instanceof PsiMethodCallExpression) {
      return SIGNATURE_MAPPER.mapFirst((PsiMethodCallExpression)typeDefinition);
    }
    return null;
  }

  private static @Nullable ReflectiveSignature composeMethodSignatureFromReturnTypeAndMethodType(
    PsiExpression @NotNull [] arguments
  ) {
    if (arguments.length == 2) {
      final PsiExpression methodType = findInnermostMethodType(arguments[1]);
      if (methodType != null) {
        final ReflectiveSignature signature = composeMethodSignature(methodType, false);
        if (signature != null) {
          final String text = getTypeText(arguments[0]);
          if (text != null) {
            return signature.withReturnType(text);
          }
        }
      }
    }
    return null;
  }

  /**
   * Find innermost {@link MethodType} for a {@link MethodType#methodType(Class, MethodType)} call
   *
   * <p>
   * Examples:
   * <ol>
   *   <li>
   *     For {@code MethodType.methodType(void.class, MethodType.methodType(String.class)}
   *     will return {@link PsiExpression} for {@code MethodType.methodType(String.class)}
   *   </li>
   *   <li>
   *     For {@code MethodType.methodType(void.class, MethodType.methodType(String.class, MethodType.methodType(List.class))}
   *     will return {@link PsiExpression} for {@code MethodType.methodType(List.class)}
   *   </li>
   * </ol>
   *
   * @param methodType the origin {@link MethodType}
   * @return innermost {@link MethodType} as {@link PsiExpression} or {@code null}, if unable to resolve or there are too many nested calls
   */
  public static @Nullable PsiExpression findInnermostMethodType(@Nullable PsiExpression methodType) {
    methodType = findDefinition(methodType);
    int preventEndlessLoop = 5;
    while (METHOD_TYPE_WITH_METHOD_TYPE_MATCHER.matches(methodType)) {
      if (--preventEndlessLoop == 0) {
        return null;
      }
      methodType = PsiUtil.skipParenthesizedExprDown(methodType);
      if (!(methodType instanceof PsiMethodCallExpression call)) {
        return null;
      }
      final PsiExpression[] expressions = call.getArgumentList().getExpressions();
      if (expressions.length != 2) {
        return null;
      }
      methodType = findDefinition(expressions[1]);
    }

    return METHOD_TYPE_MATCHER.matches(methodType) ? methodType : null;
  }

  private static @Nullable ReflectiveSignature composeMethodSignatureFromReturnTypeAndList(PsiExpression @NotNull [] arguments) {
    if (arguments.length == 2) {
      final PsiExpression returnType = findDefinition(arguments[0]);
      if (returnType != null) {
        final PsiExpression list = arguments[1];
        final List<PsiExpression> components = getListComponents(list);
        if (components != null) {
          final List<PsiExpression> signature = ContainerUtil.prepend(components, returnType);
          return ReflectiveSignature.create(ContainerUtil.map(signature, typeExpression -> getTypeText(typeExpression)));
        }
      }
    }
    return null;
  }

  private static @Nullable ReflectiveSignature composeMethodSignatureFromReturnTypeAndArray(PsiExpression @NotNull [] arguments) {
    if (arguments.length == 2) {
      final PsiExpression returnType = findDefinition(arguments[0]);
      if (returnType != null) {
        final PsiExpression array = arguments[1];
        final List<PsiExpression> components = getVarargs(array);
        if (components != null) {
          final List<PsiExpression> signature = ContainerUtil.prepend(components, returnType);
          return ReflectiveSignature.create(ContainerUtil.map(signature, typeExpression -> getTypeText(typeExpression)));
        }
      }
    }
    return null;
  }

  private static @Nullable ReflectiveSignature composeMethodSignatureFromTypes(PsiExpression @NotNull [] returnAndParameterTypes) {
    final List<String> typeTexts = ContainerUtil.map(returnAndParameterTypes, JavaReflectionReferenceUtil::getTypeText);
    return ReflectiveSignature.create(typeTexts);
  }

  public static @Nullable Pair.NonNull<Integer, Boolean> getGenericSignature(PsiExpression @NotNull [] genericSignatureShape) {
    if (genericSignatureShape.length == 0 || genericSignatureShape.length > 2) {
      return null;
    }

    final Integer objectArgCount = computeConstantExpression(genericSignatureShape[0], Integer.class);
    final Boolean finalArray = // there's an additional parameter which is an ellipsis or an array
      genericSignatureShape.length > 1 ? computeConstantExpression(genericSignatureShape[1], Boolean.class) : false;

    if (objectArgCount == null || objectArgCount < 0 || objectArgCount > 255) {
      return null;
    }
    if (finalArray == null || finalArray && objectArgCount > 254) {
      return null;
    }
    return Pair.createNonNull(objectArgCount, finalArray);
  }

  /**
   * All the types in the method signature are either unbounded type parameters or java.lang.Object (with possible vararg)
   */
  private static @Nullable ReflectiveSignature composeGenericMethodSignature(PsiExpression @NotNull [] genericSignatureShape) {
    final Pair.NonNull<Integer, Boolean> signature = getGenericSignature(genericSignatureShape);
    if (signature == null) return null;
    final int objectArgCount = signature.getFirst();
    final boolean finalArray = signature.getSecond();

    final List<String> typeNames = new ArrayList<>();
    typeNames.add(CommonClassNames.JAVA_LANG_OBJECT); // return type

    for (int i = 0; i < objectArgCount; i++) {
      typeNames.add(CommonClassNames.JAVA_LANG_OBJECT);
    }
    if (finalArray) {
      typeNames.add(CommonClassNames.JAVA_LANG_OBJECT + "[]");
    }
    return ReflectiveSignature.create(typeNames);
  }


  public static final class ReflectiveType {
    final PsiType myType;
    final boolean myIsExact;

    private ReflectiveType(@NotNull PsiType erasedType, boolean isExact) {
      myType = erasedType;
      myIsExact = isExact;
    }

    public @NotNull String getQualifiedName() {
      return myType.getCanonicalText();
    }

    @Override
    public String toString() {
      return myType.getCanonicalText();
    }

    public boolean isEqualTo(@Nullable PsiType otherType) {
      return otherType != null && myType.equals(erasure(otherType));
    }

    public boolean isAssignableFrom(@NotNull PsiType type) {
      return myType.isAssignableFrom(type);
    }

    public boolean isPrimitive() {
      return myType instanceof PsiPrimitiveType;
    }

    public @NotNull PsiType getType() {
      return myType;
    }

    public boolean isExact() {
      return myIsExact;
    }

    public @Nullable ReflectiveClass getReflectiveClass() {
      PsiClass psiClass = getPsiClass();
      if (psiClass != null) {
        return new ReflectiveClass(psiClass, myIsExact);
      }
      return null;
    }

    public @Nullable ReflectiveType getArrayComponentType() {
      if (myType instanceof PsiArrayType) {
        PsiType componentType = ((PsiArrayType)myType).getComponentType();
        return new ReflectiveType(componentType, myIsExact);
      }
      return null;
    }

    public @Nullable PsiClass getPsiClass() {
      return PsiTypesUtil.getPsiClass(myType);
    }

    @Contract("!null,_ -> !null; null,_ -> null")
    public static @Nullable ReflectiveType create(@Nullable PsiType originalType, boolean isExact) {
      if (originalType != null) {
        return new ReflectiveType(erasure(originalType), isExact);
      }
      return null;
    }

    @Contract("!null,_ -> !null; null,_ -> null")
    public static @Nullable ReflectiveType create(@Nullable PsiClass psiClass, boolean isExact) {
      if (psiClass != null) {
        final PsiElementFactory factory = JavaPsiFacade.getElementFactory(psiClass.getProject());
        return new ReflectiveType(factory.createType(psiClass), isExact);
      }
      return null;
    }

    @Contract("!null -> !null; null -> null")
    public static @Nullable ReflectiveType arrayOf(@Nullable ReflectiveType itemType) {
      if (itemType != null) {
        return new ReflectiveType(itemType.myType.createArrayType(), itemType.myIsExact);
      }
      return null;
    }

    private static @NotNull PsiType erasure(@NotNull PsiType type) {
      final PsiType erasure = TypeConversionUtil.erasure(type);
      if (erasure instanceof PsiEllipsisType) {
        return ((PsiEllipsisType)erasure).toArrayType();
      }
      return erasure;
    }
  }

  public static class ReflectiveClass {
    final PsiClass myPsiClass;
    final boolean myIsExact;

    public ReflectiveClass(@NotNull PsiClass psiClass, boolean isExact) {
      myPsiClass = psiClass;
      myIsExact = isExact;
    }

    public @NotNull PsiClass getPsiClass() {
      return myPsiClass;
    }

    public boolean isExact() {
      return myIsExact || myPsiClass.hasModifierProperty(PsiModifier.FINAL);
    }
  }

  public static final class ReflectiveSignature implements Comparable<ReflectiveSignature> {
    public static final ReflectiveSignature NO_ARGUMENT_CONSTRUCTOR_SIGNATURE =
      new ReflectiveSignature(null, JavaKeywords.VOID, ArrayUtilRt.EMPTY_STRING_ARRAY);

    private final Icon myIcon;
    private final @NotNull String myReturnType;
    private final String @NotNull [] myArgumentTypes;

    public static @Nullable ReflectiveSignature create(@NotNull List<String> typeTexts) {
      return create(null, typeTexts);
    }

    public static @Nullable ReflectiveSignature create(@Nullable Icon icon, @NotNull List<String> typeTexts) {
      if (!typeTexts.isEmpty() && !typeTexts.contains(null)) {
        final String[] argumentTypes = ArrayUtilRt.toStringArray(typeTexts.subList(1, typeTexts.size()));
        return new ReflectiveSignature(icon, typeTexts.get(0), argumentTypes);
      }
      return null;
    }

    private ReflectiveSignature(@Nullable Icon icon, @NotNull String returnType, String @NotNull [] argumentTypes) {
      myIcon = icon;
      myReturnType = returnType;
      myArgumentTypes = argumentTypes;
    }

    public String getText(boolean withReturnType, @NotNull Function<? super String, String> transformation) {
      return getText(withReturnType, true, transformation);
    }

    public String getText(boolean withReturnType, boolean withParentheses, @NotNull Function<? super String, String> transformation) {
      final StringJoiner joiner = new StringJoiner(", ", withParentheses ? "(" : "", withParentheses ? ")" : "");
      if (withReturnType) {
        joiner.add(transformation.apply(myReturnType));
      }
      for (String argumentType : myArgumentTypes) {
        joiner.add(transformation.apply(argumentType));
      }
      return joiner.toString();
    }

    public @NotNull String getShortReturnType() {
      return PsiNameHelper.getShortClassName(myReturnType);
    }

    public @NotNull String getShortArgumentTypes() {
      return getText(false, PsiNameHelper::getShortClassName);
    }

    public @NotNull Icon getIcon() {
      return myIcon != null ? myIcon : IconManager.getInstance().getPlatformIcon(PlatformIcons.Method);
    }

    public ReflectiveSignature withReturnType(@NotNull String returnType) {
      return new ReflectiveSignature(this.myIcon, returnType, this.myArgumentTypes);
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
      return o instanceof ReflectiveSignature other && 
             Objects.equals(myReturnType, other.myReturnType) &&
             Arrays.equals(myArgumentTypes, other.myArgumentTypes);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myReturnType, Arrays.hashCode(myArgumentTypes));
    }

    @Override
    public String toString() {
      return myReturnType + " " + Arrays.toString(myArgumentTypes);
    }
  }
}
