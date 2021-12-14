// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.codeInsight.completion.CompletionMemory;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.JavaMethodCallElement;
import com.intellij.codeInsight.daemon.impl.analysis.LambdaHighlightingUtil;
import com.intellij.codeInsight.hints.ParameterHintsPass;
import com.intellij.codeInsight.lookup.CommaTailType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NullableComputable;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.source.resolve.CompletionParameterTypeInferencePolicy;
import com.intellij.psi.impl.source.resolve.DefaultParameterTypeInferencePolicy;
import com.intellij.psi.impl.source.resolve.ParameterTypeInferencePolicy;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.JavaPsiConstructorUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import com.siyeh.ig.testFrameworks.AssertHint;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Supplier;

/**
 * @author ven
 */
public final class ExpectedTypesProvider {
  private static final ExpectedTypeInfo VOID_EXPECTED = createInfoImpl(PsiType.VOID, ExpectedTypeInfo.TYPE_OR_SUBTYPE, PsiType.VOID, TailType.SEMICOLON);

  private static final Logger LOG = Logger.getInstance(ExpectedTypesProvider.class);

  public static ExpectedTypesProvider getInstance(@NotNull Project project) {
    return project.getService(ExpectedTypesProvider.class);
  }

  private static final int MAX_COUNT = 50;
  private static final ExpectedClassProvider ourGlobalScopeClassProvider = new ExpectedClassProvider() {
    @Override
    public PsiField @NotNull [] findDeclaredFields(@NotNull final PsiManager manager, @NotNull String name) {
      final PsiShortNamesCache cache = PsiShortNamesCache.getInstance(manager.getProject());
      GlobalSearchScope scope = GlobalSearchScope.allScope(manager.getProject());
      return cache.getFieldsByName(name, scope);
    }

    @Override
    public PsiMethod @NotNull [] findDeclaredMethods(@NotNull final PsiManager manager, @NotNull String name) {
      Project project = manager.getProject();
      final PsiShortNamesCache cache = PsiShortNamesCache.getInstance(project);
      GlobalSearchScope sources = GlobalSearchScope.projectScope(project);
      GlobalSearchScope libraries = GlobalSearchScope.notScope(sources);
      PsiMethod[] sourceMethods = cache.getMethodsByNameIfNotMoreThan(name, sources, MAX_COUNT);
      if (sourceMethods.length >= MAX_COUNT) return sourceMethods;
      PsiMethod[] libraryMethods = cache.getMethodsByNameIfNotMoreThan(name, libraries, MAX_COUNT-sourceMethods.length);
      return ArrayUtil.mergeArrays(sourceMethods, libraryMethods);
    }
  };
  private static final PsiType[] PRIMITIVE_TYPES = {PsiType.BYTE, PsiType.CHAR, PsiType.SHORT, PsiType.INT, PsiType.LONG, PsiType.FLOAT, PsiType.DOUBLE};

  @NotNull
  public static ExpectedTypeInfo createInfo(@NotNull  PsiType type, @ExpectedTypeInfo.Type int kind, PsiType defaultType, @NotNull TailType tailType) {
    return createInfoImpl(type, kind, defaultType, tailType);
  }

  @NotNull
  private static ExpectedTypeInfoImpl createInfoImpl(@NotNull PsiType type, PsiType defaultType) {
    return createInfoImpl(type, ExpectedTypeInfo.TYPE_OR_SUBTYPE, defaultType, TailType.NONE);
  }

  @NotNull
  private static ExpectedTypeInfoImpl createInfoImpl(@NotNull PsiType type, @ExpectedTypeInfo.Type int kind, PsiType defaultType, @NotNull TailType tailType) {
    return new ExpectedTypeInfoImpl(type, kind, defaultType, tailType, null, ExpectedTypeInfoImpl.NULL);
  }

  @NotNull
  private static ExpectedTypeInfoImpl createInfoImpl(@NotNull PsiType type,
                                                     int kind,
                                                     PsiType defaultType,
                                                     @NotNull TailType tailType,
                                                     PsiMethod calledMethod,
                                                     Supplier<String> expectedName) {
    return new ExpectedTypeInfoImpl(type, kind, defaultType, tailType, calledMethod, expectedName);
  }

  @Nullable
  public static ExpectedTypeInfo getSingleExpectedTypeForCompletion(@Nullable PsiExpression expr) {
    ExpectedTypeInfo[] expectedTypes = getExpectedTypes(expr, true, ourGlobalScopeClassProvider, false, false, 1);
    if (expectedTypes.length > 0) {
      return expectedTypes[0];
    }
    return null;
  }

  public static ExpectedTypeInfo @NotNull [] getExpectedTypes(@Nullable PsiExpression expr, boolean forCompletion) {
    return getExpectedTypes(expr, forCompletion, false, false);
  }

  public static ExpectedTypeInfo @NotNull [] getExpectedTypes(@Nullable PsiExpression expr, boolean forCompletion, final boolean voidable, boolean usedAfter) {
    return getExpectedTypes(expr, forCompletion, ourGlobalScopeClassProvider, voidable, usedAfter);
  }

  public static ExpectedTypeInfo @NotNull [] getExpectedTypes(@Nullable PsiExpression expr,
                                                              boolean forCompletion,
                                                              ExpectedClassProvider classProvider, boolean usedAfter) {
    return getExpectedTypes(expr, forCompletion, classProvider, false, usedAfter);
  }

  public static ExpectedTypeInfo @NotNull [] getExpectedTypes(@Nullable PsiExpression expr, boolean forCompletion, ExpectedClassProvider classProvider,
                                                              final boolean voidable, boolean usedAfter) {
    return getExpectedTypes(expr, forCompletion, classProvider, voidable, usedAfter, Integer.MAX_VALUE);
  }

  private static ExpectedTypeInfo @NotNull [] getExpectedTypes(@Nullable PsiExpression expr,
                                                               boolean forCompletion,
                                                               ExpectedClassProvider classProvider,
                                                               boolean voidable,
                                                               boolean usedAfter, int maxCandidates) {
    if (expr == null) return ExpectedTypeInfo.EMPTY_ARRAY;
    PsiElement parent = expr.getParent();
    PsiFunctionalExpression functionalExpression = extractFunctionalExpression(expr);
    if (functionalExpression != null) {
      final Collection<? extends PsiType> types = FunctionalInterfaceSuggester.suggestFunctionalInterfaces(functionalExpression);
      if (types.isEmpty()) {
        return ExpectedTypeInfo.EMPTY_ARRAY;
      }
      else {
        final ExpectedTypeInfo[] result = new ExpectedTypeInfo[types.size()];
        int i = 0;
        for (PsiType type : types) {
          result[i++] = new ExpectedTypeInfoImpl(type, ExpectedTypeInfo.TYPE_SAME_SHAPED, type, TailType.NONE, null, ExpectedTypeInfoImpl.NULL);
        }
        return result;
      }
    }
    MyParentVisitor visitor = new MyParentVisitor(expr, forCompletion, classProvider, voidable, usedAfter, maxCandidates);
    if (parent != null) {
      parent.accept(visitor);
    }
    ExpectedTypeInfo[] result = visitor.getResult();
    if (forCompletion) {
      return ContainerUtil.map2Array(result, ExpectedTypeInfo.class, i -> ((ExpectedTypeInfoImpl)i).fixUnresolvedTypes(expr));
    }
    return result;
  }

  private static PsiFunctionalExpression extractFunctionalExpression(PsiExpression expr) {
    PsiElement parent = expr.getParent();
    if (expr instanceof PsiFunctionalExpression && parent instanceof PsiExpressionStatement && !(parent.getParent() instanceof PsiSwitchLabeledRuleStatement)) {
      return (PsiFunctionalExpression)expr;
    }
    parent = PsiTreeUtil.skipParentsOfType(expr, PsiParenthesizedExpression.class);
    if (parent instanceof PsiAssignmentExpression &&
        parent.getParent() instanceof PsiExpressionStatement &&
        PsiTreeUtil.isAncestor(((PsiAssignmentExpression)parent).getLExpression(), expr, false)) {
      return ObjectUtils.tryCast(((PsiAssignmentExpression)parent).getRExpression(), PsiFunctionalExpression.class);
    }
    return null;
  }

