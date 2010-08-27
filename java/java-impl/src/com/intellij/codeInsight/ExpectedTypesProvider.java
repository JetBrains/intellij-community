/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.source.jsp.jspJava.JspMethodCall;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.search.searches.DeepestSuperMethodsSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.HashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author ven
 */
public class ExpectedTypesProvider {
  private static final ExpectedTypeInfo VOID_EXPECTED = new ExpectedTypeInfoImpl(PsiType.VOID, ExpectedTypeInfo.TYPE_OR_SUBTYPE, 0, PsiType.VOID,
                                                                                 TailType.SEMICOLON);

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.ExpectedTypesProvider");
  public static ExpectedTypesProvider getInstance(Project project) {
    return ServiceManager.getService(project, ExpectedTypesProvider.class);
  }

  private static final ExpectedClassProvider ourGlobalScopeClassProvider = new ExpectedClassProvider() {
    public PsiField[] findDeclaredFields(final PsiManager manager, String name) {
      final PsiShortNamesCache cache = JavaPsiFacade.getInstance(manager.getProject()).getShortNamesCache();
      GlobalSearchScope scope = GlobalSearchScope.allScope(manager.getProject());
      return cache.getFieldsByName(name, scope);
    }

    public PsiMethod[] findDeclaredMethods(final PsiManager manager, String name) {
      final PsiShortNamesCache cache = JavaPsiFacade.getInstance(manager.getProject()).getShortNamesCache();
      GlobalSearchScope scope = GlobalSearchScope.allScope(manager.getProject());
      return cache.getMethodsByName(name, scope);
    }
  };
  private static final PsiType[] PRIMITIVE_TYPES = {PsiType.BYTE, PsiType.CHAR, PsiType.SHORT, PsiType.INT, PsiType.LONG, PsiType.FLOAT, PsiType.DOUBLE};

  public static ExpectedTypeInfo createInfo(@NotNull  PsiType type, int kind, PsiType defaultType, TailType tailType) {
    return createInfoImpl(type, kind, defaultType, tailType);
  }

  private static ExpectedTypeInfoImpl createInfoImpl(@NotNull PsiType type, int kind, PsiType defaultType, TailType tailType) {
    int dims = 0;
    while (type instanceof PsiArrayType) {
      type = ((PsiArrayType) type).getComponentType();
      LOG.assertTrue(defaultType instanceof PsiArrayType);
      defaultType = ((PsiArrayType) defaultType).getComponentType();
      dims++;
    }
    return new ExpectedTypeInfoImpl(type, kind, dims, defaultType, tailType);
  }

  public static ExpectedTypeInfo[] getExpectedTypes(PsiExpression expr, boolean forCompletion) {
    return getExpectedTypes(expr, forCompletion, false, false);
  }

  public static ExpectedTypeInfo[] getExpectedTypes(PsiExpression expr, boolean forCompletion, final boolean voidable, boolean usedAfter) {
    return getExpectedTypes(expr, forCompletion, ourGlobalScopeClassProvider, voidable, usedAfter);
  }

  public static ExpectedTypeInfo[] getExpectedTypes(PsiExpression expr,
                                                    boolean forCompletion,
                                                    ExpectedClassProvider classProvider, boolean usedAfter) {
    return getExpectedTypes(expr, forCompletion, classProvider, false, usedAfter);
  }

  public static ExpectedTypeInfo[] getExpectedTypes(PsiExpression expr, boolean forCompletion, ExpectedClassProvider classProvider,
                                                    final boolean voidable, boolean usedAfter) {
    if (expr == null) return null;
    PsiElement parent = expr.getParent();
    while (parent instanceof PsiParenthesizedExpression) {
      expr = (PsiExpression)parent;
      parent = parent.getParent();
    }
    MyParentVisitor visitor = new MyParentVisitor(expr, forCompletion, classProvider, voidable, usedAfter);
    parent.accept(visitor);
    return visitor.getResult();
  }

