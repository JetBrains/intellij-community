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
package com.intellij.debugger.engine;

import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.jdi.StackFrameProxy;
import com.intellij.debugger.jdi.LocalVariableProxyImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.StringBuilderSpinAllocator;
import com.sun.jdi.Location;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ContextUtil {
  public static final Key<Boolean> IS_JSP_IMPLICIT = new Key<Boolean>("JspImplicit");
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.impl.PositionUtil");

  @Nullable
  public static SourcePosition getSourcePosition(final StackFrameContext context) {
    if (context == null) {
      return null;
    }
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
    final CompoundPositionManager positionManager = debugProcess.getPositionManager();
    if (positionManager == null) {
      // process already closed
      return null;
    }
    try {
      return positionManager.getSourcePosition(location);
    } catch (IndexNotReadyException e) {
      return null;
    }
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

      PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(element.getProject()).getResolveHelper();
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
      final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(element.getProject()).getElementFactory();
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
    final PsiElement rootElement = JspPsiUtil.isInJspFile(psiFile) ? (JspPsiUtil.getJspFile(psiFile)).getJavaClass() : psiFile;
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