  public static PsiType @NotNull [] processExpectedTypes(ExpectedTypeInfo @NotNull [] infos,
                                                         @NotNull PsiTypeVisitor<? extends PsiType> visitor, @NotNull Project project) {
    LinkedHashSet<PsiType> set = new LinkedHashSet<>();
    for (ExpectedTypeInfo info : infos) {
      ExpectedTypeInfoImpl infoImpl = (ExpectedTypeInfoImpl)info;

      if (infoImpl.getDefaultType() instanceof PsiClassType) {
        JavaResolveResult result = ((PsiClassType)infoImpl.getDefaultType()).resolveGenerics();
        PsiClass aClass = (PsiClass)result.getElement();
        if (aClass instanceof PsiAnonymousClass) {
          processType(((PsiAnonymousClass)aClass).getBaseClassType(), visitor, set);
          ((PsiAnonymousClass)aClass).getBaseClassType().accept(visitor);
        }
        else {
          processType(infoImpl.getDefaultType(), visitor, set);
        }
      }
      else {
        processType(infoImpl.getDefaultType(), visitor, set);
      }

      if (infoImpl.getKind() == ExpectedTypeInfo.TYPE_OR_SUPERTYPE) {
        processAllSuperTypes(infoImpl.getType(), visitor, project, set, new HashSet<>());
      }
      else if (infoImpl.getKind() == ExpectedTypeInfo.TYPE_OR_SUBTYPE) {
        if (infoImpl.getType() instanceof PsiPrimitiveType) {
          processPrimitiveTypeAndSubtypes((PsiPrimitiveType)infoImpl.getType(), visitor, set);
        }
        //else too expensive to search
      }
    }

    return set.toArray(PsiType.createArray(set.size()));
  }

  private static void processType(@NotNull PsiType type, @NotNull PsiTypeVisitor<? extends PsiType> visitor, @NotNull Set<? super PsiType> typeSet) {
    PsiType accepted = type.accept(visitor);
    if (accepted != null) typeSet.add(accepted);
  }

  private static void processPrimitiveTypeAndSubtypes(@NotNull PsiPrimitiveType type,
                                                      @NotNull PsiTypeVisitor<? extends PsiType> visitor,
                                                      @NotNull Set<? super PsiType> set) {
    if (type.equals(PsiType.BOOLEAN) || type.equals(PsiType.VOID) || type.equals(PsiType.NULL)) return;

    for (int i = 0; ; i++) {
      final PsiType primitive = PRIMITIVE_TYPES[i];
      processType(primitive, visitor, set);
      if (primitive.equals(type)) return;
    }
  }

  public static void processAllSuperTypes(@NotNull PsiType type, @NotNull PsiTypeVisitor<? extends PsiType> visitor, @NotNull Project project, @NotNull Set<? super PsiType> set, @NotNull Set<? super PsiType> visited) {
    if (!visited.add(type)) return;

    if (type instanceof PsiPrimitiveType) {
      if (type.equals(PsiType.BOOLEAN) || type.equals(PsiType.VOID) || type.equals(PsiType.NULL)) return;

      Stack<PsiType> stack = new Stack<>();
      for (int i = PRIMITIVE_TYPES.length - 1; !PRIMITIVE_TYPES[i].equals(type); i--) {
        stack.push(PRIMITIVE_TYPES[i]);
      }
      while(!stack.empty()) {
        processType(stack.pop(), visitor, set);
      }
    }
    else{
      PsiManager manager = PsiManager.getInstance(project);
      GlobalSearchScope resolveScope = type.getResolveScope();
      if (resolveScope == null) resolveScope = GlobalSearchScope.allScope(project);
      PsiClassType objectType = PsiType.getJavaLangObject(manager, resolveScope);
      processType(objectType, visitor, set);

      if (type instanceof PsiClassType) {
        for (PsiType superType : type.getSuperTypes()) {
          processType(superType, visitor, set);
          processAllSuperTypes(superType, visitor, project, set, visited);
        }
      }
    }
  }

  private static final class MyParentVisitor extends JavaElementVisitor {
    private static final int MAX_VAR_HOPS = 3;
    private PsiExpression myExpr;
    private int myHops = 0;
    private final boolean myForCompletion;
    private final boolean myUsedAfter;
    private final int myMaxCandidates;
    private final ExpectedClassProvider myClassProvider;
    private final boolean myVoidable;
    final List<ExpectedTypeInfo> myResult = new ArrayList<>();
    @NonNls private static final String LENGTH_SYNTHETIC_ARRAY_FIELD = "length";

    private MyParentVisitor(PsiExpression expr,
                            boolean forCompletion,
                            ExpectedClassProvider classProvider,
                            boolean voidable,
                            boolean usedAfter,
                            int maxCandidates) {
      myExpr = expr;
      myForCompletion = forCompletion;
      myClassProvider = classProvider;
      myVoidable = voidable;
      myUsedAfter = usedAfter;
      myMaxCandidates = maxCandidates;
    }

    public ExpectedTypeInfo @NotNull [] getResult() {
      if (myResult.size() > myMaxCandidates) {
        return myResult.subList(0, myMaxCandidates).toArray(ExpectedTypeInfo.EMPTY_ARRAY);
      }
      return myResult.toArray(ExpectedTypeInfo.EMPTY_ARRAY);
    }

    @Override
    public void visitParenthesizedExpression(PsiParenthesizedExpression expression) {
      PsiElement parent = expression.getParent();
      if (parent != null) {
        final MyParentVisitor visitor = new MyParentVisitor(expression, myForCompletion, myClassProvider, myVoidable, myUsedAfter,
                                                            myMaxCandidates);
        parent.accept(visitor);
        for (final ExpectedTypeInfo info : visitor.myResult) {
          myResult.add(createInfoImpl(info.getType(), info.getKind(), info.getDefaultType(), TailTypes.RPARENTH, info.getCalledMethod(),
                                      ((ExpectedTypeInfoImpl)info)::getExpectedName));
        }
      }
    }

    @Override
    public void visitAnnotationMethod(@NotNull final PsiAnnotationMethod method) {
      if (myExpr == method.getDefaultValue()) {
        final PsiType type = method.getReturnType();
        if (type != null) {
          myResult.add(createInfoImpl(type, ExpectedTypeInfo.TYPE_OR_SUBTYPE, type, TailType.SEMICOLON));
        }
      }
    }

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      if (myForCompletion) {
        final MyParentVisitor visitor = new MyParentVisitor(expression, true, myClassProvider, myVoidable, myUsedAfter, myMaxCandidates);
        expression.getParent().accept(visitor);
        myResult.addAll(visitor.myResult);
        return;
      }

      String referenceName = expression.getReferenceName();
      if (referenceName != null) {
        final PsiElement parent = expression.getParent();
        if (parent instanceof PsiMethodCallExpression) {
          Collections.addAll(myResult, findClassesWithDeclaredMethod((PsiMethodCallExpression)parent));
        }
        else if (parent instanceof PsiVariable ||
                 parent instanceof PsiExpression) {
          if (LENGTH_SYNTHETIC_ARRAY_FIELD.equals(referenceName)) {
            myResult.addAll(anyArrayType());
          }
          else {
            Collections.addAll(myResult, findClassesWithDeclaredField(expression));
          }
        }
      }
    }

    @Override
    public void visitExpressionStatement(PsiExpressionStatement statement) {
      if (statement.getParent() instanceof PsiSwitchLabeledRuleStatement) {
        PsiSwitchBlock block = ((PsiSwitchLabeledRuleStatement)statement.getParent()).getEnclosingSwitchBlock();
        if (block instanceof PsiSwitchExpression) {
          Collections.addAll(myResult, getExpectedTypes((PsiExpression)block, myForCompletion));
          return;
        }
      }
      if (myVoidable) {
        myResult.add(VOID_EXPECTED);
      }
    }

    @Override
    public void visitYieldStatement(PsiYieldStatement statement) {
      PsiSwitchExpression expression = statement.findEnclosingExpression();
      if (expression != null) {
        Collections.addAll(myResult, getExpectedTypes(expression, myForCompletion));
      }
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      myExpr = (PsiExpression)myExpr.getParent();
      expression.getParent().accept(this);
    }

    @Override
    public void visitAnnotationArrayInitializer(@NotNull PsiArrayInitializerMemberValue initializer) {
      PsiElement parent = initializer.getParent();
      while (parent instanceof PsiArrayInitializerMemberValue) {
        parent = parent.getParent();
      }
      final PsiType type;
      if (parent instanceof PsiNameValuePair) {
        type = getAnnotationMethodType((PsiNameValuePair)parent);
      }
      else {
        type = ((PsiAnnotationMethod)parent).getReturnType();
      }
      if (type instanceof PsiArrayType) {
        final PsiType componentType = ((PsiArrayType)type).getComponentType();
        myResult.add(createInfoImpl(componentType, componentType));
      }
    }

