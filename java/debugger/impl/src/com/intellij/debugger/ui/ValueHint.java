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
package com.intellij.debugger.ui;

import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.*;
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilderImpl;
import com.intellij.debugger.engine.evaluation.expression.ExpressionEvaluator;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.ui.impl.DebuggerTreeRenderer;
import com.intellij.debugger.ui.impl.InspectDebuggerTree;
import com.intellij.debugger.ui.impl.watch.WatchItemDescriptor;
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.ui.SimpleColoredText;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xdebugger.impl.evaluate.quick.common.AbstractValueHint;
import com.intellij.xdebugger.impl.evaluate.quick.common.AbstractValueHintTreeComponent;
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueHintType;
import com.sun.jdi.PrimitiveValue;
import com.sun.jdi.Value;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * User: lex
 * Date: Nov 24, 2003
 * Time: 7:31:26 PM
 */
public class ValueHint extends AbstractValueHint {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.ValueHint");
  private PsiExpression myCurrentExpression = null;

  private ValueHint(Project project, Editor editor, Point point, ValueHintType type, final PsiExpression selectedExpression, final TextRange textRange) {
    super(project, editor, point, type, textRange);
    myCurrentExpression = selectedExpression;
  }

  public static ValueHint createValueHint(Project project, Editor editor, Point point, ValueHintType type) {
    Pair<PsiExpression, TextRange> pair = getSelectedExpression(project, editor, point, type);
    return new ValueHint(project, editor, point, type, pair.getFirst(), pair.getSecond());
  }

  protected boolean canShowHint() {
    return myCurrentExpression != null;
  }

  protected void evaluateAndShowHint() {
    final DebuggerContextImpl debuggerContext = DebuggerManagerEx.getInstanceEx(getProject()).getContext();

    final DebuggerSession debuggerSession = debuggerContext.getDebuggerSession();
    if(debuggerSession == null || !debuggerSession.isPaused()) return;

    try {
      final ExpressionEvaluator evaluator = EvaluatorBuilderImpl.getInstance().build(myCurrentExpression, debuggerContext.getSourcePosition());

      debuggerContext.getDebugProcess().getManagerThread().schedule(new DebuggerContextCommandImpl(debuggerContext) {
        public Priority getPriority() {
          return Priority.HIGH;
        }

        public void threadAction() {
          try {
            final EvaluationContextImpl evaluationContext = debuggerContext.createEvaluationContext();


            final TextWithImports text = new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, myCurrentExpression.getText());
            final Value value = evaluator.evaluate(evaluationContext);

            final WatchItemDescriptor descriptor = new WatchItemDescriptor(getProject(), text, value);
            if (!isActiveTootlipApplicable(value) || getType() == ValueHintType.MOUSE_OVER_HINT) {
              descriptor.setContext(evaluationContext);
              if (getType() == ValueHintType.MOUSE_OVER_HINT) {
                // force using default renderer for mouse over hint in order to not to call accidentaly methods while rendering
                // otherwise, if the hint is invoked explicitly, show it with the right "auto" renderer
                descriptor.setRenderer(debuggerContext.getDebugProcess().getDefaultRenderer(value));
              }
              descriptor.updateRepresentation(evaluationContext, new DescriptorLabelListener() {
                public void labelChanged() {
                  if(getCurrentRange() != null) {
                    if(getType() != ValueHintType.MOUSE_OVER_HINT || descriptor.isValueValid()) {
                      final SimpleColoredText simpleColoredText = DebuggerTreeRenderer.getDescriptorText(debuggerContext, descriptor, true);
                      if (isActiveTootlipApplicable(value)){
                        simpleColoredText.append(" (" + DebuggerBundle.message("active.tooltip.suggestion") + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
                      }
                      showHint(simpleColoredText, descriptor);
                    }
                  }
                }
              });
            } else {
              final InspectDebuggerTree tree = getInspectTree(descriptor);
              showTreePopup(tree, debuggerContext, myCurrentExpression.getText(), new ValueHintTreeComponent(ValueHint.this, tree, myCurrentExpression.getText()));
            }
          }
          catch (EvaluateException e) {
            LOG.debug(e);
          }
        }

      });
    }
    catch (EvaluateException e) {
      LOG.debug(e);
    }
  }

  private static boolean isActiveTootlipApplicable(final Value value) {
    return value != null && !(value instanceof PrimitiveValue);
  }


