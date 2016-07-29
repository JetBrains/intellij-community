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
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiFunctionalExpression;
import com.intellij.psi.impl.cache.RecordUtil;
import com.intellij.psi.impl.java.stubs.FunctionalExpressionKey.CoarseType;
import com.intellij.psi.impl.java.stubs.index.JavaStubIndexKeys;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.LightTreeUtil;
import com.intellij.psi.impl.source.tree.RecursiveLighterASTNodeWalkingVisitor;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.psi.impl.source.tree.JavaElementType.*;

public abstract class FunctionalExpressionElementType<T extends PsiFunctionalExpression> extends JavaStubElementType<FunctionalExpressionStub<T>,T> {
  public FunctionalExpressionElementType(String debugName) {
    super(debugName);
  }

  @Override
  public void serialize(@NotNull FunctionalExpressionStub<T> stub, @NotNull StubOutputStream dataStream) throws IOException {
    stub.getIndexKey().serializeKey(dataStream);
  }

  @NotNull
  @Override
  public FunctionalExpressionStub<T> deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    return new FunctionalExpressionStub<T>(parentStub, this, FunctionalExpressionKey.deserializeKey(dataStream));
  }

  @Override
  public void indexStub(@NotNull FunctionalExpressionStub<T> stub, @NotNull IndexSink sink) {
    sink.occurrence(JavaStubIndexKeys.FUNCTIONAL_EXPRESSIONS, stub.getIndexKey());
  }

  @Override
  public FunctionalExpressionStub<T> createStub(LighterAST tree, LighterASTNode funExpr, StubElement parentStub) {
    return new FunctionalExpressionStub<T>(parentStub, this,
                                           new FunctionalExpressionKey(getFunExprParameterCount(tree, funExpr),
                                                                       calcType(tree, funExpr),
                                                                       calcLocation(tree, funExpr)));
  }

  @NotNull
  private static FunctionalExpressionKey.Location calcLocation(LighterAST tree, LighterASTNode funExpr) {
    LighterASTNode call = getContainingCall(tree, funExpr);
    List<LighterASTNode> args = getArgList(tree, call);
    int argIndex = args == null ? -1 : getArgIndex(args, funExpr);
    String methodName = call == null ? null : getCalledMethodName(tree, call);
    return methodName == null || argIndex < 0
           ? createTypedLocation(tree, funExpr)
           : new FunctionalExpressionKey.CallLocation(methodName, args.size(), argIndex);
  }

  @NotNull
  private static FunctionalExpressionKey.Location createTypedLocation(LighterAST tree, LighterASTNode funExpr) {
    LighterASTNode scope = skipExpressionsUp(tree, funExpr, TokenSet.create(LOCAL_VARIABLE, FIELD, TYPE_CAST_EXPRESSION, RETURN_STATEMENT));
    if (scope != null) {
      if (scope.getTokenType() == RETURN_STATEMENT) {
        scope = LightTreeUtil.getParentOfType(tree, scope,
                                              TokenSet.create(METHOD),
                                              TokenSet.orSet(ElementType.MEMBER_BIT_SET, TokenSet.create(LAMBDA_EXPRESSION)));
      }

      LighterASTNode typeElement = LightTreeUtil.firstChildOfType(tree, scope, TYPE);
      String typeText = getNameIdentifierText(tree, LightTreeUtil.firstChildOfType(tree, typeElement, JAVA_CODE_REFERENCE));
      if (typeText != null) {
        return new FunctionalExpressionKey.TypedLocation(typeText);
      }
    }
    return FunctionalExpressionKey.Location.UNKNOWN;
  }

  private static int getArgIndex(List<LighterASTNode> args, LighterASTNode expr) {
    for (int i = 0; i < args.size(); i++) {
      if (args.get(i).getEndOffset() >= expr.getEndOffset()) {
        return i;
      }
    }
    return -1;
  }

  @Nullable
  private static List<LighterASTNode> getArgList(LighterAST tree, LighterASTNode call) {
    LighterASTNode anonClass = LightTreeUtil.firstChildOfType(tree, call, ANONYMOUS_CLASS);
    LighterASTNode exprList = LightTreeUtil.firstChildOfType(tree, anonClass != null ? anonClass : call, EXPRESSION_LIST);
    return exprList == null ? null : LightTreeUtil.getChildrenOfType(tree, exprList, ElementType.EXPRESSION_BIT_SET);
  }

  private static CoarseType calcType(final LighterAST tree, LighterASTNode funExpr) {
    if (funExpr.getTokenType() == METHOD_REF_EXPRESSION) return CoarseType.UNKNOWN;

    LighterASTNode block = LightTreeUtil.firstChildOfType(tree, funExpr, CODE_BLOCK);
    if (block == null) {
      LighterASTNode expr = findExpressionChild(funExpr, tree);
      return isBooleanExpression(tree, expr) ? CoarseType.BOOLEAN : CoarseType.UNKNOWN;
    }

    final Ref<Boolean> returnsSomething = Ref.create(null);
    final AtomicBoolean isBoolean = new AtomicBoolean();
    final AtomicBoolean hasStatements = new AtomicBoolean();
    new RecursiveLighterASTNodeWalkingVisitor(tree) {
      @Override
      public void visitNode(@NotNull LighterASTNode element) {
        IElementType type = element.getTokenType();
        if (type == LAMBDA_EXPRESSION || ElementType.MEMBER_BIT_SET.contains(type)) {
          return;
        }

        if (type == RETURN_STATEMENT) {
          LighterASTNode expr = findExpressionChild(element, tree);
          returnsSomething.set(expr != null);
          if (isBooleanExpression(tree, expr)) {
            isBoolean.set(true);
          }
          return;
        }

        if (type == EXPRESSION_STATEMENT) {
          hasStatements.set(true);
        }

        super.visitNode(element);
      }
    }.visitNode(block);

    if (isBoolean.get()) {
      return CoarseType.BOOLEAN;
    }

    if (returnsSomething.isNull()) {
      return hasStatements.get() ? CoarseType.VOID : CoarseType.UNKNOWN;
    }

    return returnsSomething.get() ? CoarseType.NON_VOID : CoarseType.VOID;
  }

  private static LighterASTNode findExpressionChild(@NotNull LighterASTNode element, LighterAST tree) {
    return LightTreeUtil.firstChildOfType(tree, element, ElementType.EXPRESSION_BIT_SET);
  }

  private static boolean isBooleanExpression(LighterAST tree, @Nullable LighterASTNode expr) {
    if (expr == null) return false;

    IElementType type = expr.getTokenType();
    if (type == LITERAL_EXPRESSION) {
      IElementType child = tree.getChildren(expr).get(0).getTokenType();
      return child == JavaTokenType.TRUE_KEYWORD || child == JavaTokenType.FALSE_KEYWORD;
    }
    if (type == POLYADIC_EXPRESSION || type == BINARY_EXPRESSION) {
      return LightTreeUtil.firstChildOfType(tree, expr, PsiBinaryExpression.BOOLEAN_OPERATION_TOKENS) != null;
    }
    if (type == PREFIX_EXPRESSION) {
      return tree.getChildren(expr).get(0).getTokenType() == JavaTokenType.EXCL;
    }
    if (type == PARENTH_EXPRESSION) {
      return isBooleanExpression(tree, findExpressionChild(expr, tree));
    }
    if (type == CONDITIONAL_EXPRESSION) {
      List<LighterASTNode> children = LightTreeUtil.getChildrenOfType(tree, expr, ElementType.EXPRESSION_BIT_SET);
      return children.size() == 3 && (isBooleanExpression(tree, children.get(1)) || isBooleanExpression(tree, children.get(2)));
    }
    return false;
  }

  private static int getFunExprParameterCount(LighterAST tree, LighterASTNode funExpr) {
    if (funExpr.getTokenType() == METHOD_REF_EXPRESSION) return FunctionalExpressionKey.UNKNOWN_PARAM_COUNT;

    assert funExpr.getTokenType() == LAMBDA_EXPRESSION;

    LighterASTNode paramList = LightTreeUtil.firstChildOfType(tree, funExpr, PARAMETER_LIST);
    assert paramList != null;
    return LightTreeUtil.getChildrenOfType(tree, paramList, Constants.PARAMETER_BIT_SET).size();
  }

  @Nullable
  private static String getCalledMethodName(LighterAST tree, LighterASTNode call) {
    if (call.getTokenType() == NEW_EXPRESSION) {
      LighterASTNode anonClass = LightTreeUtil.firstChildOfType(tree, call, ANONYMOUS_CLASS);
      LighterASTNode ref = LightTreeUtil.firstChildOfType(tree, anonClass != null ? anonClass : call, JAVA_CODE_REFERENCE);
      return ref == null ? null : getNameIdentifierText(tree, ref);
    }

    LighterASTNode methodExpr = tree.getChildren(call).get(0);
    if (LightTreeUtil.firstChildOfType(tree, methodExpr, JavaTokenType.SUPER_KEYWORD) != null) {
      return getSuperClassName(tree, call);
    }
    if (LightTreeUtil.firstChildOfType(tree, methodExpr, JavaTokenType.THIS_KEYWORD) != null) {
      return getNameIdentifierText(tree, findClass(tree, call));
    }

    return getNameIdentifierText(tree, methodExpr);
  }

  @Nullable
  private static String getSuperClassName(LighterAST tree, LighterASTNode call) {
    LighterASTNode aClass = findClass(tree, call);
    LighterASTNode extendsList = LightTreeUtil.firstChildOfType(tree, aClass, EXTENDS_LIST);
    return getNameIdentifierText(tree, LightTreeUtil.firstChildOfType(tree, extendsList, JAVA_CODE_REFERENCE));
  }

  @Nullable
  private static String getNameIdentifierText(LighterAST tree, LighterASTNode idOwner) {
    LighterASTNode id = LightTreeUtil.firstChildOfType(tree, idOwner, JavaTokenType.IDENTIFIER);
    return id != null ? RecordUtil.intern(tree.getCharTable(), id) : null;
  }

  @Nullable
  private static LighterASTNode getContainingCall(LighterAST tree, LighterASTNode node) {
    LighterASTNode expressionList = skipExpressionsUp(tree, node, TokenSet.create(EXPRESSION_LIST));
    if (expressionList != null) {
      LighterASTNode parent = tree.getParent(expressionList);
      if (parent != null && parent.getTokenType() == ANONYMOUS_CLASS) {
        parent = tree.getParent(parent);
      }
      if (parent != null && (parent.getTokenType() == METHOD_CALL_EXPRESSION || parent.getTokenType() == NEW_EXPRESSION)) {
        return parent;
      }
    }
    return null;
  }

  private static LighterASTNode skipExpressionsUp(LighterAST tree, @NotNull LighterASTNode node, TokenSet types) {
    node = tree.getParent(node);
    while (node != null) {
      final IElementType type = node.getTokenType();
      if (types.contains(type)) return node;
      if (type != PARENTH_EXPRESSION && type != CONDITIONAL_EXPRESSION) return null;
      node = tree.getParent(node);
    }
    return null;
  }

  private static LighterASTNode findClass(LighterAST tree, LighterASTNode node) {
    while (node != null) {
      final IElementType type = node.getTokenType();
      if (type == CLASS) return node;
      node = tree.getParent(node);
    }
    return null;
  }
}