    @Override
    public void visitNameValuePair(@NotNull PsiNameValuePair pair) {
      final PsiType type = getAnnotationMethodType(pair);
      if (type == null) return;
      if (type instanceof PsiArrayType) {
        PsiType componentType = ((PsiArrayType)type).getComponentType();
        myResult.add(createInfoImpl(componentType, componentType));
      }
      else {
        myResult.add(createInfoImpl(type, type));
      }
    }

    @Nullable
    private static PsiType getAnnotationMethodType(@NotNull final PsiNameValuePair pair) {
      final PsiReference reference = pair.getReference();
      if (reference != null) {
        final PsiElement method = reference.resolve();
        if (method instanceof PsiMethod) {
          return ((PsiMethod)method).getReturnType();
        }
      }
      return null;
    }

    @Override
    public void visitLambdaExpression(PsiLambdaExpression lambdaExpression) {
      super.visitLambdaExpression(lambdaExpression);
      final PsiType functionalInterfaceType = lambdaExpression.getFunctionalInterfaceType();
      final PsiMethod scopeMethod = LambdaUtil.getFunctionalInterfaceMethod(functionalInterfaceType);
      if (scopeMethod != null) {
        visitMethodReturnType(scopeMethod, LambdaUtil.getFunctionalInterfaceReturnType(functionalInterfaceType), LambdaHighlightingUtil
          .insertSemicolonAfter(lambdaExpression));
      }
    }

    @Override
    public void visitReturnStatement(PsiReturnStatement statement) {
      final PsiMethod method;
      final PsiType type;
      final boolean tailTypeSemicolon;
      final NavigatablePsiElement psiElement = PsiTreeUtil.getParentOfType(statement, PsiLambdaExpression.class, PsiMethod.class);
      if (psiElement instanceof PsiLambdaExpression) {
        final PsiType functionalInterfaceType = ((PsiLambdaExpression)psiElement).getFunctionalInterfaceType();
        method = LambdaUtil.getFunctionalInterfaceMethod(functionalInterfaceType);
        type = LambdaUtil.getFunctionalInterfaceReturnType(functionalInterfaceType);
        tailTypeSemicolon = LambdaHighlightingUtil.insertSemicolonAfter((PsiLambdaExpression)psiElement);
      }
      else if (psiElement instanceof PsiMethod) {
        method = (PsiMethod)psiElement;
        type = method.getReturnType();
        tailTypeSemicolon = true;
      } else {
        method = null;
        type = null;
        tailTypeSemicolon = true;
      }
      if (method != null) {
        visitMethodReturnType(method, type, tailTypeSemicolon);
      }

    }

    private void visitMethodReturnType(final PsiMethod scopeMethod, PsiType type, boolean tailTypeSemicolon) {
      if (type != null) {
        Supplier<String> expectedName;
        if (PropertyUtilBase.isSimplePropertyAccessor(scopeMethod)) {
          expectedName = () -> PropertyUtilBase.getPropertyName(scopeMethod);
        }
        else {
          expectedName = ExpectedTypeInfoImpl.NULL;
        }

        myResult.add(createInfoImpl(type, ExpectedTypeInfo.TYPE_OR_SUBTYPE, type,
                                                   tailTypeSemicolon ? TailType.SEMICOLON : TailType.NONE, null, expectedName));
      }
    }

    @Override
    public void visitIfStatement(PsiIfStatement statement) {
      myResult.add(createInfoImpl(PsiType.BOOLEAN, ExpectedTypeInfo.TYPE_STRICTLY, PsiType.BOOLEAN, TailTypes.IF_RPARENTH));
    }

    @Override
    public void visitWhileStatement(PsiWhileStatement statement) {
      myResult.add(createInfoImpl(PsiType.BOOLEAN, ExpectedTypeInfo.TYPE_STRICTLY, PsiType.BOOLEAN, TailTypes.WHILE_RPARENTH));
    }

    @Override
    public void visitDoWhileStatement(PsiDoWhileStatement statement) {
      myResult.add(createInfoImpl(PsiType.BOOLEAN, ExpectedTypeInfo.TYPE_STRICTLY, PsiType.BOOLEAN, TailTypes.WHILE_RPARENTH));
    }

    @Override
    public void visitForStatement(@NotNull PsiForStatement statement) {
      if (myExpr.equals(statement.getCondition())) {
        myResult.add(createInfoImpl(PsiType.BOOLEAN, ExpectedTypeInfo.TYPE_STRICTLY, PsiType.BOOLEAN, TailType.SEMICOLON));
      }
    }

    @Override
    public void visitAssertStatement(@NotNull PsiAssertStatement statement) {
      if (statement.getAssertDescription() == myExpr) {
        final PsiClassType stringType = PsiType.getJavaLangString(myExpr.getManager(), myExpr.getResolveScope());
        myResult.add(createInfoImpl(stringType, ExpectedTypeInfo.TYPE_STRICTLY, stringType, TailType.SEMICOLON));
      }
      else {
        myResult.add(createInfoImpl(PsiType.BOOLEAN, ExpectedTypeInfo.TYPE_STRICTLY, PsiType.BOOLEAN, TailType.SEMICOLON));
      }
    }

    @Override
    public void visitForeachStatement(@NotNull PsiForeachStatement statement) {
      if (myExpr.equals(statement.getIteratedValue())) {
        PsiType type = statement.getIterationParameter().getType();

        if (PsiType.NULL.equals(type)) return;

        PsiType arrayType = type.createArrayType();
        myResult.add(createInfoImpl(arrayType, arrayType));

        PsiManager manager = statement.getManager();
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(manager.getProject());
        PsiClass iterableClass =
          JavaPsiFacade.getInstance(manager.getProject()).findClass("java.lang.Iterable", statement.getResolveScope());
        if (iterableClass != null && iterableClass.getTypeParameters().length == 1) {
          Map<PsiTypeParameter, PsiType> map = new HashMap<>();
          map.put(iterableClass.getTypeParameters()[0], PsiWildcardType.createExtends(manager, type));
          PsiType iterableType = factory.createType(iterableClass, factory.createSubstitutor(map));
          myResult.add(createInfoImpl(iterableType, iterableType));
        }
      }
    }

    @Override
    public void visitSwitchStatement(@NotNull PsiSwitchStatement statement) {
      processSwitchBlock(statement);
    }

    @Override
    public void visitSwitchExpression(@NotNull PsiSwitchExpression expression) {
      processSwitchBlock(expression);
    }

    public void processSwitchBlock(@NotNull PsiSwitchBlock statement) {
      myResult.add(createInfoImpl(PsiType.LONG, PsiType.INT));
      LanguageLevel level = PsiUtil.getLanguageLevel(statement);
      if (level.isAtLeast(LanguageLevel.JDK_1_5)) {
        PsiClassType enumType = TypeUtils.getType(CommonClassNames.JAVA_LANG_ENUM, statement);
        myResult.add(createInfoImpl(enumType, enumType));

        if (level.isAtLeast(LanguageLevel.JDK_1_7)) {
          PsiClassType stringType = TypeUtils.getStringType(statement);
          myResult.add(createInfoImpl(stringType, stringType));
        }
      }
    }

    @Override
    public void visitSynchronizedStatement(@NotNull PsiSynchronizedStatement statement) {
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(statement.getProject());
      PsiType objectType = factory.createTypeByFQClassName(CommonClassNames.JAVA_LANG_OBJECT, myExpr.getResolveScope());
      myResult.add(createInfoImpl(objectType, objectType));
    }

