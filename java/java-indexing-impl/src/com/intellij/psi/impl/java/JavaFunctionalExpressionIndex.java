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

package com.intellij.psi.impl.java;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.impl.java.stubs.FunctionalExpressionKey;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.JavaFileElementType;
import com.intellij.psi.impl.source.JavaLightTreeUtil;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.LightTreeUtil;
import com.intellij.psi.impl.source.tree.RecursiveLighterASTNodeWalkingVisitor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.KeyDescriptor;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.psi.impl.source.tree.JavaElementType.*;

public class JavaFunctionalExpressionIndex extends FileBasedIndexExtension<FunctionalExpressionKey, TIntArrayList> implements PsiDependentIndex {
  public static final ID<FunctionalExpressionKey, TIntArrayList> INDEX_ID = ID.create("java.fun.expression");
  private static final KeyDescriptor<FunctionalExpressionKey> KEY_DESCRIPTOR = new KeyDescriptor<FunctionalExpressionKey>() {
    @Override
    public int getHashCode(FunctionalExpressionKey value) {
      return value.hashCode();
    }

    @Override
    public boolean isEqual(FunctionalExpressionKey val1, FunctionalExpressionKey val2) {
      return val1.equals(val2);
    }

    @Override
    public void save(@NotNull DataOutput out, FunctionalExpressionKey value) throws IOException {
      value.serializeKey(out);
    }

    @Override
    public FunctionalExpressionKey read(@NotNull DataInput in) throws IOException {
      return FunctionalExpressionKey.deserializeKey(in);
    }
  };

  @NotNull
  private static FunctionalExpressionKey.Location calcLocation(LighterAST tree, LighterASTNode funExpr) {
    LighterASTNode call = getContainingCall(tree, funExpr);
    List<LighterASTNode> args = JavaLightTreeUtil.getArgList(tree, call);
    int argIndex = args == null ? -1 : getArgIndex(args, funExpr);
    String methodName = call == null ? null : getCalledMethodName(tree, call);
    return methodName == null || argIndex < 0
           ? createTypedLocation(tree, funExpr)
           : new FunctionalExpressionKey.CallLocation(methodName, args.size(), argIndex, StreamApiDetector.isStreamApiCall(tree, call));
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
      String typeText = JavaLightTreeUtil.getNameIdentifierText(tree, LightTreeUtil.firstChildOfType(tree, typeElement, JAVA_CODE_REFERENCE));
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

  private static FunctionalExpressionKey.CoarseType calcType(final LighterAST tree, LighterASTNode funExpr) {
    if (funExpr.getTokenType() == METHOD_REF_EXPRESSION) return FunctionalExpressionKey.CoarseType.UNKNOWN;

    LighterASTNode block = LightTreeUtil.firstChildOfType(tree, funExpr, CODE_BLOCK);
    if (block == null) {
      LighterASTNode expr = findExpressionChild(funExpr, tree);
      return isBooleanExpression(tree, expr) ? FunctionalExpressionKey.CoarseType.BOOLEAN : FunctionalExpressionKey.CoarseType.UNKNOWN;
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
      return FunctionalExpressionKey.CoarseType.BOOLEAN;
    }

    if (returnsSomething.isNull()) {
      return hasStatements.get() ? FunctionalExpressionKey.CoarseType.VOID : FunctionalExpressionKey.CoarseType.UNKNOWN;
    }

    return returnsSomething.get() ? FunctionalExpressionKey.CoarseType.NON_VOID : FunctionalExpressionKey.CoarseType.VOID;
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
      return ref == null ? null : JavaLightTreeUtil.getNameIdentifierText(tree, ref);
    }

    LighterASTNode methodExpr = tree.getChildren(call).get(0);
    if (LightTreeUtil.firstChildOfType(tree, methodExpr, JavaTokenType.SUPER_KEYWORD) != null) {
      return getSuperClassName(tree, call);
    }
    if (LightTreeUtil.firstChildOfType(tree, methodExpr, JavaTokenType.THIS_KEYWORD) != null) {
      return JavaLightTreeUtil.getNameIdentifierText(tree, findClass(tree, call));
    }

    return JavaLightTreeUtil.getNameIdentifierText(tree, methodExpr);
  }

  @Nullable
  private static String getSuperClassName(LighterAST tree, LighterASTNode call) {
    LighterASTNode aClass = findClass(tree, call);
    LighterASTNode extendsList = LightTreeUtil.firstChildOfType(tree, aClass, EXTENDS_LIST);
    return JavaLightTreeUtil.getNameIdentifierText(tree, LightTreeUtil.firstChildOfType(tree, extendsList, JAVA_CODE_REFERENCE));
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

  @NotNull
  @Override
  public KeyDescriptor<FunctionalExpressionKey> getKeyDescriptor() {
    return KEY_DESCRIPTOR;
  }

  @Override
  public int getVersion() {
    return 1;
  }

  @NotNull
  @Override
  public ID<FunctionalExpressionKey, TIntArrayList> getName() {
    return INDEX_ID;
  }

  @NotNull
  @Override
  public DataIndexer<FunctionalExpressionKey, TIntArrayList, FileContent> getIndexer() {
    return inputData -> {
      Map<FunctionalExpressionKey, TIntArrayList> result = new HashMap<>();

      LighterAST tree = ((FileContentImpl)inputData).getLighterASTForPsiDependentIndex();
      new RecursiveLighterASTNodeWalkingVisitor(tree) {
        @Override
        public void visitNode(@NotNull LighterASTNode element) {
          if (element.getTokenType() == METHOD_REF_EXPRESSION ||
              element.getTokenType() == LAMBDA_EXPRESSION) {
            FunctionalExpressionKey key = new FunctionalExpressionKey(getFunExprParameterCount(tree, element),
                                                                      calcType(tree, element),
                                                                      calcLocation(tree, element));
            TIntArrayList list = result.get(key);
            if (list == null) {
              result.put(key, list = new TIntArrayList());
            }
            list.add(element.getStartOffset());
          }

          super.visitNode(element);
        }
      }.visitNode(tree.getRoot());

      return result;
    };
  }

  @NotNull
  @Override
  public DataExternalizer<TIntArrayList> getValueExternalizer() {
    return new DataExternalizer<TIntArrayList>() {
      @Override
      public void save(@NotNull DataOutput out, TIntArrayList value) throws IOException {
        DataInputOutputUtil.writeINT(out, value.size());
        for (int i = 0; i < value.size(); i++) {
          DataInputOutputUtil.writeINT(out, value.get(i));
        }
      }

      @Override
      public TIntArrayList read(@NotNull DataInput in) throws IOException {
        int length = DataInputOutputUtil.readINT(in);
        TIntArrayList list = new TIntArrayList(length);
        for (int i = 0; i < length; i++) {
          list.add(DataInputOutputUtil.readINT(in));
        }
        return list;
      }
    };
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return new DefaultFileTypeSpecificInputFilter(JavaFileType.INSTANCE) {
      @Override
      public boolean acceptInput(@NotNull VirtualFile file) {
        return super.acceptInput(file) && JavaFileElementType.isInSourceContent(file);
      }
    };
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }
}