  public static PsiType[] processExpectedTypes(ExpectedTypeInfo[] infos,
                                        final PsiTypeVisitor<PsiType> visitor, Project project) {
    Set<PsiType> set = new LinkedHashSet<PsiType>();
    for (ExpectedTypeInfo info : infos) {
      ExpectedTypeInfoImpl infoImpl = (ExpectedTypeInfoImpl)info;

      if (infoImpl.getDefaultType() instanceof PsiClassType && infoImpl.getDimCount() == 0) {
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

      if (infoImpl.kind == ExpectedTypeInfo.TYPE_OR_SUPERTYPE) {
        processAllSuperTypes(infoImpl.getType(), infoImpl.getDimCount(), visitor, project, set);
      }
      else if (infoImpl.getKind() == ExpectedTypeInfo.TYPE_OR_SUBTYPE) {
        if (infoImpl.getType() instanceof PsiPrimitiveType && infoImpl.getDimCount() == 0) {
          processPrimitiveTypeAndSubtypes((PsiPrimitiveType)infoImpl.getType(), visitor, set);
        } //else too expensive to search
      }
    }

    return set.toArray(new PsiType[set.size()]);
  }

  private static void processType(@NotNull PsiType type, PsiTypeVisitor<PsiType> visitor, Set<PsiType> typeSet) {
    PsiType accepted = type.accept(visitor);
    if (accepted != null) typeSet.add(accepted);
  }

  public static void processPrimitiveTypeAndSubtypes(PsiPrimitiveType type, PsiTypeVisitor<PsiType> visitor, Set<PsiType> set) {
    if (type.equals(PsiType.BOOLEAN) || type.equals(PsiType.VOID) || type.equals(PsiType.NULL)) return;

    for (int i = 0; ; i++) {
      final PsiType primitive = PRIMITIVE_TYPES[i];
      processType(primitive, visitor, set);
      if (primitive.equals(type)) return;
    }
  }

  public static void processAllSuperTypes(PsiType type, int dimCount, PsiTypeVisitor<PsiType> visitor, Project project, Set<PsiType> set) {
    if (type instanceof PsiPrimitiveType) {
      if (type.equals(PsiType.BOOLEAN) || type.equals(PsiType.VOID) || type.equals(PsiType.NULL)) return;

      Stack<PsiType> stack = new Stack<PsiType>();
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
        PsiType[] superTypes = type.getSuperTypes();
        for (PsiType superType : superTypes) {
          PsiType wrappedType = superType;
          for (int j = 0; j < dimCount; j++) {
            wrappedType = wrappedType.createArrayType();
          }
          processType(wrappedType, visitor, set);
          processAllSuperTypes(superType, dimCount, visitor, project, set);
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
    private ExpectedTypeInfo[] myResult = ExpectedTypeInfo.EMPTY_ARRAY;
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

    public ExpectedTypeInfo[] getResult() {
      return myResult;
    }

    @Override
    public void visitAnnotationMethod(final PsiAnnotationMethod method) {
      if (myExpr == method.getDefaultValue()) {
        final PsiType type = method.getReturnType();
        if (type != null) {
          myResult = new ExpectedTypeInfo[]{createInfoImpl(type, ExpectedTypeInfo.TYPE_OR_SUBTYPE, type, TailType.SEMICOLON)};
        }
      }
    }

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      if (myForCompletion) {
        final MyParentVisitor visitor = new MyParentVisitor(expression, myForCompletion, myClassProvider, myVoidable, myUsedAfter);
        expression.getParent().accept(visitor);
        myResult = visitor.getResult();
        return;
      }

      String referenceName = expression.getReferenceName();
      if (referenceName != null) {
        final PsiElement parent = expression.getParent();
        if (parent instanceof PsiMethodCallExpression) {
          myResult = findClassesWithDeclaredMethod((PsiMethodCallExpression)parent, myForCompletion);
        }
        else if (parent instanceof PsiReferenceExpression || parent instanceof PsiVariable ||
                 parent instanceof PsiExpression) {
          if (LENGTH_SYNTHETIC_ARRAY_FIELD.equals(referenceName)) {
            myResult = anyArrayType();
          }
          else {
            myResult = findClassesWithDeclaredField(expression);
          }
        }
      }
    }

    @Override
    public void visitExpressionStatement(PsiExpressionStatement statement) {
      if (myVoidable) {
        myResult = new ExpectedTypeInfo[]{VOID_EXPECTED};
      }
    }

    @Override public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      myExpr = (PsiExpression)myExpr.getParent();
      expression.getParent().accept(this);
    }

    @Override public void visitAnnotationArrayInitializer(PsiArrayInitializerMemberValue initializer) {
      final PsiType type = getAnnotationMethodType((PsiNameValuePair)initializer.getParent());
      if (type instanceof PsiArrayType) {
        myResult = new ExpectedTypeInfo[]{createInfoImpl(((PsiArrayType)type).getComponentType(), ExpectedTypeInfo.TYPE_OR_SUBTYPE, type, TailType.UNKNOWN)};
      }
    }

    @Override public void visitNameValuePair(PsiNameValuePair pair) {
      final PsiType type = getAnnotationMethodType(pair);
      if (type == null) return;
      final ExpectedTypeInfoImpl info = createInfoImpl(type, ExpectedTypeInfo.TYPE_OR_SUBTYPE, type, TailType.UNKNOWN);
      if (type instanceof PsiArrayType) {
        myResult = new ExpectedTypeInfo[]{info, createInfoImpl(((PsiArrayType)type).getComponentType(), ExpectedTypeInfo.TYPE_OR_SUBTYPE, type, TailType.UNKNOWN)};
      } else {
        myResult = new ExpectedTypeInfo[] {info};
      }
    }

    @Nullable
    private static PsiType getAnnotationMethodType(final PsiNameValuePair pair) {
      final PsiReference reference = pair.getReference();
      if (reference != null) {
        final PsiElement method = reference.resolve();
        if (method instanceof PsiMethod) {
          return ((PsiMethod)method).getReturnType();
        }
      }
      return null;
    }

    @Override public void visitReturnStatement(PsiReturnStatement statement) {
      PsiMethod scopeMethod = PsiTreeUtil.getParentOfType(statement, PsiMethod.class);
      if (scopeMethod != null) {
        PsiType type = scopeMethod.getReturnType();
        if (type != null) {
          ExpectedTypeInfoImpl info = createInfoImpl(type, ExpectedTypeInfo.TYPE_OR_SUBTYPE, type,
                                                     TailType.SEMICOLON);
          if (PropertyUtil.isSimplePropertyAccessor(scopeMethod)) {
            info.expectedName = PropertyUtil.getPropertyName(scopeMethod);
          }

          myResult = new ExpectedTypeInfo[]{info};
        }
        else {
          myResult = ExpectedTypeInfo.EMPTY_ARRAY;
        }
      }
    }

    @Override public void visitIfStatement(PsiIfStatement statement) {
      ExpectedTypeInfoImpl info = createInfoImpl(PsiType.BOOLEAN, ExpectedTypeInfo.TYPE_STRICTLY,
                                                 PsiType.BOOLEAN, TailTypes.IF_RPARENTH);
      myResult = new ExpectedTypeInfo[]{info};
    }

    @Override public void visitWhileStatement(PsiWhileStatement statement) {
      ExpectedTypeInfoImpl info = createInfoImpl(PsiType.BOOLEAN, ExpectedTypeInfo.TYPE_STRICTLY,
                                                 PsiType.BOOLEAN, TailTypes.WHILE_RPARENTH);
      myResult = new ExpectedTypeInfo[]{info};
    }

    @Override public void visitDoWhileStatement(PsiDoWhileStatement statement) {
      ExpectedTypeInfoImpl info = createInfoImpl(PsiType.BOOLEAN, ExpectedTypeInfo.TYPE_STRICTLY,
                                                 PsiType.BOOLEAN, TailTypes.WHILE_RPARENTH);
      myResult = new ExpectedTypeInfo[]{info};
    }

    @Override public void visitForStatement(PsiForStatement statement) {
      if (myExpr.equals(statement.getCondition())) {
        ExpectedTypeInfoImpl info = createInfoImpl(PsiType.BOOLEAN, ExpectedTypeInfo.TYPE_STRICTLY,
                                                   PsiType.BOOLEAN, TailType.SEMICOLON);
        myResult = new ExpectedTypeInfo[]{info};
      }
    }

    @Override
    public void visitAssertStatement(PsiAssertStatement statement) {
      if (statement.getAssertDescription() == myExpr) {
        final PsiClassType stringType = PsiType.getJavaLangString(myExpr.getManager(), myExpr.getResolveScope());
        ExpectedTypeInfoImpl info = createInfoImpl(stringType, ExpectedTypeInfo.TYPE_STRICTLY,
                                                   stringType, TailType.SEMICOLON);
        myResult = new ExpectedTypeInfo[]{info};
      } else {
        ExpectedTypeInfoImpl info = createInfoImpl(PsiType.BOOLEAN, ExpectedTypeInfo.TYPE_STRICTLY,
                                                   PsiType.BOOLEAN, TailType.SEMICOLON);
        myResult = new ExpectedTypeInfo[]{info};
      }
    }

    @Override public void visitForeachStatement(PsiForeachStatement statement) {
      if (myExpr.equals(statement.getIteratedValue())) {
        PsiType type = statement.getIterationParameter().getType();

        PsiType arrayType = type.createArrayType();
        ExpectedTypeInfoImpl info1 = createInfoImpl(arrayType, ExpectedTypeInfo.TYPE_OR_SUBTYPE,
                                                    arrayType, TailType.NONE);

        PsiManager manager = statement.getManager();
        PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
        PsiClass iterableClass =
          JavaPsiFacade.getInstance(manager.getProject()).findClass("java.lang.Iterable", statement.getResolveScope());
        if (iterableClass == null || iterableClass.getTypeParameters().length != 1) {
          myResult = new ExpectedTypeInfo[]{info1};
        } else {
          Map<PsiTypeParameter, PsiType> map = new HashMap<PsiTypeParameter, PsiType>();
          map.put(iterableClass.getTypeParameters()[0], PsiWildcardType.createExtends(manager, type));
          PsiSubstitutor substitutor = factory.createSubstitutor(map);
          PsiType iterableType = factory.createType(iterableClass, substitutor);
          ExpectedTypeInfoImpl info2 = createInfoImpl(iterableType, ExpectedTypeInfo.TYPE_OR_SUBTYPE,
                                                      iterableType, TailType.NONE);

          myResult = new ExpectedTypeInfo[]{info1, info2};
        }
      }
    }

    @Override public void visitSwitchStatement(PsiSwitchStatement statement) {
      ExpectedTypeInfoImpl info = createInfoImpl(PsiType.LONG, ExpectedTypeInfo.TYPE_OR_SUBTYPE, PsiType.INT,
                                                 TailType.NONE);
      if (!PsiUtil.isLanguageLevel5OrHigher(statement)) {
        myResult = new ExpectedTypeInfo[]{info};
        return;
      }

      PsiManager manager = statement.getManager();
      PsiClassType enumType = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createTypeByFQClassName("java.lang.Enum", statement.getResolveScope());
      ExpectedTypeInfoImpl enumInfo = createInfoImpl(enumType, ExpectedTypeInfo.TYPE_OR_SUBTYPE, enumType, TailType.NONE);
      myResult = new ExpectedTypeInfo[] {info, enumInfo};
    }

    @Override
    public void visitSwitchLabelStatement(final PsiSwitchLabelStatement statement) {
      final PsiSwitchStatement switchStatement = statement.getEnclosingSwitchStatement();
      if (switchStatement != null) {
        final PsiExpression expression = switchStatement.getExpression();
        if (expression != null) {
          final PsiType type = expression.getType();
          if (type != null) {
            myResult = new ExpectedTypeInfo[]{createInfoImpl(type, ExpectedTypeInfo.TYPE_OR_SUBTYPE, type, TailType.CASE_COLON)};
          }
        }
      }
    }

    @Override public void visitSynchronizedStatement(PsiSynchronizedStatement statement) {
      PsiElementFactory factory = JavaPsiFacade.getInstance(statement.getProject()).getElementFactory();
      PsiType objectType = factory.createTypeByFQClassName("java.lang.Object", myExpr.getResolveScope());
      myResult = new ExpectedTypeInfo[]{createInfoImpl(objectType, ExpectedTypeInfo.TYPE_OR_SUBTYPE, objectType, TailType.NONE)};
    }

    @Override public void visitVariable(PsiVariable variable) {
      PsiType type = variable.getType();
      ExpectedTypeInfoImpl info = createInfoImpl(type, ExpectedTypeInfo.TYPE_OR_SUBTYPE, type,
                                                 TailType.SEMICOLON);
      info.expectedName = getPropertyName(variable);
      myResult = new ExpectedTypeInfo[]{info};
    }

    @Override public void visitAssignmentExpression(PsiAssignmentExpression assignment) {
      if (myExpr.equals(assignment.getRExpression())) {
        PsiExpression lExpr = assignment.getLExpression();
        PsiType type = lExpr.getType();
        if (type != null) {
          TailType tailType = getAssignmentRValueTailType(assignment);
          ExpectedTypeInfoImpl info = createInfoImpl(type, ExpectedTypeInfo.TYPE_OR_SUBTYPE, type, tailType);
          if (lExpr instanceof PsiReferenceExpression) {
            PsiElement refElement = ((PsiReferenceExpression)lExpr).resolve();
            if (refElement instanceof PsiVariable) {
              info.expectedName = getPropertyName((PsiVariable)refElement);
            }
          }
          myResult = new ExpectedTypeInfo[]{info};
        }
        else {
          myResult = ExpectedTypeInfo.EMPTY_ARRAY;
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
          if (type != null) {
            if (type instanceof PsiClassType) {
              final PsiClass resolved = ((PsiClassType)type).resolve();
              if (resolved instanceof PsiAnonymousClass) {
                type = ((PsiAnonymousClass)resolved).getBaseClassType();
              }
            }
            final int kind = assignment.getOperationSign().getTokenType() != JavaTokenType.EQ
                             ? ExpectedTypeInfo.TYPE_STRICTLY
                             : ExpectedTypeInfo.TYPE_OR_SUPERTYPE;
            ExpectedTypeInfoImpl info = createInfoImpl(type, kind, type, TailType.NONE);
            myResult = new ExpectedTypeInfo[]{info};
            return;
          }
        }
        myResult = ExpectedTypeInfo.EMPTY_ARRAY;
      }
    }

    private static TailType getAssignmentRValueTailType(PsiAssignmentExpression assignment) {
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

    @Override public void visitExpressionList(PsiExpressionList list) {
      PsiResolveHelper helper = JavaPsiFacade.getInstance(list.getProject()).getResolveHelper();
      if (list.getParent() instanceof PsiMethodCallExpression) {
        PsiMethodCallExpression methodCall = (PsiMethodCallExpression)list.getParent();
        CandidateInfo[] candidates = helper.getReferencedMethodCandidates(methodCall, false);
        myResult = getExpectedArgumentTypesForMethodCall(candidates, list, myExpr, myForCompletion);
      }
      else if (list.getParent() instanceof PsiEnumConstant) {
        getExpectedArgumentsTypesForEnumConstant((PsiEnumConstant)list.getParent(), helper, list);
      }
      else if (list.getParent() instanceof PsiNewExpression) {
        getExpectedArgumentsTypesForNewExpression((PsiNewExpression)list.getParent(), helper, list);
      }
      else if (list.getParent() instanceof PsiAnonymousClass) {
        getExpectedArgumentsTypesForNewExpression((PsiNewExpression)list.getParent().getParent(), helper, list);
      }
    }

    private void getExpectedArgumentsTypesForEnumConstant(final PsiEnumConstant enumConstant,
                                                          final PsiResolveHelper helper,
                                                          final PsiExpressionList list) {
      final PsiClass aClass = enumConstant.getContainingClass();
      if (aClass != null) {
        LOG.assertTrue(aClass.isEnum());
        getExpectedTypesForConstructorCall(aClass, helper, list, PsiSubstitutor.EMPTY);
      }
    }

    private void getExpectedArgumentsTypesForNewExpression(final PsiNewExpression newExpr, final PsiResolveHelper helper, final PsiExpressionList list) {
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
        getExpectedTypesForConstructorCall(newClass, helper, list, substitutor);
      }
    }

    private void getExpectedTypesForConstructorCall(final PsiClass referencedClass,
                                                    final PsiResolveHelper helper,
                                                    final PsiExpressionList argumentList,
                                                    final PsiSubstitutor substitutor) {
      List<CandidateInfo> array = new ArrayList<CandidateInfo>();
      PsiMethod[] constructors = referencedClass.getConstructors();
      for (PsiMethod constructor : constructors) {
        if (helper.isAccessible(constructor, argumentList, null)) {
          array.add(new MethodCandidateInfo(constructor, substitutor, false, false, argumentList, null, argumentList.getExpressionTypes(), null));
        }
      }
      CandidateInfo[] candidates = array.toArray(new CandidateInfo[array.size()]);
      myResult = getExpectedArgumentTypesForMethodCall(candidates, argumentList, myExpr, myForCompletion);
    }

    @Override public void visitBinaryExpression(PsiBinaryExpression expr) {

      PsiExpression op1 = expr.getLOperand();
      PsiExpression op2 = expr.getROperand();
      PsiJavaToken sign = expr.getOperationSign();
      if (myForCompletion && op1.equals(myExpr)) {
        final MyParentVisitor visitor = new MyParentVisitor(expr, myForCompletion, myClassProvider, myVoidable, myUsedAfter);
        myExpr = (PsiExpression)myExpr.getParent();
        expr.getParent().accept(visitor);
        myResult = visitor.getResult();
        if (!(expr.getParent() instanceof PsiExpressionList)) {
          for (final ExpectedTypeInfo info : myResult) {
            ((ExpectedTypeInfoImpl)info).myTailType = TailType.NONE;
          }
        }
        return;
      }
      PsiExpression anotherExpr = op1.equals(myExpr) ? op2 : op1;
      PsiType anotherType = anotherExpr != null ? anotherExpr.getType() : null;
      PsiElementFactory factory = JavaPsiFacade.getInstance(expr.getProject()).getElementFactory();
      IElementType i = sign.getTokenType();
      if (i == JavaTokenType.MINUS ||
          i == JavaTokenType.ASTERISK ||
          i == JavaTokenType.DIV ||
          i == JavaTokenType.PERC ||
          i == JavaTokenType.LT ||
          i == JavaTokenType.GT ||
          i == JavaTokenType.LE ||
          i == JavaTokenType.GE) {
        if (anotherType == null) {
          myResult = ExpectedTypeInfo.EMPTY_ARRAY;
        }
        else {
          ExpectedTypeInfoImpl info = createInfoImpl(PsiType.DOUBLE, ExpectedTypeInfo.TYPE_OR_SUBTYPE,
                                                     anotherType, TailType.NONE);
          myResult = new ExpectedTypeInfo[]{info};
        }
      }
      else if (i == JavaTokenType.PLUS) {
        if (anotherType == null) {
          myResult = ExpectedTypeInfo.EMPTY_ARRAY;
        }
        else {
          if (anotherType.equalsToText("java.lang.String")) {
            PsiType objType = PsiType.getJavaLangObject(myExpr.getManager(), myExpr.getResolveScope());
            ExpectedTypeInfo info = createInfoImpl(objType, ExpectedTypeInfo.TYPE_OR_SUBTYPE, anotherType,
                                                   TailType.NONE);
            ExpectedTypeInfo info1 = createInfoImpl(PsiType.DOUBLE, ExpectedTypeInfo.TYPE_OR_SUBTYPE,
                                                    PsiType.INT, TailType.NONE);
            PsiType booleanType = PsiType.BOOLEAN;
            ExpectedTypeInfo info2 = createInfoImpl(booleanType, ExpectedTypeInfo.TYPE_STRICTLY, booleanType,
                                                    TailType.NONE);
            myResult = new ExpectedTypeInfo[]{info, info1, info2};
          }
          else {
            if (PsiType.DOUBLE.isAssignableFrom(anotherType)) {
              ExpectedTypeInfoImpl info = createInfoImpl(PsiType.DOUBLE, ExpectedTypeInfo.TYPE_OR_SUBTYPE,
                                                         anotherType, TailType.NONE);
              myResult = new ExpectedTypeInfo[]{info};
            }
          }
        }
      }
      else if (i == JavaTokenType.EQEQ || i == JavaTokenType.NE) {
        if (anotherType == null) {
          myResult = ExpectedTypeInfo.EMPTY_ARRAY;
        }
        else {
          ExpectedTypeInfoImpl info;
          if (anotherType instanceof PsiPrimitiveType) {
            if (PsiType.BOOLEAN.equals(anotherType)) {
              info = createInfoImpl(anotherType, ExpectedTypeInfo.TYPE_STRICTLY, anotherType, TailType.NONE);
            }
            else if (PsiType.NULL.equals(anotherType)) {
              PsiType objectType = factory.createTypeByFQClassName("java.lang.Object", myExpr.getResolveScope());
              info = createInfoImpl(objectType, ExpectedTypeInfo.TYPE_OR_SUBTYPE, objectType, TailType.NONE);
            }
            else {
              info = createInfoImpl(PsiType.DOUBLE, ExpectedTypeInfo.TYPE_OR_SUBTYPE, anotherType, TailType.NONE);
            }
          }
          else {
            info = createInfoImpl(anotherType, ExpectedTypeInfo.TYPE_STRICTLY, anotherType, TailType.NONE);
          }

          if (anotherExpr instanceof PsiReferenceExpression) {
            PsiElement refElement = ((PsiReferenceExpression)anotherExpr).resolve();
            if (refElement instanceof PsiVariable) {
              info.expectedName = getPropertyName((PsiVariable)refElement);
            }
          }

          myResult = new ExpectedTypeInfo[]{info};
        }
      }
      else if (i == JavaTokenType.LTLT || i == JavaTokenType.GTGT || i == JavaTokenType.GTGTGT) {
        if (anotherType == null) {
          myResult = ExpectedTypeInfo.EMPTY_ARRAY;
        }
        else {
          myResult = new ExpectedTypeInfo[]{createInfoImpl(PsiType.LONG, ExpectedTypeInfo.TYPE_BETWEEN, PsiType.SHORT, TailType.NONE)};
        }
      }
      else if (i == JavaTokenType.OROR || i == JavaTokenType.ANDAND) {
        ExpectedTypeInfoImpl info = createInfoImpl(PsiType.BOOLEAN, ExpectedTypeInfo.TYPE_STRICTLY,
                                                   PsiType.BOOLEAN, TailType.NONE);
        myResult = new ExpectedTypeInfo[]{info};
      }
      else if (i == JavaTokenType.OR || i == JavaTokenType.XOR || i == JavaTokenType.AND) {
        if (anotherType == null) {
          myResult = ExpectedTypeInfo.EMPTY_ARRAY;
        }
        else {
          ExpectedTypeInfoImpl info;
          if (PsiType.BOOLEAN.equals(anotherType)) {
            info = createInfoImpl(anotherType, ExpectedTypeInfo.TYPE_STRICTLY, anotherType, TailType.NONE);
          }
          else {
            info = createInfoImpl(PsiType.LONG, ExpectedTypeInfo.TYPE_OR_SUBTYPE, anotherType, TailType.NONE);
          }
          myResult = new ExpectedTypeInfo[]{info};
        }
      }
    }

    @Override public void visitPrefixExpression(PsiPrefixExpression expr) {
      PsiJavaToken sign = expr.getOperationSign();
      IElementType i = sign.getTokenType();
      final PsiType type = expr.getType();
      final TailType tailType = expr.getParent() instanceof PsiAssignmentExpression && ((PsiAssignmentExpression) expr.getParent()).getRExpression() == expr ?
                                getAssignmentRValueTailType((PsiAssignmentExpression) expr.getParent()) :
                                TailType.NONE;
      if (i == JavaTokenType.PLUSPLUS || i == JavaTokenType.MINUSMINUS || i == JavaTokenType.TILDE) {
        ExpectedTypeInfoImpl info;
        if (myUsedAfter && type != null) {
          info = createInfoImpl(type, ExpectedTypeInfo.TYPE_STRICTLY, type, tailType);
        }
        else {
          if (type != null) {
            info = createInfoImpl(type, ExpectedTypeInfo.TYPE_OR_SUPERTYPE, PsiType.INT, tailType);
          }
          else {
            info = createInfoImpl(PsiType.LONG, ExpectedTypeInfo.TYPE_OR_SUBTYPE, PsiType.INT, tailType);
          }
        }
        myResult = new ExpectedTypeInfo[]{info};
      }
      else if (i == JavaTokenType.PLUS || i == JavaTokenType.MINUS) {
        ExpectedTypeInfoImpl info = createInfoImpl(PsiType.DOUBLE, ExpectedTypeInfo.TYPE_OR_SUBTYPE,
                                                   PsiType.INT, tailType);
        myResult = new ExpectedTypeInfo[]{info};
      }
      else if (i == JavaTokenType.EXCL) {
        ExpectedTypeInfoImpl info = createInfoImpl(PsiType.BOOLEAN, ExpectedTypeInfo.TYPE_STRICTLY,
                                                   PsiType.BOOLEAN, tailType);
        myResult = new ExpectedTypeInfo[]{info};
      }
    }

    @Override public void visitPostfixExpression(PsiPostfixExpression expr) {
      if (myForCompletion) return;
      PsiType type = expr.getType();
      ExpectedTypeInfoImpl info;
      if (myUsedAfter && type != null) {
        info = createInfoImpl(type, ExpectedTypeInfo.TYPE_STRICTLY, type, TailType.NONE);
      }
      else {
        if (type != null) {
          info = createInfoImpl(type, ExpectedTypeInfo.TYPE_OR_SUPERTYPE, PsiType.INT, TailType.NONE);
        }
        else {
          info = createInfoImpl(PsiType.LONG, ExpectedTypeInfo.TYPE_OR_SUBTYPE, PsiType.INT, TailType.NONE);
        }
      }
      myResult = new ExpectedTypeInfo[]{info};
    }

    @Override public void visitArrayInitializerExpression(PsiArrayInitializerExpression expr) {
      PsiElement pparent = expr.getParent();
      PsiType arrayType = null;
      if (pparent instanceof PsiVariable) {
        arrayType = ((PsiVariable)pparent).getType();
      }
      else if (pparent instanceof PsiNewExpression) {
        arrayType = ((PsiNewExpression)pparent).getType();
      }
      else if (pparent instanceof PsiArrayInitializerExpression) {
        PsiType type = ((PsiArrayInitializerExpression)pparent).getType();
        if (type instanceof PsiArrayType) {
          arrayType = ((PsiArrayType)type).getComponentType();
        }
      }

      if (arrayType instanceof PsiArrayType) {
        PsiType componentType = ((PsiArrayType)arrayType).getComponentType();
        ExpectedTypeInfoImpl info = createInfoImpl(componentType, ExpectedTypeInfo.TYPE_OR_SUBTYPE,
                                                   componentType, TailType.NONE);
        myResult = new ExpectedTypeInfo[]{info};
      }
    }

    @Override public void visitNewExpression(PsiNewExpression expression) {
      PsiExpression[] arrayDimensions = expression.getArrayDimensions();
      for (PsiExpression dimension : arrayDimensions) {
        if (myExpr.equals(dimension)) {
          ExpectedTypeInfoImpl info = createInfoImpl(PsiType.INT, ExpectedTypeInfo.TYPE_OR_SUBTYPE,
                                                     PsiType.INT, TailType.NONE);
          myResult = new ExpectedTypeInfo[]{info};
          return;
        }
      }
    }

    @Override public void visitArrayAccessExpression(PsiArrayAccessExpression expr) {
      if (myExpr.equals(expr.getIndexExpression())) {
        ExpectedTypeInfoImpl info = createInfoImpl(PsiType.INT, ExpectedTypeInfo.TYPE_OR_SUBTYPE, PsiType.INT, TailType.NONE)
            ; //todo: special tail type
        myResult = new ExpectedTypeInfo[]{info};
      }
      else if (myExpr.equals(expr.getArrayExpression())) {
        if (myForCompletion) {
          myExpr = (PsiExpression)myExpr.getParent();
          expr.getParent().accept(this);
          return;
        }

        PsiElement parent = expr.getParent();
        MyParentVisitor visitor = new MyParentVisitor(expr, myForCompletion, myClassProvider, myVoidable, myUsedAfter);
        myExpr = (PsiExpression)myExpr.getParent();
        parent.accept(visitor);
        ExpectedTypeInfo[] componentTypeInfo = visitor.getResult();
        if (componentTypeInfo.length == 0) {
          myResult = anyArrayType();
        }
        else {
          myResult = new ExpectedTypeInfoImpl[componentTypeInfo.length];
          for (int i = 0; i < componentTypeInfo.length; i++) {
            ExpectedTypeInfo compInfo = componentTypeInfo[i];
            PsiType expectedArrayType = compInfo.getType().createArrayType();
            myResult[i] = createInfoImpl(expectedArrayType, ExpectedTypeInfo.TYPE_OR_SUBTYPE, expectedArrayType, TailType.NONE);
          }
        }
      }
    }

    @Override public void visitConditionalExpression(PsiConditionalExpression expr) {
      if (myExpr.equals(expr.getCondition())) {
        if (myForCompletion) {
          myExpr = expr;
          myExpr.getParent().accept(this);
          return;
        }

        ExpectedTypeInfo info = createInfoImpl(PsiType.BOOLEAN, ExpectedTypeInfo.TYPE_STRICTLY,
                                               PsiType.BOOLEAN, TailType.NONE);
        myResult = new ExpectedTypeInfo[]{info};
      }
      else if (myExpr.equals(expr.getThenExpression())) {
        ExpectedTypeInfo[] types = getExpectedTypes(expr, myForCompletion);
        if (types != null) {
          for (ExpectedTypeInfo info : types) {
            ExpectedTypeInfoImpl infoImpl = (ExpectedTypeInfoImpl)info;
            infoImpl.setInsertExplicitTypeParams(true);
            infoImpl.myTailType = TailType.COND_EXPR_COLON;
          }
        }
        myResult = types;
      }
      else {
        LOG.assertTrue(myExpr.equals(expr.getElseExpression()));
        myResult = getExpectedTypes(expr, myForCompletion);
        if (myResult != null) {
          for (ExpectedTypeInfo info : myResult) {
            ((ExpectedTypeInfoImpl)info).setInsertExplicitTypeParams(true);
          }
        }

      }
    }

    @Override public void visitThrowStatement(PsiThrowStatement statement) {
      if (statement.getException() == myExpr) {
        PsiManager manager = statement.getManager();
        PsiType throwableType = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createTypeByFQClassName("java.lang.Throwable", myExpr.getResolveScope());
        PsiMember container = PsiTreeUtil.getParentOfType(statement, PsiMethod.class, PsiClass.class);
        PsiType[] throwsTypes = PsiType.EMPTY_ARRAY;
        if (container instanceof PsiMethod) {
          throwsTypes = ((PsiMethod)container).getThrowsList().getReferencedTypes();
        }

        if (throwsTypes.length == 0) {
          final PsiClassType exceptionType = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createTypeByFQClassName("java.lang.Exception", myExpr.getResolveScope());
          throwsTypes = new PsiClassType[]{exceptionType};
        }

        ExpectedTypeInfo[] infos = new ExpectedTypeInfo[throwsTypes.length];
        for (int i = 0; i < infos.length; i++) {
          infos[i] = createInfoImpl(
            myExpr instanceof PsiTypeCastExpression && myForCompletion ?
            throwsTypes[i] :
            throwableType,
            ExpectedTypeInfo.TYPE_OR_SUBTYPE,
            throwsTypes[i],
            TailType.SEMICOLON
          );
        }
        myResult = infos;
      }
    }

    @Override public void visitCodeFragment(JavaCodeFragment codeFragment) {
      if (codeFragment instanceof PsiExpressionCodeFragment) {
        final PsiType type = ((PsiExpressionCodeFragment)codeFragment).getExpectedType();
        if (type != null) {
          myResult = new ExpectedTypeInfo[] {createInfoImpl(type, ExpectedTypeInfo.TYPE_OR_SUBTYPE, type, TailType.NONE)};
        }
      }
    }

    private ExpectedTypeInfo[] getExpectedArgumentTypesForMethodCall(CandidateInfo[] methodCandidates,
                                                                     PsiExpressionList argumentList,
                                                                     PsiExpression argument,
                                                                     boolean forCompletion) {
      if (methodCandidates.length == 0) {
        return ExpectedTypeInfo.EMPTY_ARRAY;
      }
      final PsiExpression[] args = argumentList.getExpressions();
      final int index = ArrayUtil.indexOf(args, argument);
      LOG.assertTrue(index >= 0);

      final PsiExpression[] leftArgs;
      if (index <= args.length - 1) {
        leftArgs = new PsiExpression[index];
        System.arraycopy(args, 0, leftArgs, 0, index);
      } else {
        leftArgs = null;
      }

      Set<ExpectedTypeInfo> array = new LinkedHashSet<ExpectedTypeInfo>();
      for (CandidateInfo candidateInfo : methodCandidates) {
        PsiMethod method = (PsiMethod)candidateInfo.getElement();
        PsiSubstitutor substitutor;
        if (candidateInfo instanceof MethodCandidateInfo) {
          final MethodCandidateInfo info = (MethodCandidateInfo)candidateInfo;
          substitutor = info.inferTypeArguments(forCompletion);
          if (!info.isStaticsScopeCorrect() && method != null && !method.hasModifierProperty(PsiModifier.STATIC)) continue;
        }
        else {
          substitutor = candidateInfo.getSubstitutor();
        }
        inferMethodCallArgumentTypes(argument, forCompletion, args, index, method, substitutor, array);

        if (leftArgs != null && candidateInfo instanceof MethodCandidateInfo) {
          substitutor = ((MethodCandidateInfo)candidateInfo).inferTypeArguments(forCompletion, leftArgs);
          inferMethodCallArgumentTypes(argument, forCompletion, leftArgs, index, method, substitutor, array);
        }
      }

      // try to find some variants without considering previous argument PRIMITIVE_TYPES
      if (forCompletion && array.isEmpty()) {
        for (CandidateInfo candidate : methodCandidates) {
          PsiMethod method = (PsiMethod)candidate.getElement();
          PsiSubstitutor substitutor = candidate.getSubstitutor();
          PsiParameter[] parms = method.getParameterList().getParameters();
          if (parms.length <= index) continue;
          PsiParameter parm = parms[index];
          PsiType parmType = getParameterType(parm, substitutor);
          TailType tailType = getMethodArgumentTailType(argument, index, method, substitutor, parms);
          ExpectedTypeInfoImpl info = createInfoImpl(parmType, ExpectedTypeInfo.TYPE_OR_SUBTYPE, parmType,
                                                     tailType);
          info.expectedName = getPropertyName(parm);
          info.setCalledMethod(method);
          array.add(info);
        }
      }

      return array.toArray(new ExpectedTypeInfo[array.size()]);
    }

    private static TailType getMethodArgumentTailType(final PsiExpression argument, final int index, final PsiMethod method, final PsiSubstitutor substitutor,
                                               final PsiParameter[] parms) {
      if (index >= parms.length) {
        return TailType.NONE;
      }
      if (index == parms.length - 1) {
        //myTailType = CompletionUtil.NONE_TAIL;
        final PsiElement call = argument.getParent().getParent();
        if (call instanceof JspMethodCall) return TailType.NONE;

        PsiType returnType = method.getReturnType();
        if (returnType != null) returnType = substitutor.substitute(returnType);
        return getFinalCallParameterTailType(call, returnType, method);
      }
      return TailType.COMMA;
    }

    private void inferMethodCallArgumentTypes(final PsiExpression argument,
                                              final boolean forCompletion,
                                              final PsiExpression[] args,
                                              final int index,
                                              final PsiMethod method, final PsiSubstitutor substitutor, final Set<ExpectedTypeInfo> array) {
      PsiParameter[] parameters = method.getParameterList().getParameters();
      if (!forCompletion && parameters.length != args.length) return;
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
      PsiType defaultType = getDefautType(method, substitutor, parameterType, argument);

      ExpectedTypeInfoImpl info = createInfoImpl(parameterType, ExpectedTypeInfo.TYPE_OR_SUBTYPE, defaultType, tailType);
      info.setInsertExplicitTypeParams(true);
      info.setCalledMethod(method);
      String propertyName = getPropertyName(parameter);
      if (propertyName != null) info.expectedName = propertyName;
      array.add(info);

      if (index == parameters.length - 1 && parameter.isVarArgs()) {
        //Then we may still want to call with array argument
        final PsiArrayType arrayType = parameterType.createArrayType();
        ExpectedTypeInfoImpl info1 = createInfoImpl(arrayType, ExpectedTypeInfo.TYPE_OR_SUBTYPE, arrayType, tailType);
        info1.setInsertExplicitTypeParams(true);
        info1.setCalledMethod(method);
        info1.expectedName = propertyName;
        array.add(info1);
      }
    }

    @Nullable
    private static PsiType getTypeParameterValue(PsiClass rootClass, PsiClass derivedClass, PsiSubstitutor substitutor, int index) {
      final PsiTypeParameter[] typeParameters = rootClass.getTypeParameters();
      if (typeParameters.length > index) {
        final PsiSubstitutor psiSubstitutor = TypeConversionUtil.getClassSubstitutor(rootClass, derivedClass, substitutor);
        if (psiSubstitutor != null) {
          PsiType type = psiSubstitutor.substitute(typeParameters[index]);
          if (type != null) return type;
        }
      }
      return null;
    }

    @Nullable
    protected static PsiType checkMethod(PsiMethod method, @NonNls String className, NullableFunction<PsiClass,PsiType> function) {
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) return null;

      if (className.equals(containingClass.getQualifiedName())) {
        return function.fun(containingClass);
      }
      for (final PsiMethod psiMethod : DeepestSuperMethodsSearch.search(method).findAll()) {
        final PsiClass rootClass = psiMethod.getContainingClass();
        if (className.equals(rootClass.getQualifiedName())) {
          return function.fun(rootClass);
        }
      }
      return null;
    }

    @Nullable
    private PsiType getDefautType(final PsiMethod method, final PsiSubstitutor substitutor, final PsiType parameterType,
                                  final PsiExpression argumentList) {
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) return parameterType;

      @NonNls final String name = method.getName();
      if ("contains".equals(name) || "remove".equals(name)) {
        final PsiType type = checkMethod(method, CommonClassNames.JAVA_UTIL_COLLECTION, new NullableFunction<PsiClass, PsiType>() {
          public PsiType fun(final PsiClass psiClass) {
            return getTypeParameterValue(psiClass, containingClass, substitutor, 0);
          }
        });
        if (type != null) return type;
      }
      if ("containsKey".equals(name) || "remove".equals(name) || "get".equals(name) || "containsValue".equals(name)) {
        final PsiType type = checkMethod(method, CommonClassNames.JAVA_UTIL_MAP, new NullableFunction<PsiClass, PsiType>() {
          public PsiType fun(final PsiClass psiClass) {
            return getTypeParameterValue(psiClass, containingClass, substitutor, name.equals("containsValue") ? 1 : 0);
          }
        });
        if (type != null) return type;
      }
      if ("equals".equals(name)) {
        final PsiType type = checkMethod(method, CommonClassNames.JAVA_LANG_OBJECT, new NullableFunction<PsiClass, PsiType>() {
          public PsiType fun(final PsiClass psiClass) {
            final PsiElement parent = argumentList.getParent().getParent();
            if (parent instanceof PsiMethodCallExpression) {
              final PsiMethodCallExpression expression = (PsiMethodCallExpression)parent;
              final PsiExpression qualifierExpression = expression.getMethodExpression().getQualifierExpression();
              if (qualifierExpression != null) {
                return qualifierExpression.getType();
              }
              final PsiClass aClass = PsiTreeUtil.getContextOfType(parent, PsiClass.class, true);
              if (aClass != null) {
                return JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory().createType(aClass);
              }
            }
            return null;
          }
        });
        if (type != null) return type;
      }
      return parameterType;
    }

