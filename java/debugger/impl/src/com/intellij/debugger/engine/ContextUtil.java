// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine;

import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.evaluation.DefaultCodeFragmentFactory;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.jdi.StackFrameProxy;
import com.intellij.debugger.impl.PositionUtil;
import com.intellij.debugger.jdi.LocalVariableProxyImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.ui.impl.watch.NodeManagerImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiResolveHelper;
import com.intellij.psi.PsiStatement;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence;
import com.intellij.xdebugger.frame.XNamedValue;
import com.sun.jdi.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class ContextUtil {
  public static final Key<Boolean> IS_JSP_IMPLICIT = new Key<>("JspImplicit");
  private static final Logger LOG = Logger.getInstance(PositionUtil.class);

  @RequiresReadLockAbsence
  public static @Nullable SourcePosition getSourcePosition(final @Nullable StackFrameContext context) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    if (context == null) {
      return null;
    }
    DebugProcessImpl debugProcess = (DebugProcessImpl)context.getDebugProcess();
    if (debugProcess == null) {
      return null;
    }
    final StackFrameProxy frameProxy = context.getFrameProxy();
    if (frameProxy == null) {
      return null;
    }
    Location location = null;
    try {
      location = frameProxy.location();
    }
    catch (Throwable e) {
      LOG.debug(e);
    }
    if (location == null) {
      return null;
    }
    return debugProcess.getPositionManager().getSourcePosition(location);
  }

  /**
   * Builds the PSI context for expression evaluation under an internal read action.
   * <p>
   * To use the context within an existing read action, prepare it before the outermost read action in the call chain.
   * Call {@link #prepareContext(StackFrameContext)} on the debugger manager thread.
   * Create and use the PSI context inside the same read action:
   * <pre>{@code
   * var preparedContext = ContextUtil.prepareContext(context);
   * ReadAction.runBlocking(() -> {
   *   var element = preparedContext.getContextElement();
   *   // Continue the caller's PSI work here.
   * });
   * }</pre>
   * Use {@link PreparedContext#getSourcePosition()} when the caller also needs the source position.
   */
  @RequiresReadLockAbsence
  public static @Nullable PsiElement getContextElement(final StackFrameContext context) {
    var preparedContext = prepareContext(context);
    return ReadAction.computeBlocking(preparedContext::getContextElement);
  }

  /**
   * Collects the source position and variable metadata for a PSI context.
   * Call this method on the debugger manager thread without a read lock.
   */
  @RequiresReadLockAbsence
  public static @NotNull PreparedContext prepareContext(StackFrameContext context) {
    var position = getSourcePosition(context);
    var variables = getVisibleVariables(context);
    return new PreparedContext(position, variables);
  }

  /**
   * Stores the data needed to build a PSI context for one suspended frame.
   * Create and use this object while the frame remains suspended.
   */
  public static final class PreparedContext {
    private final @Nullable SourcePosition mySourcePosition;
    private final List<LocalVariableProxyImpl> myVariables;

    private PreparedContext(@Nullable SourcePosition sourcePosition, List<LocalVariableProxyImpl> variables) {
      mySourcePosition = sourcePosition;
      myVariables = List.copyOf(variables);
    }

    public @Nullable SourcePosition getSourcePosition() {
      return mySourcePosition;
    }

    /**
     * Builds the PSI context under the caller's read lock without querying the JVM.
     * Use the returned element within the same read action.
     */
    @RequiresReadLock
    public @Nullable PsiElement getContextElement() {
      return ContextUtil.getContextElement(mySourcePosition, myVariables);
    }
  }

  /**
   * Builds the PSI context from a source position and variable metadata under a read lock.
   * Reads cached strings through {@link LocalVariableProxyImpl#name()} and {@link LocalVariableProxyImpl#typeName()}.
   * Fetch any additional JVM data before entering the read action.
   * {@link LocalVariableProxyImpl#getVariable()} and {@link LocalVariableProxyImpl#getType()} may query the JVM.
   */
  @RequiresReadLock
  private static @Nullable PsiElement getContextElement(@Nullable SourcePosition position,
                                                        @NotNull List<LocalVariableProxyImpl> variables) {
    final PsiElement element = getContextElement(position);

    if (element == null) {
      return null;
    }

    // further code is java specific, actually
    if (element.getLanguage().getAssociatedFileType() != DefaultCodeFragmentFactory.getInstance().getFileType()) {
      return element;
    }

    try {
      PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(element.getProject()).getResolveHelper();
      StringBuilder buf = null;
      for (LocalVariableProxyImpl localVariable : variables) {
        final String varName = localVariable.name();
        if (resolveHelper.resolveReferencedVariable(varName, element) == null) {
          if (buf == null) {
            buf = new StringBuilder("{");
          }
          buf.append(localVariable.typeName()).append(" ").append(varName).append(";");
        }
      }
      if (buf == null) {
        return element;
      }

      buf.append('}');

      final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(element.getProject());
      final PsiCodeBlock codeBlockFromText = elementFactory.createCodeBlockFromText(buf.toString(), element);

      final PsiStatement[] statements = codeBlockFromText.getStatements();
      for (PsiStatement statement : statements) {
        if (statement instanceof PsiDeclarationStatement declStatement) {
          PsiElement[] declaredElements = declStatement.getDeclaredElements();
          for (PsiElement declaredElement : declaredElements) {
            declaredElement.putUserData(IS_JSP_IMPLICIT, Boolean.TRUE);
          }
        }
      }
      return codeBlockFromText;
    }
    catch (IncorrectOperationException ignored) {
      return element;
    }
  }

  @RequiresReadLockAbsence
  private static @NotNull List<LocalVariableProxyImpl> getVisibleVariables(StackFrameContext context) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    var frameProxy = (StackFrameProxyImpl)context.getFrameProxy();
    if (frameProxy != null) {
      try {
        return frameProxy.visibleVariables();
      }
      catch (EvaluateException ignored) {
      }
    }
    return Collections.emptyList();
  }

  public static @Nullable PsiElement getContextElement(@Nullable SourcePosition position) {
    return position == null ? null : position.getElementAt();
  }

  public static boolean isJspImplicit(PsiElement element) {
    return Boolean.TRUE.equals(element.getUserData(IS_JSP_IMPLICIT));
  }

  public static XNamedValue createValue(@NotNull EvaluationContextImpl context, @NotNull NodeManagerImpl nodeManager, @NotNull
    ValueDescriptorImpl descriptor) {
    return JavaValue.create(null, descriptor, context, nodeManager, false);
  }
}