    @Override
    public void visitVariable(@NotNull PsiVariable variable) {
      if (variable instanceof PsiLocalVariable && myForCompletion && myHops < MAX_VAR_HOPS) {
        PsiTypeElement typeElement = variable.getTypeElement();
        if (typeElement != null && typeElement.isInferredType()) {
          PsiElement block = PsiUtil.getVariableCodeBlock(variable, null);
          if (block != null) {
            myHops++;
            List<PsiReferenceExpression> refs = StreamEx.of(VariableAccessUtils.getVariableReferences(variable, block))
              // Remove invalid refs from initializer/annotations to avoid possible SOE
              .remove(ref -> PsiTreeUtil.isAncestor(variable, ref, true))
              .toList();
            for (PsiReferenceExpression ref : refs) {
              myExpr = ref;
              ref.getParent().accept(this);
              if (myResult.size() >= myMaxCandidates) {
                break;
              }
            }
            if (myResult.size() > 1) {
              Set<ExpectedTypeInfo> distinct = new LinkedHashSet<>(myResult);
              myResult.clear();
              myResult.addAll(distinct);
            }
            if (!myResult.isEmpty()) {
              return;
            }
          }
        }
      }
      PsiType type = variable.getType();
      TailType tail = variable instanceof PsiResourceVariable ? TailType.NONE :
                      PsiUtilCore.getElementType(PsiTreeUtil.nextCodeLeaf(variable)) == JavaTokenType.COMMA ? CommaTailType.INSTANCE :
                      TailType.SEMICOLON;
      myResult.add(createInfoImpl(type, ExpectedTypeInfo.TYPE_OR_SUBTYPE, type, tail, null, getPropertyName(variable)));
    }

    @Override
    public void visitAssignmentExpression(@NotNull PsiAssignmentExpression assignment) {
      if (myExpr.equals(assignment.getRExpression())) {
        PsiExpression lExpr = assignment.getLExpression();
        PsiType type = lExpr.getType();
        if (type != null) {
          TailType tailType = getAssignmentRValueTailType(assignment);
          Supplier<String> expectedName = ExpectedTypeInfoImpl.NULL;
          if (lExpr instanceof PsiReferenceExpression) {
            PsiElement refElement = ((PsiReferenceExpression)lExpr).resolve();
            if (refElement instanceof PsiVariable) {
              expectedName = getPropertyName((PsiVariable)refElement);
            }
          }
          myResult.add(createInfoImpl(type, ExpectedTypeInfo.TYPE_OR_SUBTYPE, type, tailType, null, expectedName));
        }
      }
      else {
        if (myForCompletion) {
          myExpr = (PsiExpression)myExpr.getParent();
          assignment.getParent().accept(this);
          return;
        }

        PsiExpression rExpr = assignment.getRExpression();
        if (rExpr != null) {
          PsiType type = rExpr.getType();
          if (type != null && type != PsiType.NULL) {
            if (type instanceof PsiClassType) {
              final PsiClass resolved = ((PsiClassType)type).resolve();
              if (resolved instanceof PsiAnonymousClass) {
                type = ((PsiAnonymousClass)resolved).getBaseClassType();
              }
            }
            final int kind = assignment.getOperationTokenType() != JavaTokenType.EQ
                             ? ExpectedTypeInfo.TYPE_STRICTLY
                             : ExpectedTypeInfo.TYPE_OR_SUPERTYPE;
            myResult.add(createInfoImpl(type, kind, type, TailType.NONE));
          }
        }
      }
    }

    @NotNull
    private static TailType getAssignmentRValueTailType(@NotNull PsiAssignmentExpression assignment) {
      if (assignment.getParent() instanceof PsiExpressionStatement) {
        if (!(assignment.getParent().getParent() instanceof PsiForStatement)) {
          return TailType.SEMICOLON;
        }

        PsiForStatement forStatement = (PsiForStatement)assignment.getParent().getParent();
        if (!assignment.getParent().equals(forStatement.getUpdate())) {
          return TailType.SEMICOLON;
        }
      }
      return TailType.NONE;
    }

    @Override
    public void visitExpressionList(@NotNull PsiExpressionList list) {
      PsiResolveHelper helper = JavaPsiFacade.getInstance(list.getProject()).getResolveHelper();
      PsiElement parent = list.getParent();
      if (parent instanceof PsiMethodCallExpression) {
        PsiMethodCallExpression methodCall = (PsiMethodCallExpression)parent;
        CandidateInfo[] candidates = helper.getReferencedMethodCandidates(methodCall, false, true);
        Collections.addAll(myResult, getExpectedArgumentTypesForMethodCall(candidates, list, myExpr, myForCompletion));
      }
      else if (parent instanceof PsiEnumConstant) {
        getExpectedArgumentsTypesForEnumConstant((PsiEnumConstant)parent, list);
      }
      else if (parent instanceof PsiNewExpression) {
        getExpectedArgumentsTypesForNewExpression((PsiNewExpression)parent, list);
      }
      else if (parent instanceof PsiAnonymousClass) {
        getExpectedArgumentsTypesForNewExpression((PsiNewExpression)parent.getParent(), list);
      }
      else if (parent instanceof PsiSwitchLabelStatementBase) {
        PsiSwitchBlock switchBlock = ((PsiSwitchLabelStatementBase)parent).getEnclosingSwitchBlock();
        handleCaseElementList(switchBlock);
      }
    }

    private void handleCaseElementList(PsiSwitchBlock switchBlock) {
      if (switchBlock != null) {
        PsiExpression expression = switchBlock.getExpression();
        if (expression != null) {
          PsiType type = expression.getType();
          if (type != null) {
            myResult.add(createInfoImpl(type, ExpectedTypeInfo.TYPE_OR_SUBTYPE, type, TailTypes.forSwitchLabel(switchBlock)));
          }
        }
      }
    }

    @Override
    public void visitCaseLabelElementList(PsiCaseLabelElementList list) {
      PsiElement parent = list.getParent();
      if (parent instanceof PsiSwitchLabelStatementBase) {
        PsiSwitchBlock switchBlock = ((PsiSwitchLabelStatementBase)parent).getEnclosingSwitchBlock();
        handleCaseElementList(switchBlock);
      }
    }

    private void getExpectedArgumentsTypesForEnumConstant(@NotNull final PsiEnumConstant enumConstant,
                                                          @NotNull final PsiExpressionList list) {
      final PsiClass aClass = enumConstant.getContainingClass();
      if (aClass != null) {
        LOG.assertTrue(aClass.isEnum());
        getExpectedTypesForConstructorCall(aClass, list, PsiSubstitutor.EMPTY);
      }
    }

    private void getExpectedArgumentsTypesForNewExpression(@NotNull final PsiNewExpression newExpr,
                                                           @NotNull final PsiExpressionList list) {
      if (PsiDiamondType.hasDiamond(newExpr)) {
        final List<CandidateInfo> candidates = PsiDiamondTypeImpl.collectStaticFactories(newExpr);
        if (candidates != null) {
          final PsiExpressionList argumentList = Objects.requireNonNull(newExpr.getArgumentList());
          Collections.addAll(myResult, getExpectedArgumentTypesForMethodCall(candidates.toArray(CandidateInfo.EMPTY_ARRAY), argumentList, myExpr, myForCompletion));
        }
        return;
      }
      PsiType newType = newExpr.getType();
      if (newType instanceof PsiClassType) {
        JavaResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(newType);
        PsiClass newClass = (PsiClass)resolveResult.getElement();
        final PsiSubstitutor substitutor;
        if (newClass instanceof PsiAnonymousClass) {
          final PsiAnonymousClass anonymous = (PsiAnonymousClass)newClass;
          newClass = anonymous.getBaseClassType().resolve();
          if (newClass == null) return;

          substitutor = TypeConversionUtil.getSuperClassSubstitutor(newClass, anonymous, PsiSubstitutor.EMPTY);
        } else if (newClass != null) {
          substitutor = resolveResult.getSubstitutor();
        }
        else {
          return;
        }
        getExpectedTypesForConstructorCall(newClass, list, substitutor);
      }
    }

    private void getExpectedTypesForConstructorCall(@NotNull PsiClass referencedClass,
                                                    @NotNull PsiExpressionList argumentList,
                                                    @NotNull PsiSubstitutor substitutor) {
      List<CandidateInfo> array = new ArrayList<>();
      for (PsiMethod constructor : referencedClass.getConstructors()) {
        array.add(new MethodCandidateInfo(constructor, substitutor, false, false, argumentList, null, argumentList.getExpressionTypes(), null));
      }
      CandidateInfo[] candidates = array.toArray(CandidateInfo.EMPTY_ARRAY);
      Collections.addAll(myResult, getExpectedArgumentTypesForMethodCall(candidates, argumentList, myExpr, myForCompletion));
    }