    private PsiType getParameterType(PsiParameter parameter, PsiSubstitutor substitutor) {
      PsiType type = parameter.getType();
      if (parameter.isVarArgs()) {
        type = ((PsiArrayType)type).getComponentType();
      }
      PsiType parameterType = substitutor.substitute(type);
      if (parameterType instanceof PsiCapturedWildcardType) {
        parameterType = ((PsiCapturedWildcardType)parameterType).getWildcard();
      }
      if (parameterType instanceof PsiWildcardType) {
        final PsiWildcardType psiWildcardType = (PsiWildcardType)parameterType;
        if (psiWildcardType.isExtends()) {
          final PsiType superBound = psiWildcardType.getBound();
          if (superBound != null) {
            parameterType = superBound;
          }
        }
      }
      return parameterType;
    }

    @Nullable
    private String getPropertyName(PsiVariable variable) {
      final String name = variable.getName();
      if (name == null) return null;
      JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(variable.getProject());
      VariableKind variableKind = codeStyleManager.getVariableKind(variable);
      return codeStyleManager.variableNameToPropertyName(name, variableKind);
    }

    private void addBaseType(Set<ExpectedTypeInfo> types, PsiClassType type, PsiMethod method) {
      PsiType[] supers = type.getSuperTypes();
      boolean addedSuper = false;
      for (PsiType aSuper : supers) {
        PsiClassType superType = (PsiClassType)aSuper;
        PsiClass superClass = superType.resolve();
        if (superClass != null) {
          if (superClass.findMethodBySignature(method, false) != null) {
            addBaseType(types, superType, method);
            addedSuper = true;
          }
        }
      }
      if (!addedSuper) {
        types.add(createInfoImpl(type, ExpectedTypeInfo.TYPE_OR_SUBTYPE, type, TailType.DOT));
      }
    }

