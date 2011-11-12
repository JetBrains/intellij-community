/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.debugger.engine.JVMName;
import com.intellij.debugger.engine.JVMNameUtil;
import com.intellij.debugger.engine.evaluation.*;
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilderImpl;
import com.intellij.debugger.engine.evaluation.expression.ExpressionEvaluator;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.impl.EditorTextProvider;
import com.intellij.debugger.ui.impl.DebuggerTreeRenderer;
import com.intellij.debugger.ui.impl.InspectDebuggerTree;
import com.intellij.debugger.ui.impl.watch.WatchItemDescriptor;
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.SimpleColoredText;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xdebugger.impl.evaluate.quick.common.AbstractValueHint;
import com.intellij.xdebugger.impl.evaluate.quick.common.AbstractValueHintTreeComponent;
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueHintType;
import com.sun.jdi.Method;
import com.sun.jdi.PrimitiveValue;
import com.sun.jdi.Value;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author lex
 * @since Nov 24, 2003
 */
public class ValueHint extends AbstractValueHint {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.ValueHint");
  private PsiElement myCurrentExpression = null;
  private Value myValueToShow = null;

  private ValueHint(Project project, Editor editor, Point point, ValueHintType type, final PsiElement selectedExpression, final TextRange textRange) {
    super(project, editor, point, type, textRange);
    myCurrentExpression = selectedExpression;
  }

  public static ValueHint createValueHint(Project project, Editor editor, Point point, ValueHintType type) {
    Trinity<PsiElement, TextRange, Value> trinity = getSelectedExpression(project, editor, point, type);
    final ValueHint hint = new ValueHint(project, editor, point, type, trinity.getFirst(), trinity.getSecond());
    hint.myValueToShow = trinity.getThird();
    return hint;
  }

  protected boolean canShowHint() {
    return myCurrentExpression != null;
  }


