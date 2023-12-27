// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class PsiCatchSectionImpl extends CompositePsiElement implements PsiCatchSection, Constants {
  private static final Logger LOG = Logger.getInstance(PsiCatchSectionImpl.class);

  private final Object myTypesCacheLock = new Object();

  public PsiCatchSectionImpl() {
    super(CATCH_SECTION);
  }

  @Override
  public PsiParameter getParameter() {
    return (PsiParameter)findChildByRoleAsPsiElement(ChildRole.PARAMETER);
  }

  @Override
  public PsiCodeBlock getCatchBlock() {
    return (PsiCodeBlock)findChildByRoleAsPsiElement(ChildRole.CATCH_BLOCK);
  }

  @Override
  public PsiType getCatchType() {
    PsiParameter parameter = getParameter();
    if (parameter == null) return null;
    return parameter.getType();
  }

  @Override
  @NotNull
  public List<PsiType> getPreciseCatchTypes() {
    final PsiParameter parameter = getParameter();
    if (parameter == null) return Collections.emptyList();

    return getTypesCache();
  }

  private List<PsiType> getTypesCache() {
    return CachedValuesManager.getProjectPsiDependentCache(this, section ->
      computePreciseCatchTypes(section.getParameter())
    );
  }

  private List<PsiType> computePreciseCatchTypes(@Nullable final PsiParameter parameter) {
    if (parameter == null) {
      return ContainerUtil.emptyList();
    }

    PsiType declaredType = parameter.getType();

    // When the thrown expression is an ... exception parameter Ej (parameter) of a catch clause Cj (this) ...
    if (PsiUtil.getLanguageLevel(parameter).isAtLeast(LanguageLevel.JDK_1_7) &&
        isCatchParameterEffectivelyFinal(parameter, getCatchBlock())) {
      PsiTryStatement statement = getTryStatement();
      // ... and the try block of the try statement which declares Cj (tryBlock) can throw T ...
      Collection<PsiClassType> thrownTypes = getThrownTypes(statement);
      if (thrownTypes.isEmpty()) return Collections.emptyList();
      // ... and for all exception parameters Ei declared by any catch clauses Ci, 1 <= i < j,
      //     declared to the left of Cj for the same try statement, T is not assignable to Ei ...
      final PsiParameter[] parameters = statement.getCatchBlockParameters();
      final int currentIdx = ArrayUtil.find(parameters, parameter);
      List<PsiType> uncaughtTypes = ContainerUtil.mapNotNull(thrownTypes, thrownType -> {
        for (int i = 0; i < currentIdx; i++) {
          final PsiType catchType = parameters[i].getType();
          if (catchType.isAssignableFrom(thrownType)) return null;
        }
        return thrownType;
      });
      if (uncaughtTypes.isEmpty()) return Collections.emptyList();  // unreachable catch section
      // ... and T is assignable to Ej ...
      if (declaredType instanceof PsiDisjunctionType) {
        return Collections.unmodifiableList(((PsiDisjunctionType)declaredType).getDisjunctions()
                                              .stream()
                                              .flatMap(disjunction -> computePreciseCatchTypes(disjunction, uncaughtTypes).stream())
                                              .collect(Collectors.toCollection(() -> new ArrayList<>())));
      }
      else {
        return computePreciseCatchTypes(declaredType, uncaughtTypes);
      }
    }

    return Collections.singletonList(declaredType);
  }

  private static List<PsiType> computePreciseCatchTypes(PsiType declaredType, List<PsiType> uncaughtTypes) {
    List<PsiType> types = new SmartList<>();
    for (PsiType type : uncaughtTypes) {
      if (type.isAssignableFrom(declaredType)) {
        types.add(declaredType);
      }
      else if (declaredType.isAssignableFrom(type) ||
          // JLS 11.2.3 "Exception Checking":
          // "It is a compile-time error if a catch clause can catch checked exception class E1 and it is not the case
          // that the try block corresponding to the catch clause can throw a checked exception class that is
          // a subclass or superclass of E1, unless E1 is Exception or a superclass of Exception."
          // So here unchecked exception can sneak through Exception or Throwable catch type only.
          ExceptionUtil.isGeneralExceptionType(declaredType) && type instanceof PsiClassType && ExceptionUtil.isUncheckedException((PsiClassType)type)) {
        types.add(type);
      }
    }
    // ... the throw statement throws precisely the set of exception types T.
    return types;
  }

  private static Collection<PsiClassType> getThrownTypes(@NotNull PsiTryStatement statement) {
    Collection<PsiClassType> types = new ArrayList<>();
    PsiCodeBlock tryBlock = statement.getTryBlock();
    if (tryBlock != null) {
      types.addAll(ExceptionUtil.getThrownExceptions(tryBlock));
    }
    PsiResourceList resourceList = statement.getResourceList();
    if (resourceList != null) {
      types.addAll(ExceptionUtil.getThrownExceptions(resourceList));
    }
    return types;
  }

  // do not use control flow here to avoid dead loop
  private static boolean isCatchParameterEffectivelyFinal(final PsiParameter parameter, @Nullable final PsiCodeBlock catchBlock) {
    final boolean[] result = {true};
    if (catchBlock != null) {
      catchBlock.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
          super.visitReferenceExpression(expression);
          if (expression.resolve() == parameter && PsiUtil.isAccessedForWriting(expression)) {
            result[0] = false;
            stopWalking();
          }
        }
      });
    }
    return result[0];
  }

  @Override
  @NotNull
  public PsiTryStatement getTryStatement() {
    return (PsiTryStatement)getParent();
  }

  @Override
  @Nullable
  public PsiJavaToken getLParenth() {
    return (PsiJavaToken)findChildByRole(ChildRole.CATCH_BLOCK_PARAMETER_LPARENTH);
  }

  @Override
  @Nullable
  public PsiJavaToken getRParenth() {
    return (PsiJavaToken)findChildByRole(ChildRole.CATCH_BLOCK_PARAMETER_RPARENTH);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitCatchSection(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiCatchSection";
  }

  @Override
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

  @Override
  public int getChildRole(@NotNull ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == PARAMETER) {
      return ChildRole.PARAMETER;
    }
    if (i == CODE_BLOCK) {
      return ChildRole.CATCH_BLOCK;
    }
    if (i == CATCH_KEYWORD) {
      return ChildRole.CATCH_KEYWORD;
    }
    if (i == LPARENTH) {
      return ChildRole.CATCH_BLOCK_PARAMETER_LPARENTH;
    }
    if (i == RPARENTH) {
      return ChildRole.CATCH_BLOCK_PARAMETER_RPARENTH;
    }

    return ChildRoleBase.NONE;
  }

  @Override
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