    private ExpectedTypeInfo[] anyArrayType() {
      PsiType objType = PsiType.getJavaLangObject(myExpr.getManager(), myExpr.getResolveScope()).createArrayType();
      ExpectedTypeInfo info = createInfoImpl(objType, ExpectedTypeInfo.TYPE_OR_SUBTYPE, objType,
                                             TailType.NONE);
      ExpectedTypeInfo info1 = createInfoImpl(PsiType.DOUBLE.createArrayType(), ExpectedTypeInfo.TYPE_OR_SUBTYPE,
                                              PsiType.INT.createArrayType(), TailType.NONE);
      PsiType booleanType = PsiType.BOOLEAN.createArrayType();
      ExpectedTypeInfo info2 = createInfoImpl(booleanType, ExpectedTypeInfo.TYPE_STRICTLY, booleanType,
                                              TailType.NONE);
      return new ExpectedTypeInfo[]{info, info1, info2};
    }

    private ExpectedTypeInfo[] findClassesWithDeclaredMethod(final PsiMethodCallExpression methodCallExpr, final boolean forCompletion) {
      final PsiReferenceExpression reference = methodCallExpr.getMethodExpression();
      final PsiManager manager = methodCallExpr.getManager();
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());
      final PsiMethod[] methods = myClassProvider.findDeclaredMethods(reference.getManager(), reference.getReferenceName());
      Set<ExpectedTypeInfo> types = new THashSet<ExpectedTypeInfo>();
      for (PsiMethod method : methods) {
        final PsiClass aClass = method.getContainingClass();
        if (aClass == null || !facade.getResolveHelper().isAccessible(method, reference, aClass)) continue;

        final PsiSubstitutor substitutor = ExpectedTypeUtil.inferSubstitutor(method, methodCallExpr, forCompletion);
        final PsiClassType type =
          substitutor == null ? facade.getElementFactory().createType(aClass) : facade.getElementFactory().createType(aClass, substitutor);

        if (method.hasModifierProperty(PsiModifier.STATIC) ||
            method.hasModifierProperty(PsiModifier.FINAL) ||
            method.hasModifierProperty(PsiModifier.PRIVATE)) {
          types.add(createInfoImpl(type, ExpectedTypeInfo.TYPE_STRICTLY, type, TailType.DOT));
        }
        else {
          addBaseType(types, type, method);
        }
      }

