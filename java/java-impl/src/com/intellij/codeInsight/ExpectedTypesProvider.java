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
package com.intellij.codeInsight;

import com.intellij.codeInsight.daemon.impl.analysis.LambdaHighlightingUtil;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NullableComputable;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.source.resolve.CompletionParameterTypeInferencePolicy;
import com.intellij.psi.impl.source.resolve.DefaultParameterTypeInferencePolicy;
import com.intellij.psi.impl.source.resolve.ParameterTypeInferencePolicy;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.scope.conflictResolvers.DuplicateConflictResolver;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.Stack;
import com.siyeh.ig.psiutils.ExpressionUtils;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author ven
 */
public class ExpectedTypesProvider {
  private static final ExpectedTypeInfo VOID_EXPECTED = createInfoImpl(PsiType.VOID, ExpectedTypeInfo.TYPE_OR_SUBTYPE, PsiType.VOID, TailType.SEMICOLON);

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.ExpectedTypesProvider");

  private ExpectedTypesProvider() {
  }

  public static ExpectedTypesProvider getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, ExpectedTypesProvider.class);
  }

  private static final int MAX_COUNT = 50;
  private static final ExpectedClassProvider ourGlobalScopeClassProvider = new ExpectedClassProvider() {
    @Override
    @NotNull
    public PsiField[] findDeclaredFields(@NotNull final PsiManager manager, @NotNull String name) {
      final PsiShortNamesCache cache = PsiShortNamesCache.getInstance(manager.getProject());
      GlobalSearchScope scope = GlobalSearchScope.allScope(manager.getProject());
      return cache.getFieldsByName(name, scope);
    }

    @Override
    @NotNull
    public PsiMethod[] findDeclaredMethods(@NotNull final PsiManager manager, @NotNull String name) {
      Project project = manager.getProject();
      final PsiShortNamesCache cache = PsiShortNamesCache.getInstance(project);
      GlobalSearchScope sources = GlobalSearchScope.projectScope(project);
      GlobalSearchScope libraries = GlobalSearchScope.notScope(sources);
      PsiMethod[] sourceMethods = cache.getMethodsByNameIfNotMoreThan(name, sources, MAX_COUNT);
      if (sourceMethods.length == MAX_COUNT) return sourceMethods;
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
                                                     NullableComputable<String> expectedName) {
    return new ExpectedTypeInfoImpl(type, kind, defaultType, tailType, calledMethod, expectedName);
  }

  @NotNull
  public static ExpectedTypeInfo[] getExpectedTypes(@Nullable PsiExpression expr, boolean forCompletion) {
    return getExpectedTypes(expr, forCompletion, false, false);
  }

  @NotNull
  public static ExpectedTypeInfo[] getExpectedTypes(@Nullable PsiExpression expr, boolean forCompletion, final boolean voidable, boolean usedAfter) {
    return getExpectedTypes(expr, forCompletion, ourGlobalScopeClassProvider, voidable, usedAfter);
  }

  @NotNull
  public static ExpectedTypeInfo[] getExpectedTypes(@Nullable PsiExpression expr,
                                                    boolean forCompletion,
                                                    ExpectedClassProvider classProvider, boolean usedAfter) {
    return getExpectedTypes(expr, forCompletion, classProvider, false, usedAfter);
  }

  @NotNull
  public static ExpectedTypeInfo[] getExpectedTypes(@Nullable PsiExpression expr, boolean forCompletion, ExpectedClassProvider classProvider,
                                                    final boolean voidable, boolean usedAfter) {
    if (expr == null) return ExpectedTypeInfo.EMPTY_ARRAY;
    PsiElement parent = expr.getParent();
    if (expr instanceof PsiFunctionalExpression && parent instanceof PsiExpressionStatement) {
      final Collection<? extends PsiType> types = FunctionalInterfaceSuggester.suggestFunctionalInterfaces((PsiFunctionalExpression)expr);
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
    MyParentVisitor visitor = new MyParentVisitor(expr, forCompletion, classProvider, voidable, usedAfter);
    if (parent != null) {
      parent.accept(visitor);
    }
    return visitor.getResult();
  }

  @NotNull
  public static PsiType[] processExpectedTypes(@NotNull ExpectedTypeInfo[] infos,
                                               @NotNull PsiTypeVisitor<PsiType> visitor, @NotNull Project project) {
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

  private static void processType(@NotNull PsiType type, @NotNull PsiTypeVisitor<PsiType> visitor, @NotNull Set<PsiType> typeSet) {
    PsiType accepted = type.accept(visitor);
    if (accepted != null) typeSet.add(accepted);
  }

  private static void processPrimitiveTypeAndSubtypes(@NotNull PsiPrimitiveType type,
                                                      @NotNull PsiTypeVisitor<PsiType> visitor,
                                                      @NotNull Set<PsiType> set) {
    if (type.equals(PsiType.BOOLEAN) || type.equals(PsiType.VOID) || type.equals(PsiType.NULL)) return;

    for (int i = 0; ; i++) {
      final PsiType primitive = PRIMITIVE_TYPES[i];
      processType(primitive, visitor, set);
      if (primitive.equals(type)) return;
    }
  }

  public static void processAllSuperTypes(@NotNull PsiType type, @NotNull PsiTypeVisitor<PsiType> visitor, @NotNull Project project, @NotNull Set<PsiType> set, @NotNull Set<PsiType> visited) {
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

  private static class MyParentVisitor extends JavaElementVisitor {
    private PsiExpression myExpr;
    private final boolean myForCompletion;
    private final boolean myUsedAfter;
    private final ExpectedClassProvider myClassProvider;
    private final boolean myVoidable;
    final List<ExpectedTypeInfo> myResult = ContainerUtil.newArrayList();
    @NonNls private static final String LENGTH_SYNTHETIC_ARRAY_FIELD = "length";

    private MyParentVisitor(PsiExpression expr,
                            boolean forCompletion,
                            ExpectedClassProvider classProvider,
                            boolean voidable,
                            boolean usedAfter) {
      myExpr = expr;
      myForCompletion = forCompletion;
      myClassProvider = classProvider;
      myVoidable = voidable;
      myUsedAfter = usedAfter;
    }

    @NotNull
    public ExpectedTypeInfo[] getResult() {
      return myResult.toArray(new ExpectedTypeInfo[myResult.size()]);
    }

    @Override
    public void visitParenthesizedExpression(PsiParenthesizedExpression expression) {
      PsiElement parent = expression.getParent();
      if (parent != null) {
        final MyParentVisitor visitor = new MyParentVisitor(expression, myForCompletion, myClassProvider, myVoidable, myUsedAfter);
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
        final MyParentVisitor visitor = new MyParentVisitor(expression, true, myClassProvider, myVoidable, myUsedAfter);
        expression.getParent().accept(visitor);
        myResult.addAll(visitor.myResult);
        return;
      }

      String referenceName = expression.getReferenceName();
      if (referenceName != null) {
        final PsiElement parent = expression.getParent();
        if (parent instanceof PsiMethodCallExpression) {
          Collections.addAll(myResult, findClassesWithDeclaredMethod((PsiMethodCallExpression)parent, false));
        }
        else if (parent instanceof PsiReferenceExpression || parent instanceof PsiVariable ||
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
      if (myVoidable) {
        myResult.add(VOID_EXPECTED);
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
        NullableComputable<String> expectedName;
        if (PropertyUtil.isSimplePropertyAccessor(scopeMethod)) {
          expectedName = () -> PropertyUtil.getPropertyName(scopeMethod);
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

        PsiType arrayType = type.createArrayType();
        myResult.add(createInfoImpl(arrayType, arrayType));

        PsiManager manager = statement.getManager();
        PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
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
      myResult.add(createInfoImpl(PsiType.LONG, PsiType.INT));
      if (!PsiUtil.isLanguageLevel5OrHigher(statement)) {
        return;
      }

      PsiManager manager = statement.getManager();
      PsiClassType enumType = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createTypeByFQClassName("java.lang.Enum", statement.getResolveScope());
      myResult.add(createInfoImpl(enumType, enumType));
    }

    @Override
    public void visitSwitchLabelStatement(@NotNull final PsiSwitchLabelStatement statement) {
      final PsiSwitchStatement switchStatement = statement.getEnclosingSwitchStatement();
      if (switchStatement != null) {
        final PsiExpression expression = switchStatement.getExpression();
        if (expression != null) {
          final PsiType type = expression.getType();
          if (type != null) {
            myResult.add(createInfoImpl(type, ExpectedTypeInfo.TYPE_OR_SUBTYPE, type, TailType.CASE_COLON));
          }
        }
      }
    }

    @Override
    public void visitSynchronizedStatement(@NotNull PsiSynchronizedStatement statement) {
      PsiElementFactory factory = JavaPsiFacade.getInstance(statement.getProject()).getElementFactory();
      PsiType objectType = factory.createTypeByFQClassName(CommonClassNames.JAVA_LANG_OBJECT, myExpr.getResolveScope());
      myResult.add(createInfoImpl(objectType, objectType));
    }

    @Override
    public void visitVariable(@NotNull PsiVariable variable) {
      PsiType type = variable.getType();
      TailType tail = variable instanceof PsiResourceVariable ? TailType.NONE : TailType.SEMICOLON;
      myResult.add(createInfoImpl(type, ExpectedTypeInfo.TYPE_OR_SUBTYPE, type, tail, null, getPropertyName(variable)));
    }

    @Override
    public void visitAssignmentExpression(@NotNull PsiAssignmentExpression assignment) {
      if (myExpr.equals(assignment.getRExpression())) {
        PsiExpression lExpr = assignment.getLExpression();
        PsiType type = lExpr.getType();
        if (type != null) {
          TailType tailType = getAssignmentRValueTailType(assignment);
          NullableComputable<String> expectedName = ExpectedTypeInfoImpl.NULL;
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
        final JavaResolveResult[] candidates = PsiDiamondTypeImpl.collectStaticFactories(newExpr, DuplicateConflictResolver.INSTANCE);
        if (candidates != null) {
          final PsiExpressionList argumentList = newExpr.getArgumentList();
          Collections.addAll(myResult, getExpectedArgumentTypesForMethodCall(ContainerUtil.map(candidates, candidate -> (CandidateInfo)candidate, CandidateInfo.EMPTY_ARRAY), argumentList, myExpr, myForCompletion));
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

    private void getExpectedTypesForConstructorCall(@NotNull final PsiClass referencedClass,
                                                    @NotNull final PsiExpressionList argumentList,
                                                    final PsiSubstitutor substitutor) {
      List<CandidateInfo> array = new ArrayList<>();
      for (PsiMethod constructor : referencedClass.getConstructors()) {
        array.add(new MethodCandidateInfo(constructor, substitutor, false, false, argumentList, null, argumentList.getExpressionTypes(), null));
      }
      CandidateInfo[] candidates = array.toArray(new CandidateInfo[array.size()]);
      Collections.addAll(myResult, getExpectedArgumentTypesForMethodCall(candidates, argumentList, myExpr, myForCompletion));
    }

    @Override
    public void visitPolyadicExpression(@NotNull PsiPolyadicExpression expr) {
      PsiExpression[] operands = expr.getOperands();
      final int index = Arrays.asList(operands).indexOf(myExpr);
      if (index < 0) return; // broken syntax

      if (myForCompletion && index == 0) {
        final MyParentVisitor visitor = new MyParentVisitor(expr, true, myClassProvider, myVoidable, myUsedAfter);
        myExpr = (PsiExpression)myExpr.getParent();
        expr.getParent().accept(visitor);
        myResult.addAll(visitor.myResult);
        if (!(expr.getParent() instanceof PsiExpressionList)) {
          for (int i = 0; i < myResult.size(); i++) {
            final ExpectedTypeInfo info = myResult.get(i);
            myResult.set(i, createInfoImpl(info.getType(), info.getKind(), info.getDefaultType(), TailType.NONE, info.getCalledMethod(),
                                           ((ExpectedTypeInfoImpl)info)::getExpectedName
            ));
          }
        }
        return;
      }
      PsiExpression anotherExpr = index > 0 ? operands[0] : 1 < operands.length ? operands[1] : null;
      PsiType anotherType = anotherExpr != null ? anotherExpr.getType() : null;
      IElementType i = expr.getOperationTokenType();
      if (i == JavaTokenType.MINUS ||
          i == JavaTokenType.ASTERISK ||
          i == JavaTokenType.DIV ||
          i == JavaTokenType.PERC ||
          i == JavaTokenType.LT ||
          i == JavaTokenType.GT ||
          i == JavaTokenType.LE ||
          i == JavaTokenType.GE) {
        if (anotherType != null) {
          myResult.add(createInfoImpl(PsiType.DOUBLE, anotherType));
        }
      }
      else if (i == JavaTokenType.PLUS) {
        if (anotherType == null || anotherType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
          PsiClassType objectType = PsiType.getJavaLangObject(expr.getManager(), expr.getResolveScope());
          myResult.add(createInfoImpl(objectType, anotherType != null ? anotherType : objectType));
        }
        else if (PsiType.DOUBLE.isAssignableFrom(anotherType)) {
          myResult.add(createInfoImpl(PsiType.DOUBLE, anotherType));
        }
      }
      else if (i == JavaTokenType.EQEQ || i == JavaTokenType.NE) {
        ContainerUtil.addIfNotNull(myResult, getEqualsType(anotherExpr));
      }
      else if (i == JavaTokenType.LTLT || i == JavaTokenType.GTGT || i == JavaTokenType.GTGTGT) {
        if (anotherType != null) {
          myResult.add(createInfoImpl(PsiType.LONG, ExpectedTypeInfo.TYPE_BETWEEN, PsiType.SHORT, TailType.NONE));
        }
      }
      else if (i == JavaTokenType.OROR || i == JavaTokenType.ANDAND) {
        myResult.add(createInfoImpl(PsiType.BOOLEAN, ExpectedTypeInfo.TYPE_STRICTLY, PsiType.BOOLEAN, TailType.NONE));
      }
      else if (i == JavaTokenType.OR || i == JavaTokenType.XOR || i == JavaTokenType.AND) {
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

      NullableComputable<String> expectedName = ExpectedTypeInfoImpl.NULL;
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
        MyParentVisitor visitor = new MyParentVisitor(expr, false, myClassProvider, myVoidable, myUsedAfter);
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
        ExpectedTypeInfo[] types = getExpectedTypes(expr, myForCompletion);
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
        Collections.addAll(myResult, getExpectedTypes(expr, myForCompletion));
      }
    }

    @Override
    public void visitThrowStatement(@NotNull PsiThrowStatement statement) {
      if (statement.getException() == myExpr) {
        PsiManager manager = statement.getManager();
        PsiType throwableType = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createTypeByFQClassName("java.lang.Throwable", myExpr.getResolveScope());
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
          final PsiClassType exceptionType = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createTypeByFQClassName("java.lang.Exception", myExpr.getResolveScope());
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

    @NotNull
    private ExpectedTypeInfo[] getExpectedArgumentTypesForMethodCall(@NotNull CandidateInfo[] allCandidates,
                                                                     @NotNull PsiExpressionList argumentList,
                                                                     @NotNull PsiExpression argument,
                                                                     boolean forCompletion) {
      if (allCandidates.length == 0) {
        return ExpectedTypeInfo.EMPTY_ARRAY;
      }

      PsiMethod toExclude = ExpressionUtils.isConstructorInvocation(argumentList.getParent())
                            ? PsiTreeUtil.getParentOfType(argument, PsiMethod.class) : null;

      PsiResolveHelper helper = JavaPsiFacade.getInstance(myExpr.getProject()).getResolveHelper();
      List<CandidateInfo> methodCandidates = new ArrayList<>();
      for (CandidateInfo candidate : allCandidates) {
        PsiElement element = candidate.getElement();
        if (element instanceof PsiMethod && helper.isAccessible((PsiMember)element, argumentList, null) && element != toExclude) {
          methodCandidates.add(candidate);
        }
      }

      final PsiExpression[] args = argumentList.getExpressions().clone();
      final int index = ArrayUtil.indexOf(args, argument);
      LOG.assertTrue(index >= 0);

      final PsiExpression[] leftArgs;
      if (index <= args.length - 1) {
        leftArgs = new PsiExpression[index];
        System.arraycopy(args, 0, leftArgs, 0, index);
        if (forCompletion) {
          args[index] = null;
        }
      }
      else {
        leftArgs = null;
      }

      ParameterTypeInferencePolicy policy = forCompletion ? CompletionParameterTypeInferencePolicy.INSTANCE : DefaultParameterTypeInferencePolicy.INSTANCE;

      Set<ExpectedTypeInfo> array = new LinkedHashSet<>();
      for (CandidateInfo candidateInfo : methodCandidates) {
        PsiMethod method = (PsiMethod)candidateInfo.getElement();
        PsiSubstitutor substitutor;
        if (candidateInfo instanceof MethodCandidateInfo) {
          final MethodCandidateInfo info = (MethodCandidateInfo)candidateInfo;
          substitutor = MethodCandidateInfo.ourOverloadGuard.doPreventingRecursion(argumentList, false,
                                                                                   () -> info.inferSubstitutorFromArgs(policy, args));
          if (!info.isStaticsScopeCorrect() && !method.hasModifierProperty(PsiModifier.STATIC) || info.getInferenceErrorMessage() != null) continue;
        }
        else {
          substitutor = MethodCandidateInfo.ourOverloadGuard.doPreventingRecursion(argumentList, false, candidateInfo::getSubstitutor);
        }
        if (substitutor == null) {
          return ExpectedTypeInfo.EMPTY_ARRAY;
        }
        inferMethodCallArgumentTypes(argument, forCompletion, args, index, method, substitutor, array);

        if (leftArgs != null && candidateInfo instanceof MethodCandidateInfo) {
          substitutor = MethodCandidateInfo.ourOverloadGuard.doPreventingRecursion(argumentList, false,
                                                                                   () -> ((MethodCandidateInfo)candidateInfo).inferSubstitutorFromArgs(policy, leftArgs));
          if (substitutor != null) {
            inferMethodCallArgumentTypes(argument, forCompletion, leftArgs, index, method, substitutor, array);
          }
        }
      }

      // try to find some variants without considering previous argument PRIMITIVE_TYPES
      if (forCompletion && array.isEmpty()) {
        for (CandidateInfo candidate : methodCandidates) {
          PsiMethod method = (PsiMethod)candidate.getElement();
          PsiSubstitutor substitutor = candidate.getSubstitutor();
          PsiParameter[] params = method.getParameterList().getParameters();
          if (params.length <= index) continue;
          PsiParameter param = params[index];
          PsiType paramType = getParameterType(param, substitutor);
          TailType tailType = getMethodArgumentTailType(argument, index, method, substitutor, params);
          ExpectedTypeInfoImpl info = createInfoImpl(paramType, ExpectedTypeInfo.TYPE_OR_SUBTYPE, paramType,
                                                     tailType, method, getPropertyName(param));
          array.add(info);
        }
      }

      return array.toArray(new ExpectedTypeInfo[array.size()]);
    }

    @NotNull
    private static TailType getMethodArgumentTailType(@NotNull final PsiExpression argument,
                                                      final int index,
                                                      @NotNull final PsiMethod method,
                                                      @NotNull final PsiSubstitutor substitutor,
                                                      @NotNull final PsiParameter[] params) {
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
      return TailType.COMMA;
    }

    private static void inferMethodCallArgumentTypes(@NotNull final PsiExpression argument,
                                                     final boolean forCompletion,
                                                     @NotNull final PsiExpression[] args,
                                                     final int index,
                                                     @NotNull final PsiMethod method,
                                                     @NotNull final PsiSubstitutor substitutor,
                                                     @NotNull final Set<ExpectedTypeInfo> array) {
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
                                          @NotNull final PsiExpression argument, @NotNull PsiExpression[] args, int index) {
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) return parameterType;

      PsiType hardcoded = HardcodedDefaultTypesKt.getDefaultType(method, substitutor, index, argument);
      if (hardcoded != null) return hardcoded;

      @NonNls final String name = method.getName();
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(containingClass.getProject());
      int argCount = Math.max(index + 1, args.length);
      if ("assertEquals".equals(name) || "assertSame".equals(name) && method.getParameterList().getParametersCount() == argCount) {
        if (argCount == 2 ||
            argCount == 3 && method.getParameterList().getParameters()[0].getType().equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
          int other = index == argCount - 1 ? index - 1 : index + 1;
          if (args.length > other) {
            ExpectedTypeInfo info = getEqualsType(args[other]);
            if (info != null && parameterType.isAssignableFrom(info.getDefaultType())) {
              return info.getDefaultType();
            }
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
      PsiType parameterType = substitutor.substitute(type);
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

    @NotNull
    private ExpectedTypeInfo[] findClassesWithDeclaredMethod(@NotNull final PsiMethodCallExpression methodCallExpr, final boolean forCompletion) {
      PsiUtilCore.ensureValid(methodCallExpr);
      final PsiReferenceExpression reference = methodCallExpr.getMethodExpression();
      if (reference.getQualifierExpression() instanceof PsiClassObjectAccessExpression) {
        return ExpectedTypeInfo.EMPTY_ARRAY;
      }
      final PsiManager manager = methodCallExpr.getManager();
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());
      final PsiMethod[] methods = myClassProvider.findDeclaredMethods(manager, reference.getReferenceName());
      Set<ExpectedTypeInfo> types = new THashSet<>();
      for (PsiMethod method : methods) {
        final PsiClass aClass = method.getContainingClass();
        if (aClass == null || !facade.getResolveHelper().isAccessible(method, reference, aClass)) continue;

        final PsiSubstitutor substitutor = ExpectedTypeUtil.inferSubstitutor(method, methodCallExpr, forCompletion);
        final PsiClassType type =
          substitutor == null ? facade.getElementFactory().createType(aClass) : facade.getElementFactory().createType(aClass, substitutor);

        if (method.hasModifierProperty(PsiModifier.STATIC) || method.hasModifierProperty(PsiModifier.PRIVATE)) {
          types.add(createInfoImpl(type, ExpectedTypeInfo.TYPE_STRICTLY, type, TailType.DOT));
        } else if (method.findSuperMethods().length == 0) {
          types.add(createInfoImpl(type, ExpectedTypeInfo.TYPE_OR_SUBTYPE, type, TailType.DOT));
        }
      }

      return types.toArray(new ExpectedTypeInfo[types.size()]);
    }

    @NotNull
    private ExpectedTypeInfo[] findClassesWithDeclaredField(@NotNull PsiReferenceExpression expression) {
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(expression.getProject());
      PsiField[] fields = myClassProvider.findDeclaredFields(expression.getManager(), expression.getReferenceName());
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
      return types.toArray(new ExpectedTypeInfo[types.size()]);
    }
  }

  /**
   * Finds fields and methods of specified name whenever corresponding reference has been encountered.
   * By default searches in the global scope (see ourGlobalScopeClassProvider), but caller can provide its own algorithm e.g. to narrow search scope
   */
  public interface ExpectedClassProvider {
    @NotNull
    PsiField[] findDeclaredFields(@NotNull PsiManager manager, @NotNull String name);

    @NotNull
    PsiMethod[] findDeclaredMethods(@NotNull PsiManager manager, @NotNull String name);
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

}
