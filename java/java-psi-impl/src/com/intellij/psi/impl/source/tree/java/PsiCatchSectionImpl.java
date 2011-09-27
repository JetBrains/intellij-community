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
package com.intellij.psi.impl.source.tree.java;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author ven
 */
public class PsiCatchSectionImpl extends CompositePsiElement implements PsiCatchSection, Constants {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiCatchSectionImpl");

  private CachedValue<List<PsiType>> myTypesCache = null;

  public PsiCatchSectionImpl() {
    super(CATCH_SECTION);
  }

  public PsiParameter getParameter() {
    return (PsiParameter)findChildByRoleAsPsiElement(ChildRole.PARAMETER);
  }

  public PsiCodeBlock getCatchBlock() {
    return (PsiCodeBlock)findChildByRoleAsPsiElement(ChildRole.CATCH_BLOCK);
  }

  public PsiType getCatchType() {
    PsiParameter parameter = getParameter();
    if (parameter == null) return null;
    return parameter.getType();
  }

  @NotNull
  public List<PsiType> getPreciseCatchTypes() {
    final PsiParameter parameter = getParameter();
    if (parameter == null) return Collections.emptyList();

    return getTypesCache().getValue();
  }

  private synchronized CachedValue<List<PsiType>> getTypesCache() {
    if (myTypesCache == null) {
      final CachedValuesManager cacheManager = CachedValuesManager.getManager(getProject());
      myTypesCache = cacheManager.createCachedValue(new CachedValueProvider<List<PsiType>>() {
          @Override public Result<List<PsiType>> compute() {
            final List<PsiType> types = computePreciseCatchTypes(getParameter());
            return Result.create(types, PsiModificationTracker.MODIFICATION_COUNT);
          }
        }, false);
    }
    return myTypesCache;
  }

  private List<PsiType> computePreciseCatchTypes(final PsiParameter parameter) {
    final PsiType declaredType = parameter.getType();

    // When the thrown expression is a ... exception parameter Ej (parameter) of a catch clause Cj (this) ...
    final LanguageLevel level = PsiUtil.getLanguageLevel(parameter);
    if (level.isAtLeast(LanguageLevel.JDK_1_7)) {
      if (isCatchParameterEffectivelyFinal(parameter, getCatchBlock())) {
        final PsiCodeBlock tryBlock = getTryStatement().getTryBlock();
        if (tryBlock != null) {
          // ... and the try block of the try statement which declares Cj (tryBlock) can throw T ...
          final List<PsiClassType> thrownTypes = ExceptionUtil.getThrownExceptions(tryBlock);
          // ... and for all exception parameters Ei declared by any catch clauses Ci, 1 <= i < j,
          //     declared to the left of Cj for the same try statement, T is not assignable to Ei ...
          final PsiParameter[] parameters = getTryStatement().getCatchBlockParameters();
          final List<PsiType> uncaughtTypes = ContainerUtil.mapNotNull(thrownTypes, new NullableFunction<PsiClassType, PsiType>() {
            @Override public PsiType fun(final PsiClassType thrownType) {
              for (int i = 0; i < parameters.length && parameters[i] != parameter && thrownTypes.size() > 0; i++) {
                final PsiType catchType = parameters[i].getType();
                if (catchType.isAssignableFrom(thrownType)) return null;
              }
              return thrownType;
            }
          });
          // ... and T is assignable to Ej ...
          boolean passed = true;
          for (PsiType type : uncaughtTypes) {
            if (!declaredType.isAssignableFrom(type)) {
              passed = false;
              break;
            }
          }
          // ... the throw statement throws precisely the set of exception types T.
          if (passed) return uncaughtTypes;
        }
      }
    }

    return Arrays.asList(declaredType);
  }

  // do not use control flow here to avoid dead loop
  private static boolean isCatchParameterEffectivelyFinal(final PsiParameter parameter, final PsiCodeBlock catchBlock) {
    final boolean[] result = {true};
    if (catchBlock != null) {
      catchBlock.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitAssignmentExpression(final PsiAssignmentExpression expression) {
          final PsiExpression left = expression.getLExpression();
          if (left instanceof PsiReferenceExpression && parameter.equals(((PsiReferenceExpression)left).resolve())) {
            result[0] = false;
            stopWalking();
          }
        }
      });
    }
    return result[0];
  }

  @NotNull
  public PsiTryStatement getTryStatement() {
    return (PsiTryStatement)getParent();
  }

  @Nullable
  public PsiJavaToken getLParenth() {
    return (PsiJavaToken)findChildByRole(ChildRole.CATCH_BLOCK_PARAMETER_LPARENTH);
  }

  @Nullable
  public PsiJavaToken getRParenth() {
    return (PsiJavaToken)findChildByRole(ChildRole.CATCH_BLOCK_PARAMETER_RPARENTH);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitCatchSection(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiCatchSection";
  }

  public ASTNode findChildByRole(int role) {
    switch(role) {
      default:
        return null;

      case ChildRole.PARAMETER:
        return findChildByType(PARAMETER);

      case ChildRole.CATCH_KEYWORD:
        return findChildByType(CATCH_KEYWORD);

      case ChildRole.CATCH_BLOCK_PARAMETER_LPARENTH:
        return findChildByType(LPARENTH);

      case ChildRole.CATCH_BLOCK_PARAMETER_RPARENTH:
        return findChildByType(RPARENTH);

      case ChildRole.CATCH_BLOCK:
        return findChildByType(CODE_BLOCK);
    }
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == PARAMETER) {
      return ChildRole.PARAMETER;
    } else if (i == CODE_BLOCK) {
      return ChildRole.CATCH_BLOCK;
    } else if (i == CATCH_KEYWORD) {
      return ChildRole.CATCH_KEYWORD;
    } else if (i == LPARENTH) {
      return ChildRole.CATCH_BLOCK_PARAMETER_LPARENTH;
    } else if (i == RPARENTH) {
      return ChildRole.CATCH_BLOCK_PARAMETER_RPARENTH;
    }

    return ChildRoleBase.NONE;
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, this);
    if (lastParent == null || lastParent.getParent() != this)
      // Parent element should not see our vars
      return true;

    final PsiParameter catchParameter = getParameter();
    if (catchParameter != null) {
      return processor.execute(catchParameter, state);
    }

    return PsiScopesUtil.walkChildrenScopes(this, processor, state, lastParent, place);
  }
}