  public void showTreePopup(final InspectDebuggerTree tree,
                        final DebuggerContextImpl debuggerContext,
                        final String title,
                        final AbstractValueHintTreeComponent<?> component) {
    DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
      public void run() {
        tree.rebuild(debuggerContext);
        showTreePopup(component, tree, title);
      }
    });
  }

  private void showHint(final SimpleColoredText text, final WatchItemDescriptor descriptor) {
    DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
      public void run() {
        if(!isHintHidden()) {
          JComponent component;
          if (!isActiveTootlipApplicable(descriptor.getValue())) {
            component = HintUtil.createInformationLabel(text);
          }
          else {
            component = createExpandableHintComponent(text, new Runnable() {
              public void run() {
                final DebuggerContextImpl debuggerContext = DebuggerManagerEx.getInstanceEx(getProject()).getContext();
                final DebugProcessImpl debugProcess = debuggerContext.getDebugProcess();
                debugProcess.getManagerThread().schedule(new DebuggerContextCommandImpl(debuggerContext) {
                              public void threadAction() {
                                descriptor.setRenderer(debugProcess.getAutoRenderer(descriptor));
                                final InspectDebuggerTree tree = getInspectTree(descriptor);
                                showTreePopup(tree, debuggerContext, myCurrentExpression.getText(),
                                              new ValueHintTreeComponent(ValueHint.this, tree, myCurrentExpression.getText()));
                              }
                            });
              }
            });
          }
          if (!showHint(component)) return;
          if(getType() == ValueHintType.MOUSE_CLICK_HINT) {
            HintUtil.createInformationLabel(text).requestFocusInWindow();
          }
        }
      }
    });
  }

  private InspectDebuggerTree getInspectTree(final WatchItemDescriptor descriptor) {
    final InspectDebuggerTree tree = new InspectDebuggerTree(getProject());
    tree.getModel().addTreeModelListener(createTreeListener(tree));
    tree.setInspectDescriptor(descriptor);
    return tree;
  }

  @Nullable
  private static Pair<PsiExpression, TextRange> findExpression(PsiElement element, boolean allowMethodCalls) {
    if (!(element instanceof PsiIdentifier || element instanceof PsiKeyword)) {
      return null;
    }

    PsiElement expression = null;
    PsiElement parent = element.getParent();
    if (parent instanceof PsiVariable) {
      expression = element;
    }
    else if (parent instanceof PsiReferenceExpression) {
      final PsiElement pparent = parent.getParent();
      if (pparent instanceof PsiMethodCallExpression) {
        parent = pparent;
      }
      if (allowMethodCalls || !DebuggerUtils.hasSideEffects(parent)) {
        expression = parent;
      }
    }
    else if (parent instanceof PsiThisExpression) {
      expression = parent;
    }
    
    if (expression != null) {
      try {
        PsiElement context = element;
        if(parent instanceof PsiParameter) {
          try {
            context = ((PsiMethod)((PsiParameter)parent).getDeclarationScope()).getBody();
          }
          catch (Throwable ignored) {
          }
        } 
        else {
          while(context != null  && !(context instanceof PsiStatement) && !(context instanceof PsiClass)) {
            context = context.getParent();
          }
        }
        TextRange textRange = expression.getTextRange();
        PsiExpression psiExpression = JavaPsiFacade.getInstance(expression.getProject()).getElementFactory().createExpressionFromText(expression.getText(), context);
        return Pair.create(psiExpression, textRange);
      }
      catch (IncorrectOperationException e) {
        LOG.debug(e);
      }
    }
    return null;
  }

  private static Pair<PsiExpression, TextRange> getSelectedExpression(final Project project, final Editor editor, final Point point, final ValueHintType type) {
    final Ref<PsiExpression> selectedExpression = Ref.create(null);
    final Ref<TextRange> currentRange = Ref.create(null);

    PsiDocumentManager.getInstance(project).commitAndRunReadAction(new Runnable() {
      public void run() {
        // Point -> offset
        final int offset = calculateOffset(editor, point);


        PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());

        if(psiFile == null || !psiFile.isValid()) return;

        int selectionStart = editor.getSelectionModel().getSelectionStart();
        int selectionEnd   = editor.getSelectionModel().getSelectionEnd();

        if((type == ValueHintType.MOUSE_CLICK_HINT || type == ValueHintType.MOUSE_ALT_OVER_HINT) && (selectionStart <= offset && offset <= selectionEnd)) {
          PsiElement ctx = (selectionStart > 0) ? psiFile.findElementAt(selectionStart - 1) : psiFile.findElementAt(selectionStart);
          try {
            String text = editor.getSelectionModel().getSelectedText();
            if(text != null && ctx != null) {
              selectedExpression.set(JavaPsiFacade.getInstance(project).getElementFactory().createExpressionFromText(text, ctx));
              currentRange.set(new TextRange(editor.getSelectionModel().getSelectionStart(), editor.getSelectionModel().getSelectionEnd()));
            }
          } catch (IncorrectOperationException e) {
          }
        }

        if(currentRange.get() == null) {
          PsiElement elementAtCursor = psiFile.findElementAt(offset);
          if (elementAtCursor == null) return;
          Pair<PsiExpression, TextRange> pair = findExpression(elementAtCursor, type == ValueHintType.MOUSE_CLICK_HINT || type == ValueHintType.MOUSE_ALT_OVER_HINT);
          if (pair == null) return;
          selectedExpression.set(pair.getFirst());
          currentRange.set(pair.getSecond());
        }
      }
    });
    return Pair.create(selectedExpression.get(), currentRange.get());
  }

}