  @Nullable
  private ExpressionEvaluator getExpressionEvaluator(DebuggerContextImpl debuggerContext) throws EvaluateException {
    if (myCurrentExpression instanceof PsiExpression) {
      return EvaluatorBuilderImpl.getInstance().build(myCurrentExpression, debuggerContext.getSourcePosition());
    }

    CodeFragmentFactory factory = DebuggerUtilsEx.getEffectiveCodeFragmentFactory(myCurrentExpression);
    TextWithImportsImpl textWithImports = new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, myCurrentExpression.getText());
    if (factory == null) return null;
    JavaCodeFragment codeFragment = factory.createCodeFragment(textWithImports, myCurrentExpression.getContext(), getProject());
    codeFragment.forceResolveScope(GlobalSearchScope.allScope(getProject()));
    return factory.getEvaluatorBuilder().build(codeFragment, debuggerContext.getSourcePosition());
  }


  protected void evaluateAndShowHint() {
    final DebuggerContextImpl debuggerContext = DebuggerManagerEx.getInstanceEx(getProject()).getContext();

    final DebuggerSession debuggerSession = debuggerContext.getDebuggerSession();
    if(debuggerSession == null || !debuggerSession.isPaused()) return;

    try {

      final ExpressionEvaluator evaluator = getExpressionEvaluator(debuggerContext);
      if (evaluator == null) return;

      debuggerContext.getDebugProcess().getManagerThread().schedule(new DebuggerContextCommandImpl(debuggerContext) {
        public Priority getPriority() {
          return Priority.HIGH;
        }

        public void threadAction() {
          try {
            final EvaluationContextImpl evaluationContext = debuggerContext.createEvaluationContext();

            final String expressionText = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
              public String compute() {
                return myCurrentExpression.getText();
              }
            });
            final TextWithImports text = new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, expressionText);
            final Value value = myValueToShow != null? myValueToShow : evaluator.evaluate(evaluationContext);

            final WatchItemDescriptor descriptor = new WatchItemDescriptor(getProject(), text, value);
            if (!isActiveTooltipApplicable(value) || getType() == ValueHintType.MOUSE_OVER_HINT) {
              if (getType() == ValueHintType.MOUSE_OVER_HINT) {
                // force using default renderer for mouse over hint in order to not to call accidentally methods while rendering
                // otherwise, if the hint is invoked explicitly, show it with the right "auto" renderer
                descriptor.setRenderer(DebugProcessImpl.getDefaultRenderer(value));
              }
              descriptor.updateRepresentation(evaluationContext, new DescriptorLabelListener() {
                public void labelChanged() {
                  if(getCurrentRange() != null) {
                    if(getType() != ValueHintType.MOUSE_OVER_HINT || descriptor.isValueValid()) {
                      final SimpleColoredText simpleColoredText = DebuggerTreeRenderer.getDescriptorText(debuggerContext, descriptor, true);
                      if (isActiveTooltipApplicable(value)){
                        simpleColoredText.append(" (" + DebuggerBundle.message("active.tooltip.suggestion") + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
                      }
                      showHint(simpleColoredText, descriptor);
                    }
                  }
                }
              });
            } else {
              final InspectDebuggerTree tree = getInspectTree(descriptor);
              showTreePopup(tree, debuggerContext, expressionText, new ValueHintTreeComponent(ValueHint.this, tree, expressionText));
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

  private static boolean isActiveTooltipApplicable(final Value value) {
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
          if (!isActiveTooltipApplicable(descriptor.getValue())) {
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
  private static Pair<PsiElement, TextRange> findExpression(PsiElement element, boolean allowMethodCalls) {
    final EditorTextProvider textProvider = EditorTextProvider.EP.forLanguage(element.getLanguage());
    if (textProvider != null) {
      return textProvider.findExpression(element, allowMethodCalls);
    }
    return null;
  }

  private static Trinity<PsiElement, TextRange, Value> getSelectedExpression(final Project project, final Editor editor, final Point point, final ValueHintType type) {
    final Ref<PsiElement> selectedExpression = Ref.create(null);
    final Ref<TextRange> currentRange = Ref.create(null);
    final Ref<Value> preCalculatedValue = Ref.create(null);

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
              selectedExpression.set(JVMElementFactories.getFactory(ctx.getLanguage(), project).createExpressionFromText(text, ctx));
              currentRange.set(new TextRange(editor.getSelectionModel().getSelectionStart(), editor.getSelectionModel().getSelectionEnd()));
            }
          }
          catch (IncorrectOperationException ignored) { }
        }

        if(currentRange.get() == null) {
          PsiElement elementAtCursor = psiFile.findElementAt(offset);
          if (elementAtCursor == null) {
            return;
          }
          Pair<PsiElement, TextRange> pair = findExpression(elementAtCursor, type == ValueHintType.MOUSE_CLICK_HINT || type == ValueHintType.MOUSE_ALT_OVER_HINT);
          if (pair == null) {
            if (type == ValueHintType.MOUSE_OVER_HINT) {
              final DebuggerSession debuggerSession = DebuggerManagerEx.getInstanceEx(project).getContext().getDebuggerSession();
              if(debuggerSession != null && debuggerSession.isPaused()) {
                final Pair<Method, Value> lastExecuted = debuggerSession.getProcess().getLastExecutedMethod();
                if (lastExecuted != null) {
                  final Method method = lastExecuted.getFirst();
                  if (method != null) {
                    final Pair<PsiElement, TextRange> expressionPair = findExpression(elementAtCursor, true);
                    if (expressionPair != null && expressionPair.getFirst() instanceof PsiMethodCallExpression) {
                      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expressionPair.getFirst();
                      final PsiMethod psiMethod = methodCallExpression.resolveMethod();
                      if (psiMethod != null) {
                        final JVMName jvmSignature = JVMNameUtil.getJVMSignature(psiMethod);
                        try {
                          if (method.name().equals(psiMethod.getName()) && method.signature().equals(jvmSignature.getName(debuggerSession.getProcess()))) {
                            pair = expressionPair;
                            preCalculatedValue.set(lastExecuted.getSecond());
                          }
                        }
                        catch (EvaluateException ignored) {
                        }
                      }
                    }
                  }
                }
              }
            }
          }
          if (pair == null) {
            return;
          }
          selectedExpression.set(pair.getFirst());
          currentRange.set(pair.getSecond());
        }
      }
    });
    return Trinity.create(selectedExpression.get(), currentRange.get(), preCalculatedValue.get());
  }
}
