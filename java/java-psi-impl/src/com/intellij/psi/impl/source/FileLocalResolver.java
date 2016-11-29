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
package com.intellij.psi.impl.source;

import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.LightTreeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

import static com.intellij.psi.impl.source.tree.JavaElementType.*;

/**
 * Resolves unambiguous Java identifiers inside a file (if it can), using {@link LighterAST}. Can be used during indexing.
 */
public class FileLocalResolver {
  private final LighterAST myTree;

  public FileLocalResolver(@NotNull LighterAST tree) {
    myTree = tree;
  }

  @NotNull
  public LighterAST getLightTree() {
    return myTree;
  }

  /**
   * @param ref reference node
   * @return a resolve result corresponding to a local variable, parameter or field that the given reference resolves to.
   */
  @NotNull
  public LightResolveResult resolveLocally(@NotNull LighterASTNode ref) {
    final String refName = JavaLightTreeUtil.getNameIdentifierText(myTree, ref);
    if (refName == null) return LightResolveResult.UNKNOWN;
    if (!canResolveToLocalVariable(myTree, ref)) return LightResolveResult.NON_LOCAL;

    boolean passedClass = false;
    LighterASTNode lastParent = ref;
    while (true) {
      LighterASTNode scope = myTree.getParent(lastParent);
      if (scope == null) return LightResolveResult.NON_LOCAL;

      for (LighterASTNode var : getDeclarations(scope, lastParent)) {
        if (refName.equals(JavaLightTreeUtil.getNameIdentifierText(myTree, var))) {
          if (passedClass) return var.getTokenType() == FIELD ? LightResolveResult.NON_LOCAL : LightResolveResult.UNKNOWN;
          return LightResolveResult.resolved(var);
        }
      }

      lastParent = scope;
      passedClass |= scope.getTokenType() == CLASS || scope.getTokenType() == ANONYMOUS_CLASS;
    }
  }

  private boolean canResolveToLocalVariable(@NotNull LighterAST tree, @NotNull LighterASTNode ref) {
    LighterASTNode parent = tree.getParent(ref);
    return parent != null && parent.getTokenType() != METHOD_CALL_EXPRESSION && !hasQualifier(ref);
  }

  private boolean hasQualifier(@NotNull LighterASTNode ref) {
    return LightTreeUtil.firstChildOfType(myTree, ref, ElementType.EXPRESSION_BIT_SET) != null;
  }

  @NotNull
  private Iterable<LighterASTNode> getDeclarations(LighterASTNode scope, @Nullable LighterASTNode lastParent) {
    IElementType type = scope.getTokenType();
    if (type == CODE_BLOCK) {
      return walkChildrenScopes(before(LightTreeUtil.getChildrenOfType(myTree, scope, DECLARATION_STATEMENT), lastParent));
    }
    if (type == DECLARATION_STATEMENT) {
      return before(LightTreeUtil.getChildrenOfType(myTree, scope, LOCAL_VARIABLE), lastParent);
    }
    if (type == FOR_STATEMENT) {
      return walkChildrenScopes(before(LightTreeUtil.getChildrenOfType(myTree, scope, ElementType.JAVA_STATEMENT_BIT_SET), lastParent));
    }
    if (type == FOREACH_STATEMENT || type == CATCH_SECTION) {
      return JBIterable.of(LightTreeUtil.firstChildOfType(myTree, scope, PARAMETER));
    }
    if (type == TRY_STATEMENT) {
      return walkChildrenScopes(before(LightTreeUtil.getChildrenOfType(myTree, scope, RESOURCE_LIST), lastParent));
    }
    if (type == RESOURCE_LIST) {
      return before(LightTreeUtil.getChildrenOfType(myTree, scope, RESOURCE_VARIABLE), lastParent);
    }
    if (type == CLASS) {
      return LightTreeUtil.getChildrenOfType(myTree, scope, FIELD);
    }
    if (type == LAMBDA_EXPRESSION || type == METHOD) {
      LighterASTNode paramList = LightTreeUtil.firstChildOfType(myTree, scope, PARAMETER_LIST);
      return paramList == null ? ContainerUtil.<LighterASTNode>emptyList() : LightTreeUtil.getChildrenOfType(myTree, paramList, PARAMETER);
    }
    return Collections.emptyList();
  }