    @Override
    public void visitPolyadicExpression(@NotNull PsiPolyadicExpression expr) {
      PsiExpression[] operands = expr.getOperands();
      final int index = Arrays.asList(operands).indexOf(myExpr);
      if (index < 0) return; // broken syntax

      IElementType op = expr.getOperationTokenType();
      PsiExpression anotherExpr = index > 0 ? operands[0] : 1 < operands.length ? operands[1] : null;

      if (myForCompletion && index == 0) {
        if (op == JavaTokenType.EQEQ || op == JavaTokenType.NE) {
          ContainerUtil.addIfNotNull(myResult, getEqualsType(anotherExpr));
        }
        final MyParentVisitor visitor = new MyParentVisitor(expr, true, myClassProvider, myVoidable, myUsedAfter, myMaxCandidates);
        myExpr = (PsiExpression)myExpr.getParent();
        expr.getParent().accept(visitor);
        myResult.addAll(visitor.myResult);
        if (!(expr.getParent() instanceof PsiExpressionList)) {
          myResult.replaceAll(
            info -> createInfoImpl(info.getType(), info.getKind(), info.getDefaultType(), TailType.NONE, info.getCalledMethod(),
                                   ((ExpectedTypeInfoImpl)info)::getExpectedName
            ));
        }
        return;
      }

      PsiType anotherType = anotherExpr != null ? anotherExpr.getType() : null;

      if (op == JavaTokenType.MINUS ||
          op == JavaTokenType.ASTERISK ||
          op == JavaTokenType.DIV ||
          op == JavaTokenType.PERC ||
          op == JavaTokenType.LT ||
          op == JavaTokenType.GT ||
          op == JavaTokenType.LE ||
          op == JavaTokenType.GE) {
        if (anotherType != null) {
          myResult.add(createInfoImpl(PsiType.DOUBLE, anotherType));
        }
      }
      else if (op == JavaTokenType.PLUS) {
        if (anotherType == null || anotherType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
          PsiClassType objectType = PsiType.getJavaLangObject(expr.getManager(), expr.getResolveScope());
          myResult.add(createInfoImpl(objectType, anotherType != null ? anotherType : objectType));
        }
        else if (PsiType.DOUBLE.isAssignableFrom(anotherType)) {
          myResult.add(createInfoImpl(PsiType.DOUBLE, anotherType));
        }
      }
      else if (op == JavaTokenType.EQEQ || op == JavaTokenType.NE) {
        ContainerUtil.addIfNotNull(myResult, getEqualsType(anotherExpr));
      }
      else if (op == JavaTokenType.LTLT || op == JavaTokenType.GTGT || op == JavaTokenType.GTGTGT) {
        if (anotherType != null) {
          myResult.add(createInfoImpl(PsiType.LONG, ExpectedTypeInfo.TYPE_BETWEEN, PsiType.SHORT, TailType.NONE));
        }
      }
      else if (op == JavaTokenType.OROR || op == JavaTokenType.ANDAND) {
        myResult.add(createInfoImpl(PsiType.BOOLEAN, ExpectedTypeInfo.TYPE_STRICTLY, PsiType.BOOLEAN, TailType.NONE));
      }
      else if (op == JavaTokenType.OR || op == JavaTokenType.XOR || op == JavaTokenType.AND) {
        if (anotherType != null) {
          ExpectedTypeInfoImpl info;
          if (PsiType.BOOLEAN.equals(anotherType)) {
            info = createInfoImpl(anotherType, ExpectedTypeInfo.TYPE_STRICTLY, anotherType, TailType.NONE);
          }
          else {
            info = createInfoImpl(PsiType.LONG, anotherType);
          }
          myResult.add(info);
        }
      }
    }

    @Nullable
    private static ExpectedTypeInfo getEqualsType(@Nullable PsiExpression anotherExpr) {
      PsiType anotherType = anotherExpr != null ? anotherExpr.getType() : null;
      if (anotherType == null) {
        return null;
      }

      Supplier<String> expectedName = ExpectedTypeInfoImpl.NULL;
      if (anotherExpr instanceof PsiReferenceExpression) {
        PsiElement refElement = ((PsiReferenceExpression)anotherExpr).resolve();
        if (refElement instanceof PsiVariable) {
          expectedName = getPropertyName((PsiVariable)refElement);
        }
      }
      ExpectedTypeInfoImpl info;
      if (anotherType instanceof PsiPrimitiveType) {
        if (PsiType.BOOLEAN.equals(anotherType)) {
          info = createInfoImpl(anotherType, ExpectedTypeInfo.TYPE_STRICTLY, anotherType, TailType.NONE, null, expectedName);
        }
        else if (PsiType.NULL.equals(anotherType)) {
          PsiType objectType = PsiType.getJavaLangObject(anotherExpr.getManager(), anotherExpr.getResolveScope());
          info = createInfoImpl(objectType, ExpectedTypeInfo.TYPE_OR_SUBTYPE, objectType, TailType.NONE, null, expectedName);
        }
        else {
          info = createInfoImpl(PsiType.DOUBLE, ExpectedTypeInfo.TYPE_OR_SUBTYPE, anotherType, TailType.NONE, null, expectedName);
        }
      }
      else {
        info = createInfoImpl(anotherType, ExpectedTypeInfo.TYPE_STRICTLY, anotherType, TailType.NONE, null, expectedName);
      }

      return info;
    }

    @Override
    public void visitPrefixExpression(@NotNull PsiPrefixExpression expr) {
      IElementType i = expr.getOperationTokenType();
      final PsiType type = expr.getType();
      final PsiElement parent = expr.getParent();
      final TailType tailType = parent instanceof PsiAssignmentExpression && ((PsiAssignmentExpression)parent).getRExpression() == expr ?
                                getAssignmentRValueTailType((PsiAssignmentExpression)parent) :
                                TailType.NONE;
      if (i == JavaTokenType.PLUSPLUS || i == JavaTokenType.MINUSMINUS || i == JavaTokenType.TILDE) {
        ExpectedTypeInfoImpl info;
        if (myUsedAfter && type != null) {
          info = createInfoImpl(type, ExpectedTypeInfo.TYPE_STRICTLY, type, tailType);
        }
        else {
          if (type != null) {
            info = createInfoImpl(type, type instanceof PsiPrimitiveType ? ExpectedTypeInfo.TYPE_OR_SUPERTYPE : ExpectedTypeInfo.TYPE_OR_SUBTYPE, PsiType.INT, tailType);
          }
          else {
            info = createInfoImpl(PsiType.LONG, ExpectedTypeInfo.TYPE_OR_SUBTYPE, PsiType.INT, tailType);
          }
        }
        myResult.add(info);
      }
      else if (i == JavaTokenType.PLUS || i == JavaTokenType.MINUS) {
        if (parent instanceof PsiStatement) {
          myResult.add(createInfoImpl(PsiType.DOUBLE, ExpectedTypeInfo.TYPE_OR_SUBTYPE, PsiType.INT, tailType));
        }
        else {
          myExpr = (PsiExpression)myExpr.getParent();
          parent.accept(this);
        }
      }
      else if (i == JavaTokenType.EXCL) {
        myResult.add(createInfoImpl(PsiType.BOOLEAN, ExpectedTypeInfo.TYPE_STRICTLY, PsiType.BOOLEAN, tailType));
      }
    }

    @Override
    public void visitPostfixExpression(@NotNull PsiPostfixExpression expr) {
      if (myForCompletion) return;
      PsiType type = expr.getType();
      ExpectedTypeInfoImpl info;
      if (myUsedAfter && type != null) {
        info = createInfoImpl(type, ExpectedTypeInfo.TYPE_STRICTLY, type, TailType.NONE);
      }
      else {
        if (type != null) {
          info = createInfoImpl(type, type instanceof PsiPrimitiveType ? ExpectedTypeInfo.TYPE_OR_SUPERTYPE : ExpectedTypeInfo.TYPE_OR_SUBTYPE, PsiType.INT, TailType.NONE);
        }
        else {
          info = createInfoImpl(PsiType.LONG, PsiType.INT);
        }
      }
      myResult.add(info);
    }

    @Override
    public void visitArrayInitializerExpression(@NotNull PsiArrayInitializerExpression expr) {
      PsiElement pParent = expr.getParent();
      PsiType arrayType = null;
      if (pParent instanceof PsiVariable) {
        arrayType = ((PsiVariable)pParent).getType();
      }
      else if (pParent instanceof PsiNewExpression) {
        arrayType = ((PsiNewExpression)pParent).getType();
      }
      else if (pParent instanceof PsiArrayInitializerExpression) {
        PsiType type = ((PsiArrayInitializerExpression)pParent).getType();
        if (type instanceof PsiArrayType) {
          arrayType = ((PsiArrayType)type).getComponentType();
        }
      }

      if (arrayType instanceof PsiArrayType) {
        PsiType componentType = ((PsiArrayType)arrayType).getComponentType();
        myResult.add(createInfoImpl(componentType, componentType));
      }
    }

