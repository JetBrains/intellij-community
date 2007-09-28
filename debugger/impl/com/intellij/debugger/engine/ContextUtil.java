package com.intellij.debugger.engine;

import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.jdi.StackFrameProxy;
import com.intellij.debugger.jdi.LocalVariableProxyImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.StringBuilderSpinAllocator;
import com.sun.jdi.Location;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class ContextUtil {
  public static final Key<Boolean> IS_JSP_IMPLICIT = new Key<Boolean>("JspImplicit");
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.impl.PositionUtil");

  @Nullable
  public static SourcePosition getSourcePosition(final StackFrameContext context) {
    DebugProcessImpl debugProcess = (DebugProcessImpl)context.getDebugProcess();
    if(debugProcess == null) {
      return null;
    }
    final StackFrameProxy frameProxy = context.getFrameProxy();
    if(frameProxy == null) {
      return null;
    }
    Location location = null;
    try {
      location = frameProxy.location();
    }
    catch (Throwable th) {
      LOG.debug(th);
    }
    return ((DebugProcessImpl)context.getDebugProcess()).getPositionManager().getSourcePosition(location);
  }

  @Nullable
  public static PsiElement getContextElement(final StackFrameContext context) {
    return getContextElement(context, getSourcePosition(context));
  }

  @Nullable
  protected static PsiElement getContextElement(final StackFrameContext context, final SourcePosition position) {
    if(LOG.isDebugEnabled()) {
      final SourcePosition sourcePosition = getSourcePosition(context);
      LOG.assertTrue(Comparing.equal(sourcePosition, position));
    }

    final PsiElement element = getContextElement(position);

    if(element == null) {
      return null;
    }

    final StackFrameProxyImpl frameProxy = (StackFrameProxyImpl)context.getFrameProxy();

    if(frameProxy == null) {
      return element;
    }

    final StringBuilder buf = StringBuilderSpinAllocator.alloc();
    try {
      List<LocalVariableProxyImpl> list = frameProxy.visibleVariables();

      PsiResolveHelper resolveHelper = element.getManager().getResolveHelper();
      buf.append('{');
      for (LocalVariableProxyImpl localVariable : list) {
        final String varName = localVariable.name();
        if (resolveHelper.resolveReferencedVariable(varName, element) == null) {
          buf.append(localVariable.getVariable().typeName()).append(" ").append(varName).append(";");
        }
      }
      buf.append('}');

      if (buf.length() <= 2) {
        return element;
      }
      final PsiElementFactory elementFactory = element.getManager().getElementFactory();
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
    catch (IncorrectOperationException e) {
      return element;
    }
    catch (EvaluateException e) {
      return element;
    }
    finally {
      StringBuilderSpinAllocator.dispose(buf);
    }
  }

  @Nullable
  public static PsiElement getContextElement(final SourcePosition position) {
    if(position == null) {
      return null;
    }

    return getContextElementInText(position.getFile(), position.getLine());
  }

  @Nullable
  private static PsiElement getContextElementInText(PsiFile psiFile, int lineNumber) {
    if(lineNumber < 0) {
      return psiFile;
    }

    final Document document = PsiDocumentManager.getInstance(psiFile.getProject()).getDocument(psiFile);
    if (document == null) {
      return null;
    }
    if (lineNumber >= document.getLineCount()) {
      return psiFile;
    }
    int startOffset = document.getLineStartOffset(lineNumber);
    if(startOffset == -1) {
      return null;
    }

    PsiElement element;
    final PsiElement rootElement = PsiUtil.isInJspFile(psiFile) ? (PsiUtil.getJspFile(psiFile)).getJavaClass() : psiFile;
    while(true) {
      final CharSequence charsSequence = document.getCharsSequence();
      for (; startOffset < charsSequence.length(); startOffset++) {
        char c = charsSequence.charAt(startOffset);
        if (c != ' ' && c != '\t') {
          break;
        }
      }
      element = rootElement.findElementAt(startOffset);

      if(element instanceof PsiComment) {
        startOffset = element.getTextRange().getEndOffset() + 1;
      }
      else{
        break;
      }
    }

    if (element != null && element.getParent() instanceof PsiForStatement) {
      return ((PsiForStatement)element.getParent()).getInitialization();
    }
    else {
      return element;
    }
  }

  public static boolean isJspImplicit(PsiElement element) {
    return Boolean.TRUE.equals(element.getUserData(IS_JSP_IMPLICIT));
  }
}