  @NotNull
  private JBIterable<LighterASTNode> walkChildrenScopes(JBIterable<LighterASTNode> children) {
    return children.flatMap(new Function<LighterASTNode, Iterable<? extends LighterASTNode>>() {
      @Override
      public Iterable<? extends LighterASTNode> fun(LighterASTNode child) {
        return getDeclarations(child, null);
      }
    });
  }

  @NotNull
  private static JBIterable<LighterASTNode> before(List<LighterASTNode> children, @Nullable final LighterASTNode lastParent) {
    return JBIterable.from(children).filter(new Condition<LighterASTNode>() {
      @Override
      public boolean value(LighterASTNode node) {
        return lastParent == null || node.getStartOffset() < lastParent.getStartOffset();
      }
    });
  }

  /**
   * Determine the type name of the given variable. Can be used to later iterate over all classes with this name for lightweight checks
   * before loading AST and fully resolving the type.
   * @param var Variable node
   * @return the short name of the class corresponding to the type of the variable, or null if the variable is not of class type or
   * the type is generic
   */
  @Nullable
  public String getShortClassTypeName(@NotNull LighterASTNode var) {
    LighterASTNode typeRef = LightTreeUtil.firstChildOfType(myTree, LightTreeUtil.firstChildOfType(myTree, var, TYPE), JAVA_CODE_REFERENCE);
    String refName = JavaLightTreeUtil.getNameIdentifierText(myTree, typeRef);
    if (refName == null) return null;

    if (LightTreeUtil.firstChildOfType(myTree, typeRef, JAVA_CODE_REFERENCE) != null) return refName;
    if (isTypeParameter(refName, var)) return null;
    return refName;
  }

  private boolean isTypeParameter(String name, LighterASTNode place) {
    LighterASTNode scope = place;
    while (scope != null) {
      if (scope.getTokenType() == CLASS || scope.getTokenType() == METHOD) {
        if (hasOwnTypeParameter(name, scope)) {
          return true;
        }
        if (isStatic(scope)) {
          break;
        }
      }
      scope = myTree.getParent(scope);
    }
    return false;
  }

  private boolean hasOwnTypeParameter(String name, LighterASTNode member) {
    LighterASTNode typeParams = LightTreeUtil.firstChildOfType(myTree, member, TYPE_PARAMETER_LIST);
    if (typeParams != null) {
      for (LighterASTNode param : LightTreeUtil.getChildrenOfType(myTree, typeParams, TYPE_PARAMETER)) {
        if (name.equals(JavaLightTreeUtil.getNameIdentifierText(myTree, param))) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean isStatic(LighterASTNode scope) {
    LighterASTNode modList = LightTreeUtil.firstChildOfType(myTree, scope, MODIFIER_LIST);
    return modList != null && LightTreeUtil.firstChildOfType(myTree, modList, JavaTokenType.STATIC_KEYWORD) != null;
  }

  public static class LightResolveResult {
    /** We know nothing about the resolve result */
    public static final LightResolveResult UNKNOWN = new LightResolveResult();

    /** The result can't be determined, but it's definitely not a local variable/parameter */
    public static final LightResolveResult NON_LOCAL = new LightResolveResult();

    @NotNull
    static LightResolveResult resolved(@NotNull final LighterASTNode target) {
      return new LightResolveResult() {
        @Nullable
        @Override
        public LighterASTNode getTarget() {
          return target;
        }
      };
    }

    @Nullable
    public LighterASTNode getTarget() {
      return null;
    }
  }
}