    @Override
    public void visitNewExpression(@NotNull PsiNewExpression expression) {
      PsiExpression[] arrayDimensions = expression.getArrayDimensions();
      for (PsiExpression dimension : arrayDimensions) {
        if (myExpr.equals(dimension)) {
          myResult.add(createInfoImpl(PsiType.INT, PsiType.INT));
          return;
        }
      }
    }

    @Override
    public void visitArrayAccessExpression(@NotNull PsiArrayAccessExpression expr) {
      if (myExpr.equals(expr.getIndexExpression())) {
        myResult.add(createInfoImpl(PsiType.INT, PsiType.INT));
      }
      else if (myExpr.equals(expr.getArrayExpression())) {
        if (myForCompletion) {
          myExpr = (PsiExpression)myExpr.getParent();
          expr.getParent().accept(this);
          return;
        }

        PsiElement parent = expr.getParent();
        MyParentVisitor visitor = new MyParentVisitor(expr, false, myClassProvider, myVoidable, myUsedAfter, myMaxCandidates);
        myExpr = (PsiExpression)myExpr.getParent();
        parent.accept(visitor);
        ExpectedTypeInfo[] componentTypeInfo = visitor.getResult();
        if (componentTypeInfo.length == 0) {
          myResult.addAll(anyArrayType());
        }
        else {
          for (ExpectedTypeInfo compInfo : componentTypeInfo) {
            PsiType expectedArrayType = compInfo.getType().createArrayType();
            myResult.add(createInfoImpl(expectedArrayType, expectedArrayType));
          }
        }
      }
    }

    @Override
    public void visitConditionalExpression(@NotNull PsiConditionalExpression expr) {
      if (myExpr.equals(expr.getCondition())) {
        if (myForCompletion) {
          myExpr = expr;
          myExpr.getParent().accept(this);
          return;
        }

        myResult.add(createInfoImpl(PsiType.BOOLEAN, ExpectedTypeInfo.TYPE_STRICTLY, PsiType.BOOLEAN, TailType.NONE));
      }
      else if (myExpr.equals(expr.getThenExpression())) {
        ExpectedTypeInfo[] types = getExpectedTypes(expr, myForCompletion, ourGlobalScopeClassProvider, false, false, myMaxCandidates);
        for (int i = 0; i < types.length; i++) {
          final ExpectedTypeInfo info = types[i];
          types[i] = createInfoImpl(info.getType(), info.getKind(), info.getDefaultType(), TailType.COND_EXPR_COLON, info.getCalledMethod(),
                                    ((ExpectedTypeInfoImpl)info)::getExpectedName);
        }
        Collections.addAll(myResult, types);
      }
      else {
        if (!myExpr.equals(expr.getElseExpression())) {
          LOG.error(Arrays.asList(expr.getChildren()) + "; " + myExpr);
        }
        Collections.addAll(myResult, getExpectedTypes(expr, myForCompletion, ourGlobalScopeClassProvider, false, false, myMaxCandidates));
      }
    }

    @Override
    public void visitThrowStatement(@NotNull PsiThrowStatement statement) {
      if (statement.getException() == myExpr) {
        PsiManager manager = statement.getManager();
        PsiType throwableType = JavaPsiFacade.getElementFactory(manager.getProject()).createTypeByFQClassName(CommonClassNames.JAVA_LANG_THROWABLE, myExpr.getResolveScope());
        PsiElement container = PsiTreeUtil.getParentOfType(statement, PsiMethod.class, PsiLambdaExpression.class, PsiClass.class);
        PsiType[] throwsTypes = PsiType.EMPTY_ARRAY;
        if (container instanceof PsiMethod) {
          throwsTypes = ((PsiMethod)container).getThrowsList().getReferencedTypes();
        }
        else if (container instanceof PsiLambdaExpression) {
          final PsiMethod method = LambdaUtil.getFunctionalInterfaceMethod(container);
          if (method != null) {
            throwsTypes = method.getThrowsList().getReferencedTypes();
          }
        }

        if (throwsTypes.length == 0) {
          final PsiClassType exceptionType = JavaPsiFacade.getElementFactory(manager.getProject()).createTypeByFQClassName("java.lang.Exception", myExpr.getResolveScope());
          throwsTypes = new PsiClassType[]{exceptionType};
        }

        for (PsiType throwsType : throwsTypes) {
          myResult.add(createInfoImpl(
            myExpr instanceof PsiTypeCastExpression && myForCompletion ? throwsType : throwableType,
            ExpectedTypeInfo.TYPE_OR_SUBTYPE,
            throwsType,
            TailType.SEMICOLON
          ));
        }
      }
    }

    @Override
    public void visitCodeFragment(@NotNull JavaCodeFragment codeFragment) {
      if (codeFragment instanceof PsiExpressionCodeFragment) {
        final PsiType type = ((PsiExpressionCodeFragment)codeFragment).getExpectedType();
        if (type != null) {
          myResult.add(createInfoImpl(type, type));
        }
      }
    }

    private ExpectedTypeInfo @NotNull [] getExpectedArgumentTypesForMethodCall(CandidateInfo @NotNull [] allCandidates,
                                                                               @NotNull PsiExpressionList argumentList,
                                                                               @NotNull PsiExpression argument,
                                                                               boolean forCompletion) {
      if (allCandidates.length == 0) {
        return ExpectedTypeInfo.EMPTY_ARRAY;
      }

      if (CodeInsightSettings.getInstance().SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION) {
        allCandidates = selectCandidateChosenOnCompletion(argumentList.getParent(), allCandidates);
      }

      PsiMethod toExclude = JavaPsiConstructorUtil.isConstructorCall(argumentList.getParent())
                            ? PsiTreeUtil.getParentOfType(argument, PsiMethod.class) : null;

      PsiResolveHelper helper = JavaPsiFacade.getInstance(myExpr.getProject()).getResolveHelper();
      List<CandidateInfo> methodCandidates = new ArrayList<>();
      for (CandidateInfo candidate : allCandidates) {
        PsiElement element = candidate.getElement();
        if (element instanceof PsiMethod && helper.isAccessible((PsiMember)element, argumentList, null) && element != toExclude) {
          methodCandidates.add(candidate);
        }
      }
      if (methodCandidates.isEmpty()) {
        Collections.addAll(methodCandidates, allCandidates);
      }

      final PsiExpression[] args = argumentList.getExpressions().clone();
      final int index = ArrayUtil.indexOf(args, argument);
      LOG.assertTrue(index >= 0);

      final PsiExpression[] leftArgs;
      if (index <= args.length - 1) {
        leftArgs = Arrays.copyOf(args, index);
        if (forCompletion) {
          args[index] = null;
        }
      }
      else {
        leftArgs = null;
      }

      ParameterTypeInferencePolicy policy = forCompletion ? CompletionParameterTypeInferencePolicy.INSTANCE : DefaultParameterTypeInferencePolicy.INSTANCE;

      Set<ExpectedTypeInfo> set = new LinkedHashSet<>();
      for (CandidateInfo candidateInfo : methodCandidates) {
        PsiMethod method = (PsiMethod)candidateInfo.getElement();
        PsiTypeParameter returnTypeParameter = getReturnTypeParameterNotMentionedPreviously(index, method);
        PsiSubstitutor substitutor;
        if (candidateInfo instanceof MethodCandidateInfo) {
          MethodCandidateInfo info = (MethodCandidateInfo)candidateInfo;
          substitutor = info.inferSubstitutorFromArgs(policy, args);
          if (!info.isStaticsScopeCorrect() && !method.hasModifierProperty(PsiModifier.STATIC) || info.getInferenceErrorMessage() != null) continue;
          if (forCompletion && returnTypeParameter != null) {
            PsiType substituted = substitutor.substitute(returnTypeParameter);
            if (substituted instanceof PsiClassType) {
              // Relax return type substitution
              substitutor = substitutor.put(returnTypeParameter, PsiWildcardType.createExtends(method.getManager(), substituted));
            }
          }
        }
        else {
          substitutor = candidateInfo.getSubstitutor();
        }
        if (substitutor == null) {
          return ExpectedTypeInfo.EMPTY_ARRAY;
        }
        inferMethodCallArgumentTypes(argument, forCompletion, args, index, method, substitutor, set);
        if (set.size() >= myMaxCandidates) break;

        if (leftArgs != null && candidateInfo instanceof MethodCandidateInfo) {
          substitutor = ((MethodCandidateInfo)candidateInfo).inferSubstitutorFromArgs(policy, leftArgs);
          if (substitutor != null) {
            inferMethodCallArgumentTypes(argument, forCompletion, leftArgs, index, method, substitutor, set);
            if (set.size() >= myMaxCandidates) break;
          }
        }
      }

      // try to find some variants without considering previous argument PRIMITIVE_TYPES
      if (forCompletion && set.isEmpty()) {
        for (CandidateInfo candidate : methodCandidates) {
          PsiMethod method = (PsiMethod)candidate.getElement();
          PsiSubstitutor substitutor = candidate.getSubstitutor();
          PsiParameter[] params = method.getParameterList().getParameters();
          if (params.length <= index) continue;
          PsiParameter param = params[index];
          PsiType paramType = getParameterType(param, substitutor);
          if (method.hasTypeParameters() && PsiTypesUtil.mentionsTypeParameters(paramType, ContainerUtil.newHashSet(method.getTypeParameters()))) {
            continue;
          }
          TailType tailType = getMethodArgumentTailType(argument, index, method, substitutor, params);
          ExpectedTypeInfoImpl info = createInfoImpl(paramType, ExpectedTypeInfo.TYPE_OR_SUBTYPE, paramType,
                                                     tailType, method, getPropertyName(param));
          set.add(info);
          if (set.size() >= myMaxCandidates) break;
        }
      }

      return set.toArray(ExpectedTypeInfo.EMPTY_ARRAY);
    }