      return types.toArray(new ExpectedTypeInfo[types.size()]);
    }

    private ExpectedTypeInfo[] findClassesWithDeclaredField(PsiReferenceExpression expression) {
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(expression.getProject());
      PsiField[] fields = myClassProvider.findDeclaredFields(expression.getManager(), expression.getReferenceName());
      List<ExpectedTypeInfo> types = new ArrayList<ExpectedTypeInfo>();
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
   * By default searhes in the global scope (see ourGlobalScopeClassProvider), but caller can provide its own algorithm e.g. to narrow search scope
   */
  public interface ExpectedClassProvider {
    PsiField[] findDeclaredFields(final PsiManager manager, String name);

    PsiMethod[] findDeclaredMethods(final PsiManager manager, String name);
  }

  public static TailType getFinalCallParameterTailType(PsiElement call, PsiType returnType, PsiMethod method) {
    if (method.isConstructor() &&
        call instanceof PsiMethodCallExpression && ((PsiMethodCallExpression)call).getMethodExpression() instanceof PsiSuperExpression) {
      return TailTypes.CALL_RPARENTH_SEMICOLON;
    }

    final boolean chainable = !PsiType.VOID.equals(returnType) && returnType != null;

    final PsiElement parent = call.getParent();
    final boolean statementContext = parent instanceof PsiExpressionStatement || parent instanceof PsiVariable ||
                                     parent instanceof PsiCodeBlock || parent instanceof PsiThrowStatement;

    if (statementContext && !chainable) {
      return TailTypes.CALL_RPARENTH_SEMICOLON;
    }

    return TailTypes.CALL_RPARENTH;
  }

}
