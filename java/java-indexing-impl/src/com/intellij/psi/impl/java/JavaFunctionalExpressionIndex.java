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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.impl.java.stubs.FunctionalExpressionKey;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.FileLocalResolver;
import com.intellij.psi.impl.source.JavaFileElementType;
import com.intellij.psi.impl.source.JavaLightTreeUtil;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.LightTreeUtil;
import com.intellij.psi.impl.source.tree.RecursiveLighterASTNodeWalkingVisitor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.text.StringSearcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.psi.impl.source.tree.JavaElementType.*;

public class JavaFunctionalExpressionIndex extends FileBasedIndexExtension<FunctionalExpressionKey, Map<Integer, FunExprOccurrence>> {
  public static final ID<FunctionalExpressionKey, Map<Integer, FunExprOccurrence>> INDEX_ID = ID.create("java.fun.expression");
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
  private static List<ReferenceChainLink> createCallChain(FileLocalResolver resolver, @Nullable LighterASTNode expr) {
    List<ReferenceChainLink> chain = new ArrayList<>();
    while (true) {
      if (expr == null) return reversedChain(chain);
      if (expr.getTokenType() == PARENTH_EXPRESSION) {
        expr = LightTreeUtil.firstChildOfType(resolver.getLightTree(), expr, ElementType.EXPRESSION_BIT_SET);
        continue;
      }
      if (expr.getTokenType() == TYPE_CAST_EXPRESSION) {
        String typeName = resolver.getShortClassTypeName(expr);
        ContainerUtil.addIfNotNull(chain, typeName != null ? new ReferenceChainLink(typeName, false, -1) : null);
        return reversedChain(chain);
      }

      boolean isCall = expr.getTokenType() == METHOD_CALL_EXPRESSION || expr.getTokenType() == NEW_EXPRESSION;
      String referenceName = getReferencedMemberName(resolver.getLightTree(), expr, isCall);
      if (referenceName == null) return reversedChain(chain);

      LighterASTNode qualifier = getQualifier(resolver.getLightTree(), expr, isCall);
      if (qualifier == null) {
        ContainerUtil.addIfNotNull(chain, createChainStart(resolver, expr, isCall, referenceName));
        return reversedChain(chain);
      }

      chain.add(new ReferenceChainLink(referenceName, isCall, getArgCount(resolver.getLightTree(), expr)));
      expr = qualifier;
    }
  }

  @NotNull
  private static List<ReferenceChainLink> reversedChain(List<ReferenceChainLink> chain) {
    Collections.reverse(chain);
    return chain;
  }

  private static int getArgCount(LighterAST tree, LighterASTNode expr) {
    List<LighterASTNode> args = JavaLightTreeUtil.getArgList(tree, expr);
    return args == null ? -1 : args.size();
  }

  @Nullable
  private static LighterASTNode getQualifier(LighterAST tree, LighterASTNode expr, boolean isCall) {
    LighterASTNode qualifier = tree.getChildren(expr).get(0);
    if (isCall) {
      List<LighterASTNode> children = tree.getChildren(qualifier);
      qualifier = children.isEmpty() ? null : children.get(0);
    }
    return qualifier != null && ElementType.EXPRESSION_BIT_SET.contains(qualifier.getTokenType()) ? qualifier : null;
  }

  @Nullable
  private static String getReferencedMemberName(LighterAST tree, LighterASTNode expr, boolean isCall) {
    if (isCall) {
      return getCalledMethodName(tree, expr);
    }
    if (expr.getTokenType() == REFERENCE_EXPRESSION) {
      return JavaLightTreeUtil.getNameIdentifierText(tree, expr);
    }
    return null;
  }

  @Nullable
  private static ReferenceChainLink createChainStart(FileLocalResolver resolver,
                                                     LighterASTNode expr,
                                                     boolean isCall,
                                                     String referenceName) {
    if (!isCall) {
      FileLocalResolver.LightResolveResult result = resolver.resolveLocally(expr);
      if (result == FileLocalResolver.LightResolveResult.UNKNOWN) return null;

      LighterASTNode target = result.getTarget();
      if (target != null) {
        String typeName = resolver.getShortClassTypeName(target);
        return typeName != null ? new ReferenceChainLink(typeName, false, -1) : null;
      }
    }
    return new ReferenceChainLink(referenceName, isCall, getArgCount(resolver.getLightTree(), expr));
  }

  @NotNull
  private static String calcExprType(LighterASTNode funExpr, FileLocalResolver resolver) {
    LighterAST tree = resolver.getLightTree();
    LighterASTNode scope = skipExpressionsUp(tree, funExpr, TokenSet.create(
      LOCAL_VARIABLE, FIELD, TYPE_CAST_EXPRESSION, RETURN_STATEMENT, ASSIGNMENT_EXPRESSION, ARRAY_INITIALIZER_EXPRESSION));
    int arrayDepth = 0;
    while (scope != null && scope.getTokenType() == ARRAY_INITIALIZER_EXPRESSION) {
      scope = tree.getParent(scope);
      // should be either new-expression or variable/field declaration
      arrayDepth++;
    }
    if (scope != null) {
      if (scope.getTokenType() == ASSIGNMENT_EXPRESSION) {
        LighterASTNode lValue = findExpressionChild(scope, tree);
        scope = lValue == null ? null : resolver.resolveLocally(lValue).getTarget();
      }
      else if (scope.getTokenType() == RETURN_STATEMENT) {
        scope = LightTreeUtil.getParentOfType(tree, scope,
                                              TokenSet.create(METHOD),
                                              TokenSet.orSet(ElementType.MEMBER_BIT_SET, TokenSet.create(LAMBDA_EXPRESSION)));
      }
      else if (scope.getTokenType() == NEW_EXPRESSION) {
        assert arrayDepth > 0;
        if (arrayDepth != LightTreeUtil.getChildrenOfType(tree, scope, JavaTokenType.LBRACKET).size()) return "";
        LighterASTNode typeRef = LightTreeUtil.firstChildOfType(tree, scope, JAVA_CODE_REFERENCE);
        String refName = JavaLightTreeUtil.getNameIdentifierText(tree, typeRef);
        return StringUtil.notNullize(refName);
      }
    }
    return StringUtil.notNullize(scope == null ? null : resolver.getShortClassTypeName(scope, arrayDepth));
  }