    @Nullable
    private static PsiTypeParameter getReturnTypeParameterNotMentionedPreviously(int index, PsiMethod method) {
      PsiTypeParameter returnTypeParameter = ObjectUtils.tryCast(PsiUtil.resolveClassInClassTypeOnly(method.getReturnType()), PsiTypeParameter.class);
      if (returnTypeParameter == null || returnTypeParameter.getOwner() != method) return null;
      PsiParameter[] parameters = method.getParameterList().getParameters();
      for (int i = 0; i < index && i < parameters.length; i++) {
        PsiType prevParameterType = parameters[i].getType();
        if (PsiTypesUtil.mentionsTypeParameters(prevParameterType, Set.of(returnTypeParameter))) return null;
      }
      return returnTypeParameter;
    }

    private static CandidateInfo @NotNull [] selectCandidateChosenOnCompletion(@Nullable PsiElement call, CandidateInfo @NotNull [] candidates) {
      if (call instanceof PsiCall) {
        PsiCall originalCall = CompletionUtil.getOriginalElement((PsiCall)call);
        if (originalCall != null) {
          PsiMethod method = CompletionMemory.getChosenMethod(originalCall);
          if (method != null) {
            for (CandidateInfo candidate : candidates) {
              if (CompletionUtil.getOriginalOrSelf(candidate.getElement()) == method) return new CandidateInfo[]{candidate};
            }
          }
        }
      }
      return candidates;
    }

    @NotNull
    private static TailType getMethodArgumentTailType(@NotNull final PsiExpression argument,
                                                      final int index,
                                                      @NotNull final PsiMethod method,
                                                      @NotNull final PsiSubstitutor substitutor,
                                                      final PsiParameter @NotNull [] params) {
      if (index >= params.length || index == params.length - 2 && params[index + 1].isVarArgs()) {
        return TailType.NONE;
      }
      if (index == params.length - 1) {
        final PsiElement call = argument.getParent().getParent();
        // ignore JspMethodCall
        if (call instanceof SyntheticElement) return TailType.NONE;

        PsiType returnType = method.getReturnType();
        if (returnType != null) returnType = substitutor.substitute(returnType);
        return getFinalCallParameterTailType(call, returnType, method);
      }
      if (CodeInsightSettings.getInstance().SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION) {
        PsiCall completedOuterCall = getCompletedOuterCall(argument);
        if (completedOuterCall != null) return new CommaTailTypeWithSyncHintUpdate(completedOuterCall);
      }
      return CommaTailType.INSTANCE;
    }

    @Nullable
    private static PsiCall getCompletedOuterCall(@NotNull PsiExpression argument) {
      PsiElement expressionList = argument.getParent();
      if (expressionList instanceof PsiExpressionList) {
        PsiElement call = expressionList.getParent();
        if (call instanceof PsiCall) {
          PsiCall originalCall = CompletionUtil.getOriginalElement((PsiCall)call);
          if (originalCall != null && JavaMethodCallElement.isCompletionMode(originalCall)) {
            return originalCall;
          }
        }
      }
      return null;
    }

    private static void inferMethodCallArgumentTypes(@NotNull final PsiExpression argument,
                                                     final boolean forCompletion,
                                                     final PsiExpression @NotNull [] args,
                                                     final int index,
                                                     @NotNull final PsiMethod method,
                                                     @NotNull final PsiSubstitutor substitutor,
                                                     @NotNull final Set<? super ExpectedTypeInfo> array) {
      LOG.assertTrue(substitutor.isValid());
      PsiParameter[] parameters = method.getParameterList().getParameters();
      if (!forCompletion && parameters.length != args.length && !method.isVarArgs()) return;
      if (parameters.length <= index && !method.isVarArgs()) return;

      for (int j = 0; j < index; j++) {
        PsiType paramType = getParameterType(parameters[Math.min(parameters.length - 1, j)],
                                             substitutor);
        PsiType argType = args[j].getType();
        if (argType != null && !paramType.isAssignableFrom(argType)) return;
      }
      PsiParameter parameter = parameters[Math.min(parameters.length - 1, index)];
      PsiType parameterType = getParameterType(parameter, substitutor);

      TailType tailType = getMethodArgumentTailType(argument, index, method, substitutor, parameters);
      PsiType defaultType = getDefaultType(method, substitutor, parameterType, argument, args, index);

      NullableComputable<String> propertyName = getPropertyName(parameter);
      ExpectedTypeInfoImpl info = createInfoImpl(parameterType, ExpectedTypeInfo.TYPE_OR_SUBTYPE, defaultType, tailType, method,
                                                 propertyName);
      array.add(info);

      if (index == parameters.length - 1 && parameter.isVarArgs()) {
        //Then we may still want to call with array argument
        final PsiArrayType arrayType = parameterType.createArrayType();
        ExpectedTypeInfoImpl info1 = createInfoImpl(arrayType, ExpectedTypeInfo.TYPE_OR_SUBTYPE, arrayType, tailType, method, propertyName);
        array.add(info1);
      }
    }

    @Nullable
    private static PsiType getDefaultType(@NotNull final PsiMethod method, final PsiSubstitutor substitutor, @NotNull final PsiType parameterType,
                                          @NotNull final PsiExpression argument, @Nullable PsiExpression @NotNull [] args, int index) {
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) return parameterType;

