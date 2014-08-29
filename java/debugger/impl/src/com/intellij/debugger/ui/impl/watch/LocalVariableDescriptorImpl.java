/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.debugger.ui.impl.watch;

import com.intellij.codeInsight.daemon.impl.IdentifierHighlighterPass;
import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.PositionUtil;
import com.intellij.debugger.jdi.LocalVariableProxyImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.ui.tree.LocalVariableDescriptor;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.debugger.ui.tree.render.ClassRenderer;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XSourcePosition;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class LocalVariableDescriptorImpl extends ValueDescriptorImpl implements LocalVariableDescriptor {
  private final StackFrameProxyImpl myFrameProxy;
  private final LocalVariableProxyImpl myLocalVariable;

  private String myTypeName = DebuggerBundle.message("label.unknown.value");
  private boolean myIsPrimitive;

  private boolean myIsNewLocal = true;

  public LocalVariableDescriptorImpl(Project project,
                                     @NotNull LocalVariableProxyImpl local) {
    super(project);
    setLvalue(true);
    myFrameProxy = local.getFrame();
    myLocalVariable = local;
  }

  @Override
  public LocalVariableProxyImpl getLocalVariable() {
    return myLocalVariable;
  }

  @Nullable
  public SourcePosition getSourcePosition(final Project project, final DebuggerContextImpl context) {
    return getSourcePosition(project, context, false);
  }

  @Nullable
  public SourcePosition getSourcePosition(final Project project, final DebuggerContextImpl context, boolean nearest) {
    StackFrameProxyImpl frame = context.getFrameProxy();
    if (frame == null) return null;

    PsiElement place = PositionUtil.getContextElement(context);

    if (place == null) {
      return null;
    }

    PsiVariable psiVariable = JavaPsiFacade.getInstance(project).getResolveHelper().resolveReferencedVariable(getName(), place);
    if (psiVariable == null) {
      return null;
    }

    PsiFile containingFile = psiVariable.getContainingFile();
    if(containingFile == null) return null;
    if (nearest) {
      return findNearest(context, psiVariable, containingFile);
    }
    return SourcePosition.createFromOffset(containingFile, psiVariable.getTextOffset());
  }

  private static SourcePosition findNearest(@NotNull DebuggerContextImpl context, @NotNull PsiVariable psi, @NotNull PsiFile file) {
    final DebuggerSession session = context.getDebuggerSession();
    if (session != null) {
      try {
        final XDebugSession debugSession = session.getXDebugSession();
        if (debugSession != null) {
          final XSourcePosition position = debugSession.getCurrentPosition();
          final Editor editor = PsiUtilBase.findEditor(psi);
          if (editor != null && position != null && file.getVirtualFile().equals(position.getFile())) {
            final Couple<Collection<TextRange>> usages = IdentifierHighlighterPass.getHighlightUsages(psi, file);
            final List<TextRange> ranges = new ArrayList<TextRange>();
            ranges.addAll(usages.first);
            ranges.addAll(usages.second);
            final int breakPointLine = position.getLine();
            int bestLine = -1;
            boolean hasSameLine = false;
            for (TextRange range : ranges) {
              final int line = editor.offsetToLogicalPosition(range.getStartOffset()).line;
              if (line > bestLine && line < breakPointLine) {
                bestLine = line;
              } else if (line == breakPointLine) {
                hasSameLine = true;
              }
            }
            if (bestLine > 0) {
              if (hasSameLine && breakPointLine - bestLine > 4) {
                return SourcePosition.createFromLine(file, breakPointLine);
              }
              return SourcePosition.createFromLine(file, bestLine);
            }
          }
        }
      }
      catch (Exception ignore) {
      }
    }
    return SourcePosition.createFromOffset(file, psi.getTextOffset());
  }

  public boolean isNewLocal() {
    return myIsNewLocal;
  }

  @Override
  public boolean isPrimitive() {
    return myIsPrimitive;
  }

  @Override
  public Value calcValue(EvaluationContextImpl evaluationContext) throws EvaluateException {
    boolean isVisible = myFrameProxy.isLocalVariableVisible(getLocalVariable());
    if (isVisible) {
      final String typeName = getLocalVariable().typeName();
      myTypeName = typeName;
      myIsPrimitive = DebuggerUtils.isPrimitiveType(typeName);
      return myFrameProxy.getValue(getLocalVariable());
    }

    return null;
  }

  public void setNewLocal(boolean aNew) {
    myIsNewLocal = aNew;
  }

  @Override
  public void displayAs(NodeDescriptor descriptor) {
    super.displayAs(descriptor);
    if(descriptor instanceof LocalVariableDescriptorImpl) {
      myIsNewLocal = ((LocalVariableDescriptorImpl)descriptor).myIsNewLocal;
    }
  }

  @Override
  public String getName() {
    return myLocalVariable.name();
  }

  @Override
  public String calcValueName() {
    final ClassRenderer classRenderer = NodeRendererSettings.getInstance().getClassRenderer();
    StringBuilder buf = StringBuilderSpinAllocator.alloc();
    try {
      buf.append(getName());
      if (classRenderer.SHOW_DECLARED_TYPE) {
        buf.append(": ");
        buf.append(classRenderer.renderTypeName(myTypeName));
      }
      return buf.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(buf);
    }
  }

  @Override
  public PsiExpression getDescriptorEvaluation(DebuggerContext context) throws EvaluateException {
    PsiElementFactory elementFactory = JavaPsiFacade.getInstance(context.getProject()).getElementFactory();
    try {
      return elementFactory.createExpressionFromText(getName(), PositionUtil.getContextElement(context));
    }
    catch (IncorrectOperationException e) {
      throw new EvaluateException(DebuggerBundle.message("error.invalid.local.variable.name", getName()), e);
    }
  }
}