  private static int getArgIndex(List<? extends LighterASTNode> args, LighterASTNode expr) {
    for (int i = 0; i < args.size(); i++) {
      if (args.get(i).getEndOffset() >= expr.getEndOffset()) {
        return i;
      }
    }
    return -1;
  }

  private static FunctionalExpressionKey.CoarseType calcReturnType(final LighterAST tree, LighterASTNode funExpr) {
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

  @Nullable
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
    return getReferenceName(tree, LightTreeUtil.firstChildOfType(tree, aClass, EXTENDS_LIST));
  }

  @Nullable
  private static String getReferenceName(LighterAST tree, LighterASTNode refParent) {
    return JavaLightTreeUtil.getNameIdentifierText(tree, LightTreeUtil.firstChildOfType(tree, refParent, JAVA_CODE_REFERENCE));
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
    return JBIterable.generate(node, tree::getParent).find(n -> n.getTokenType() == CLASS);
  }

  @NotNull
  @Override
  public KeyDescriptor<FunctionalExpressionKey> getKeyDescriptor() {
    return KEY_DESCRIPTOR;
  }

  @Override
  public int getVersion() {
    return 4;
  }

  @NotNull
  @Override
  public ID<FunctionalExpressionKey, Map<Integer, FunExprOccurrence>> getName() {
    return INDEX_ID;
  }

  @Override
  public boolean hasSnapshotMapping() {
    return true;
  }

  @NotNull
  @Override
  public DataIndexer<FunctionalExpressionKey, Map<Integer, FunExprOccurrence>, FileContent> getIndexer() {
    return inputData -> {
      CharSequence text = inputData.getContentAsText();
      int[] offsets = ArrayUtil.mergeArrays(
        new StringSearcher("->", true, true).findAllOccurrences(text),
        new StringSearcher("::", true, true).findAllOccurrences(text));
      if (offsets.length == 0) return Collections.emptyMap();

      Map<FunctionalExpressionKey, Map<Integer, FunExprOccurrence>> result = new HashMap<>();
      LighterAST tree = ((PsiDependentFileContent)inputData).getLighterAST();
      FileLocalResolver resolver = new FileLocalResolver(tree);

      LightTreeUtil.processLeavesAtOffsets(offsets, tree, (leaf, offset) -> {
        LighterASTNode element = tree.getParent(leaf);
        if (element == null) return;

        if (element.getTokenType() == METHOD_REF_EXPRESSION || element.getTokenType() == LAMBDA_EXPRESSION) {
          FunctionalExpressionKey key = new FunctionalExpressionKey(getFunExprParameterCount(tree, element),
                                                                    calcReturnType(tree, element),
                                                                    calcExprType(element, resolver));
          Map<Integer, FunExprOccurrence> map = result.computeIfAbsent(key, __ -> new LinkedHashMap<>());
          map.put(element.getStartOffset(), createOccurrence(element, resolver));
        }
      });

      return result;
    };
  }

  @NotNull
  private static FunExprOccurrence createOccurrence(@NotNull LighterASTNode funExpr, FileLocalResolver resolver) {
    LighterAST tree = resolver.getLightTree();
    LighterASTNode containingCall = getContainingCall(tree, funExpr);
    List<LighterASTNode> args = JavaLightTreeUtil.getArgList(tree, containingCall);
    int argIndex = args == null ? -1 : getArgIndex(args, funExpr);

    LighterASTNode chainExpr = containingCall;
    if (chainExpr == null) {
      LighterASTNode assignment = skipExpressionsUp(tree, funExpr, TokenSet.create(ASSIGNMENT_EXPRESSION));
      if (assignment != null) {
        chainExpr = findExpressionChild(assignment, tree);
      }
    }

    return new FunExprOccurrence(argIndex, createCallChain(resolver, chainExpr));
  }

  @NotNull
  @Override
  public DataExternalizer<Map<Integer, FunExprOccurrence>> getValueExternalizer() {
    return new DataExternalizer<Map<Integer, FunExprOccurrence>>() {
      @Override
      public void save(@NotNull DataOutput out, Map<Integer, FunExprOccurrence> value) throws IOException {
        DataInputOutputUtil.writeINT(out, value.size());
        for (Map.Entry<Integer, FunExprOccurrence> entry : value.entrySet()) {
          DataInputOutputUtil.writeINT(out, entry.getKey());
          entry.getValue().serialize(out);
        }
      }

      @Override
      public Map<Integer, FunExprOccurrence> read(@NotNull DataInput in) throws IOException {
        int length = DataInputOutputUtil.readINT(in);
        Map<Integer, FunExprOccurrence> map = new LinkedHashMap<>();
        for (int i = 0; i < length; i++) {
          int offset = DataInputOutputUtil.readINT(in);
          map.put(offset, FunExprOccurrence.deserialize(in));
        }
        return map;
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