      PsiType hardcoded = HardcodedDefaultTypesKt.getDefaultType(method, substitutor, index, argument);
      if (hardcoded != null) return hardcoded;

      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(containingClass.getProject());
      PsiMethodCallExpression call = ObjectUtils.tryCast(argument.getParent().getParent(), PsiMethodCallExpression.class);
      AssertHint assertHint = AssertHint.createAssertEqualsLikeHintForCompletion(call, args, method, index);
      if (assertHint != null) {
        PsiExpression other = assertHint.getOtherExpression(argument);
        if (other != null) {
          ExpectedTypeInfo info = getEqualsType(other);
          if (info != null && parameterType.isAssignableFrom(info.getDefaultType())) {
            return info.getDefaultType();
          }
        }
      }
      String className = containingClass.getName();
      if (className != null && className.startsWith("Log")) {
        if (parameterType instanceof PsiClassType) {
          PsiType typeArg = PsiUtil.substituteTypeParameter(parameterType, CommonClassNames.JAVA_LANG_CLASS, 0, true);
          if (typeArg instanceof PsiWildcardType && !((PsiWildcardType)typeArg).isBounded() ||
              typeArg != null && TypeConversionUtil.erasure(typeArg).equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
            PsiClass placeClass = PsiTreeUtil.getContextOfType(argument, PsiClass.class);
            PsiClass classClass = ((PsiClassType)parameterType).resolve();
            if (placeClass != null && classClass != null && classClass.getTypeParameters().length == 1) {
              return factory.createType(classClass, factory.createType(placeClass));
            }
          }
        }
      }
      return parameterType;
    }

    private static PsiType getParameterType(@NotNull PsiParameter parameter, @NotNull PsiSubstitutor substitutor) {
      PsiType type = parameter.getType();
      LOG.assertTrue(type.isValid());
      if (parameter.isVarArgs()) {
        if (type instanceof PsiArrayType) {
          type = ((PsiArrayType)type).getComponentType();
        }
        else {
          LOG.error("Vararg parameter with non-array type. Class=" + parameter.getClass() + "; type=" + parameter.getType());
        }
      }
      PsiType parameterType = GenericsUtil.eliminateExtendsFinalWildcard(substitutor.substitute(type));
      if (parameterType instanceof PsiCapturedWildcardType) {
        parameterType = ((PsiCapturedWildcardType)parameterType).getWildcard();
      }
      if (parameterType instanceof PsiWildcardType) {
        final PsiType bound = ((PsiWildcardType)parameterType).getBound();
        return bound != null ? bound : PsiType.getJavaLangObject(parameter.getManager(), GlobalSearchScope.allScope(parameter.getProject()));
      }
      return parameterType;
    }

    @NotNull
    private static NullableComputable<String> getPropertyName(@NotNull final PsiVariable variable) {
      return () -> {
        final String name = variable.getName();
        if (name == null) return null;
        JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(variable.getProject());
        VariableKind variableKind = codeStyleManager.getVariableKind(variable);
        return codeStyleManager.variableNameToPropertyName(name, variableKind);
      };
    }

    @NotNull
    private List<ExpectedTypeInfo> anyArrayType() {
      PsiType objType = PsiType.getJavaLangObject(myExpr.getManager(), myExpr.getResolveScope()).createArrayType();
      ExpectedTypeInfo info = createInfoImpl(objType, objType);
      ExpectedTypeInfo info1 = createInfoImpl(PsiType.DOUBLE.createArrayType(), PsiType.INT.createArrayType());
      PsiType booleanType = PsiType.BOOLEAN.createArrayType();
      ExpectedTypeInfo info2 = createInfoImpl(booleanType, ExpectedTypeInfo.TYPE_STRICTLY, booleanType, TailType.NONE);
      return Arrays.asList(info, info1, info2);
    }

    private ExpectedTypeInfo @NotNull [] findClassesWithDeclaredMethod(@NotNull final PsiMethodCallExpression methodCallExpr) {
      PsiUtilCore.ensureValid(methodCallExpr);
      final PsiReferenceExpression reference = methodCallExpr.getMethodExpression();
      if (reference.getQualifierExpression() instanceof PsiClassObjectAccessExpression) {
        return ExpectedTypeInfo.EMPTY_ARRAY;
      }
      final PsiManager manager = methodCallExpr.getManager();
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());
      Set<PsiMethod> psiMethods = mapToDeepestSuperMethods(myClassProvider.findDeclaredMethods(manager, Objects.requireNonNull(reference.getReferenceName())));
      Set<ExpectedTypeInfo> types = new HashSet<>();
      for (PsiMethod method : psiMethods) {
        final PsiClass aClass = method.getContainingClass();
        if (aClass == null || !facade.getResolveHelper().isAccessible(method, reference, aClass)) continue;

        final PsiSubstitutor substitutor = ExpectedTypeUtil.inferSubstitutor(method, methodCallExpr, false);
        final PsiClassType type =
          substitutor == null ? facade.getElementFactory().createType(aClass) : facade.getElementFactory().createType(aClass, substitutor);

        if (method.hasModifierProperty(PsiModifier.STATIC) || method.hasModifierProperty(PsiModifier.PRIVATE)) {
          types.add(createInfoImpl(type, ExpectedTypeInfo.TYPE_STRICTLY, type, TailType.DOT));
        }
        else {
          types.add(createInfoImpl(type, ExpectedTypeInfo.TYPE_OR_SUBTYPE, type, TailType.DOT));
        }
      }

      return types.toArray(ExpectedTypeInfo.EMPTY_ARRAY);
    }

    private static Set<PsiMethod> mapToDeepestSuperMethods(PsiMethod[] methods) {
      LinkedHashSet<PsiMethod> psiMethods = new LinkedHashSet<>();
      for (PsiMethod m : methods) {
        if (m.hasModifierProperty(PsiModifier.STATIC) || m.hasModifierProperty(PsiModifier.PRIVATE)) {
          psiMethods.add(m);
        }
        else {
          PsiMethod[] superMethods = m.findDeepestSuperMethods();
          if (superMethods.length > 0) {
            psiMethods.addAll(Arrays.asList(superMethods));
          }
          else {
            psiMethods.add(m);
          }
        }
      }
      return psiMethods;
    }

    private ExpectedTypeInfo @NotNull [] findClassesWithDeclaredField(@NotNull PsiReferenceExpression expression) {
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(expression.getProject());
      PsiField[] fields = myClassProvider.findDeclaredFields(expression.getManager(), Objects.requireNonNull(expression.getReferenceName()));
      List<ExpectedTypeInfo> types = new ArrayList<>();
      for (PsiField field : fields) {
        final PsiClass aClass = field.getContainingClass();
        if (aClass == null || !facade.getResolveHelper().isAccessible(field, expression, aClass)) continue;

        final PsiType type = facade.getElementFactory().createType(aClass);

        int kind = field.hasModifierProperty(PsiModifier.STATIC) ||
                   field.hasModifierProperty(PsiModifier.FINAL) ||
                   field.hasModifierProperty(PsiModifier.PRIVATE)
                   ? ExpectedTypeInfo.TYPE_STRICTLY
                   : ExpectedTypeInfo.TYPE_OR_SUBTYPE;
        ExpectedTypeInfo info = createInfoImpl(type, kind, type, TailType.DOT);
        //Do not filter inheritors!
        types.add(info);
      }
      return types.toArray(ExpectedTypeInfo.EMPTY_ARRAY);
    }
  }

  /**
   * Finds fields and methods of specified name whenever corresponding reference has been encountered.
   * By default searches in the global scope (see ourGlobalScopeClassProvider), but caller can provide its own algorithm e.g. to narrow search scope
   */
  public interface ExpectedClassProvider {
    PsiField @NotNull [] findDeclaredFields(@NotNull PsiManager manager, @NotNull String name);

    PsiMethod @NotNull [] findDeclaredMethods(@NotNull PsiManager manager, @NotNull String name);
  }

  @NotNull
  public static TailType getFinalCallParameterTailType(@NotNull PsiElement call, @Nullable PsiType returnType, @NotNull PsiMethod method) {
    if (method.isConstructor() &&
        call instanceof PsiMethodCallExpression && ((PsiMethodCallExpression)call).getMethodExpression() instanceof PsiSuperExpression) {
      return TailTypes.CALL_RPARENTH_SEMICOLON;
    }

    final boolean chainable = !PsiType.VOID.equals(returnType) && returnType != null || method.isConstructor() && call instanceof PsiNewExpression;

    final PsiElement parent = call.getParent();
    final boolean statementContext = parent instanceof PsiExpressionStatement || parent instanceof PsiVariable ||
                                     parent instanceof PsiCodeBlock;

    if (parent instanceof PsiThrowStatement || statementContext && !chainable) {
      return TailTypes.CALL_RPARENTH_SEMICOLON;
    }

    return TailTypes.CALL_RPARENTH;
  }

  private static final class CommaTailTypeWithSyncHintUpdate extends TailType {
    private final PsiCall myOriginalCall;

    private CommaTailTypeWithSyncHintUpdate(@NotNull PsiCall originalCall) {myOriginalCall = originalCall;}

    @Override
    public boolean isApplicable(@NotNull InsertionContext context) {
      return CommaTailType.INSTANCE.isApplicable(context);
    }

    @Override
    public int processTail(Editor editor, int tailOffset) {
      int result = CommaTailType.INSTANCE.processTail(editor, tailOffset);
      if (myOriginalCall.isValid()) {
        PsiDocumentManager.getInstance(myOriginalCall.getProject()).commitDocument(editor.getDocument());
        if (myOriginalCall.isValid()) ParameterHintsPass.asyncUpdate(myOriginalCall, editor);
      }
      return result;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      CommaTailTypeWithSyncHintUpdate update = (CommaTailTypeWithSyncHintUpdate)o;
      return myOriginalCall.equals(update.myOriginalCall);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myOriginalCall);
    }
  }
}
