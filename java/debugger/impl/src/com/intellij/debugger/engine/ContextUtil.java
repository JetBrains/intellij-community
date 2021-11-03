// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xdebugger.frame.XNamedValue;
import com.sun.jdi.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ContextUtil {
  public static final Key<Boolean> IS_JSP_IMPLICIT = new Key<>("JspImplicit");
  private static final Logger LOG = Logger.getInstance(PositionUtil.class);

  @Nullable
  public static SourcePosition getSourcePosition(@Nullable final StackFrameContext context) {
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

  @Nullable
  public static PsiElement getContextElement(final StackFrameContext context) {
    return getContextElement(context, getSourcePosition(context));
  }

  @Nullable
  public static PsiElement getContextElement(final StackFrameContext context, final SourcePosition position) {
    if (LOG.isDebugEnabled()) {
      final SourcePosition sourcePosition = getSourcePosition(context);
      LOG.assertTrue(Comparing.equal(sourcePosition, position));
    }

    return ReadAction.compute(() -> {
      final PsiElement element = getContextElement(position);

      if (element == null) {
        return null;
      }

      // further code is java specific, actually
      if (element.getLanguage().getAssociatedFileType() != DefaultCodeFragmentFactory.getInstance().getFileType()) {
        return element;
      }

      final StackFrameProxyImpl frameProxy = (StackFrameProxyImpl)context.getFrameProxy();

      if (frameProxy == null) {
        return element;
      }

      try {
        List<LocalVariableProxyImpl> list = frameProxy.visibleVariables();

        PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(element.getProject()).getResolveHelper();
        StringBuilder buf = null;
        for (LocalVariableProxyImpl localVariable : list) {
          final String varName = localVariable.name();
          if (resolveHelper.resolveReferencedVariable(varName, element) == null) {
            if (buf == null) {
              buf = new StringBuilder("{");
            }
            buf.append(localVariable.getVariable().typeName()).append(" ").append(varName).append(";");
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
          if (statement instanceof PsiDeclarationStatement) {
            PsiDeclarationStatement declStatement = (PsiDeclarationStatement)statement;
            PsiElement[] declaredElements = declStatement.getDeclaredElements();
            for (PsiElement declaredElement : declaredElements) {
              declaredElement.putUserData(IS_JSP_IMPLICIT, Boolean.TRUE);
            }
          }
        }
        return codeBlockFromText;
      }
      catch (IncorrectOperationException | EvaluateException ignored) {
        return element;
      }
    });
  }

  @Nullable
  public static PsiElement getContextElement(@Nullable SourcePosition position) {
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
