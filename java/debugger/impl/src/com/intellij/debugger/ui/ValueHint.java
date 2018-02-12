/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.debugger.actions.DebuggerActions;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.JVMName;
import com.intellij.debugger.engine.JVMNameUtil;
import com.intellij.debugger.engine.SuspendContextImpl;
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
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.WatchItemDescriptor;
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.psi.*;
import com.intellij.ui.SimpleColoredText;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xdebugger.impl.evaluate.quick.common.AbstractValueHint;
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueHintType;
import com.sun.jdi.Method;
import com.sun.jdi.PrimitiveValue;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

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

  @Override
  protected boolean canShowHint() {
    return myCurrentExpression != null;
  }


  @Nullable
  private ExpressionEvaluator getExpressionEvaluator(DebuggerContextImpl debuggerContext) throws EvaluateException {
    if (myCurrentExpression instanceof PsiExpression) {
      return EvaluatorBuilderImpl.getInstance().build(myCurrentExpression, debuggerContext.getSourcePosition());
    }

    TextWithImportsImpl textWithImports = new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, myCurrentExpression.getText());
    CodeFragmentFactory factory = DebuggerUtilsEx.findAppropriateCodeFragmentFactory(textWithImports, myCurrentExpression);
    JavaCodeFragment codeFragment = factory.createCodeFragment(textWithImports, myCurrentExpression.getContext(), getProject());
    return factory.getEvaluatorBuilder().build(codeFragment, debuggerContext.getSourcePosition());
  }


  @Override
  protected void evaluateAndShowHint() {
    final DebuggerContextImpl debuggerContext = DebuggerManagerEx.getInstanceEx(getProject()).getContext();

    final DebuggerSession debuggerSession = debuggerContext.getDebuggerSession();
    if(debuggerSession == null || !debuggerSession.isPaused()) return;

    try {

      final ExpressionEvaluator evaluator = getExpressionEvaluator(debuggerContext);
      if (evaluator == null) return;

      debuggerContext.getDebugProcess().getManagerThread().schedule(new DebuggerContextCommandImpl(debuggerContext) {
        @Override
        public Priority getPriority() {
          return Priority.HIGH;
        }

        @Override
        public void threadAction(@NotNull SuspendContextImpl suspendContext) {
          try {
            final EvaluationContextImpl evaluationContext = debuggerContext.createEvaluationContext();

            final String expressionText = ReadAction.compute(() -> myCurrentExpression.getText());
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
                @Override
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
            }
            else {
              createAndShowTree(expressionText, descriptor);
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

  private void createAndShowTree(final String expressionText, final NodeDescriptorImpl descriptor) {
    final DebuggerTreeCreatorImpl creator = new DebuggerTreeCreatorImpl(getProject());
    DebuggerInvocationUtil.invokeLater(getProject(), () -> showTreePopup(creator, Pair.create(descriptor, expressionText)));
  }

  private static boolean isActiveTooltipApplicable(final Value value) {
    return value != null && !(value instanceof PrimitiveValue);
  }

  private void showHint(final SimpleColoredText text, final WatchItemDescriptor descriptor) {
    DebuggerInvocationUtil.invokeLater(getProject(), () -> {
      if(!isHintHidden()) {
        JComponent component;
        if (!isActiveTooltipApplicable(descriptor.getValue())) {
          component = HintUtil.createInformationLabel(text);
        }
        else {
          component = createExpandableHintComponent(text, () -> {
            final DebuggerContextImpl debuggerContext = DebuggerManagerEx.getInstanceEx(getProject()).getContext();
            final DebugProcessImpl debugProcess = debuggerContext.getDebugProcess();
            debugProcess.getManagerThread().schedule(new DebuggerContextCommandImpl(debuggerContext) {
              @Override
              public void threadAction(@NotNull SuspendContextImpl suspendContext) {
                descriptor.setRenderer(debugProcess.getAutoRenderer(descriptor));
                final String expressionText = ReadAction.compute(() -> myCurrentExpression.getText());
                createAndShowTree(expressionText, descriptor);
              }
            });
          });
        }
        if (!showHint(component)) return;
        if(getType() == ValueHintType.MOUSE_CLICK_HINT) {
          HintUtil.createInformationLabel(text).requestFocusInWindow();
        }
      }
    });
  }

  public static InspectDebuggerTree createInspectTree(final NodeDescriptorImpl descriptor, Project project) {
    final InspectDebuggerTree tree = new InspectDebuggerTree(project);
    final AnAction setValueAction = ActionManager.getInstance().getAction(DebuggerActions.SET_VALUE);
    setValueAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0)), tree);
    Disposer.register(tree, new Disposable() {
      @Override
      public void dispose() {
        setValueAction.unregisterCustomShortcutSet(tree);
      }
    });
    tree.setInspectDescriptor(descriptor);
    DebuggerContextImpl context = DebuggerManagerEx.getInstanceEx(project).getContext();
    tree.rebuild(context);
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

    PsiDocumentManager.getInstance(project).commitAndRunReadAction(() -> {
      // Point -> offset
      final int offset = calculateOffset(editor, point);


      PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());

      if(psiFile == null || !psiFile.isValid()) {
        return;
      }

      int selectionStart = editor.getSelectionModel().getSelectionStart();
      int selectionEnd   = editor.getSelectionModel().getSelectionEnd();

      if((type == ValueHintType.MOUSE_CLICK_HINT || type == ValueHintType.MOUSE_ALT_OVER_HINT) && (selectionStart <= offset && offset <= selectionEnd)) {
        PsiElement ctx = (selectionStart > 0) ? psiFile.findElementAt(selectionStart - 1) : psiFile.findElementAt(selectionStart);
        try {
          String text = editor.getSelectionModel().getSelectedText();
          if(text != null && ctx != null) {
            final JVMElementFactory factory = JVMElementFactories.getFactory(ctx.getLanguage(), project);
            if (factory == null) {
              return;
            }
            selectedExpression.set(factory.createExpressionFromText(text, ctx));
            currentRange.set(new TextRange(editor.getSelectionModel().getSelectionStart(), editor.getSelectionModel().getSelectionEnd()));
          }
        }
        catch (IncorrectOperationException ignored) {
        }
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
    });
    return Trinity.create(selectedExpression.get(), currentRange.get(), preCalculatedValue.get());
  }
}
