package com.intellij.debugger.engine;

import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.jdi.LocalVariableProxyImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.jsp.JspFile;
import com.intellij.util.IncorrectOperationException;
import com.sun.jdi.Location;

import java.util.Iterator;
import java.util.List;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class ContextUtil {
  public static final Key IS_JSP_IMPLICIT = new Key("JspImplicit");
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.impl.PositionUtil");

  public static SourcePosition getSourcePosition(final StackFrameContext context) {
    DebugProcessImpl debugProcess = (DebugProcessImpl)context.getDebugProcess();
    if(debugProcess == null) {
      return null;
    }
    if(context.getFrameProxy() == null) {
      return null;
    }
    Location location = null;
    try {
      location = context.getFrameProxy().location();
    } catch (Throwable th) {
      LOG.debug(th);
    }
    return ((DebugProcessImpl)context.getDebugProcess()).getPositionManager().getSourcePosition(location);
  }

  public static PsiElement getContextElement(final StackFrameContext context) {
    return getContextElement(context, getSourcePosition(context));
  }

  protected static PsiElement getContextElement(final StackFrameContext context, final SourcePosition position) {
    if(LOG.isDebugEnabled()) {
      final SourcePosition sourcePosition = getSourcePosition(context);
      LOG.assertTrue(Comparing.equal(sourcePosition, position));
    }

    final PsiElement element = getContextElement(position);

    if(element == null) return null;

    final StackFrameProxyImpl frameProxy = (StackFrameProxyImpl)context.getFrameProxy();

    if(frameProxy == null) return element;

    try {
      List<LocalVariableProxyImpl> list = frameProxy.visibleVariables();

      StringBuffer buf = new StringBuffer();
      PsiResolveHelper resolveHelper = element.getManager().getResolveHelper();
      buf.append('{');
      for (Iterator<LocalVariableProxyImpl> iterator = list.iterator(); iterator.hasNext();) {
        LocalVariableProxyImpl localVariable = iterator.next();

        String varName = localVariable.name();
        if(resolveHelper.resolveReferencedVariable(varName, element) == null) {
          buf.append(localVariable.getVariable().typeName() + " " + varName + ";");
        }
      }
      buf.append('}');

      if(buf.length() > 2) {
        PsiElementFactory elementFactory = element.getManager().getElementFactory();
        PsiCodeBlock codeBlockFromText = elementFactory.createCodeBlockFromText(buf.toString(), element);

        PsiStatement[] statements = codeBlockFromText.getStatements();
        for (int i = 0; i < statements.length; i++) {
          PsiStatement statement = statements[i];
          if (statement instanceof PsiDeclarationStatement) {
            PsiDeclarationStatement declStatement = (PsiDeclarationStatement)statement;
            PsiElement[] declaredElements = declStatement.getDeclaredElements();
            for (int j = 0; j < declaredElements.length; j++) {
              declaredElements[j].putUserData(IS_JSP_IMPLICIT, IS_JSP_IMPLICIT);
            }
          }
        }
        return codeBlockFromText;
      }
      else {
        return element;
      }
    }
    catch (IncorrectOperationException e) {
      return element;
    }
    catch (EvaluateException e) {
      return element;
    }
  }

  public static PsiElement getContextElement(final SourcePosition position) {
    if(position == null) return null;

    return getContextElementInText(position.getFile(), position.getLine());
  }

  private static PsiElement getContextElementInText(PsiFile psiFile, int lineNumber) {
    if(lineNumber < 0) {
      return psiFile;
    }

    final Document document = PsiDocumentManager.getInstance(psiFile.getProject()).getDocument(psiFile);
    if (lineNumber >= document.getLineCount()) {
      return psiFile;
    }
    int startOffset = document.getLineStartOffset(lineNumber);
    if(startOffset == -1) {
      return null;
    }

    PsiElement element;
    final PsiElement rootElement = psiFile instanceof JspFile? ((JspFile)psiFile).getJavaRoot() : psiFile;
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
    return element.getUserData(IS_JSP_IMPLICIT) != null;
  }
}
