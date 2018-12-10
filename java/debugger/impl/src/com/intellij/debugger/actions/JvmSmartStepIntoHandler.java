// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.actions;

import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.*;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.event.KeyEvent;
import java.util.Collections;
import java.util.List;

public abstract class JvmSmartStepIntoHandler {
  public static final ExtensionPointName<JvmSmartStepIntoHandler> EP_NAME = ExtensionPointName.create("com.intellij.debugger.jvmSmartStepIntoHandler");
  private static final Logger LOG = Logger.getInstance(JvmSmartStepIntoHandler.class);

  @NotNull
  public abstract List<SmartStepTarget> findSmartStepTargets(SourcePosition position);

  public abstract boolean isAvailable(SourcePosition position);

  /**
   * Override this if you haven't PsiMethod, like in Kotlin.
   * @param position
   * @param session
   * @param fileEditor
   * @return false to continue for another handler or for default action (step into)
   */
  public boolean doSmartStep(SourcePosition position, final DebuggerSession session, TextEditor fileEditor) {
    return handleTargets(position, session, fileEditor, findSmartStepTargets(position));
  }

  protected final boolean handleTargets(SourcePosition position,
                                        DebuggerSession session,
                                        TextEditor fileEditor,
                                        List<SmartStepTarget> targets) {
    if (!targets.isEmpty()) {
      SmartStepTarget firstTarget = targets.get(0);
      if (targets.size() == 1) {
        doStepInto(session, Registry.is("debugger.single.smart.step.force"), firstTarget);
      }
      else {
        Editor editor = fileEditor.getEditor();
        PsiMethodListPopupStep popupStep =
          new PsiMethodListPopupStep(editor, targets, chosenTarget -> doStepInto(session, true, chosenTarget));
        ListPopupImpl popup = new ListPopupImpl(popupStep);
        DebuggerUIUtil.registerExtraHandleShortcuts(popup, XDebuggerActions.STEP_INTO, XDebuggerActions.SMART_STEP_INTO);
        popup.setAdText(DebuggerUIUtil.getSelectionShortcutsAdText(XDebuggerActions.STEP_INTO, XDebuggerActions.SMART_STEP_INTO));

        UIUtil.maybeInstall(popup.getList().getInputMap(JComponent.WHEN_FOCUSED),
                            "selectNextRow",
                            KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0));

        popup.addListSelectionListener(new ListSelectionListener() {
          @Override
          public void valueChanged(ListSelectionEvent e) {
            popupStep.getScopeHighlighter().dropHighlight();
            if (!e.getValueIsAdjusting()) {
              final SmartStepTarget selectedTarget = (SmartStepTarget)((JBList)e.getSource()).getSelectedValue();
              if (selectedTarget != null) {
                highlightTarget(popupStep, selectedTarget, position);
              }
            }
          }
        });
        highlightTarget(popupStep, firstTarget, position);
        DebuggerUIUtil.showPopupForEditorLine(popup, editor, position.getLine());
      }
      return true;
    }
    return false;
  }

  protected void doStepInto(DebuggerSession session, boolean force, SmartStepTarget target) {
    JvmSmartStepIntoActionHandler.doStepInto(session, force, createMethodFilter(target));
  }

  private static void highlightTarget(PsiMethodListPopupStep popupStep, SmartStepTarget target, SourcePosition position) {
    final PsiElement highlightElement = target.getHighlightElement();
    if (highlightElement != null) {
      LOG.assertTrue(PsiTreeUtil.isAncestor(position.getFile(), highlightElement, false),
                     "Highlight element " + highlightElement + " in " + target + " is not from the current file");
      popupStep.getScopeHighlighter().highlight(highlightElement, Collections.singletonList(highlightElement));
    }
  }

  /**
   * Override in case if your JVMNames slightly different then it can be provided by getJvmSignature method.
   *
   * @param stepTarget
   * @return SmartStepFilter
   */
  @Nullable
  protected MethodFilter createMethodFilter(SmartStepTarget stepTarget) {
    if (stepTarget instanceof MethodSmartStepTarget) {
      final PsiMethod method = ((MethodSmartStepTarget)stepTarget).getMethod();
      if (stepTarget.needsBreakpointRequest()) {
        return Registry.is("debugger.async.smart.step.into") && method.getContainingClass() instanceof PsiAnonymousClass
               ? new ClassInstanceMethodFilter(method, stepTarget.getCallingExpressionLines())
               : new AnonymousClassMethodFilter(method, stepTarget.getCallingExpressionLines());
      }
      else {
        return new BasicStepMethodFilter(method, stepTarget.getCallingExpressionLines());
      }
    }
    if (stepTarget instanceof LambdaSmartStepTarget) {
      LambdaSmartStepTarget lambdaTarget = (LambdaSmartStepTarget)stepTarget;
      LambdaMethodFilter lambdaMethodFilter =
        new LambdaMethodFilter(lambdaTarget.getLambda(), lambdaTarget.getOrdinal(), stepTarget.getCallingExpressionLines());

      if (Registry.is("debugger.async.smart.step.into") && lambdaTarget.isAsync()) {
        PsiLambdaExpression lambda = ((LambdaSmartStepTarget)stepTarget).getLambda();
        PsiElement expressionList = lambda.getParent();
        if (expressionList instanceof PsiExpressionList) {
          PsiElement method = expressionList.getParent();
          if (method instanceof PsiMethodCallExpression) {
            return new LambdaAsyncMethodFilter(((PsiMethodCallExpression)method).resolveMethod(),
                                               LambdaUtil.getLambdaIdx((PsiExpressionList)expressionList, lambda),
                                               lambdaMethodFilter);
          }
        }
      }

      return lambdaMethodFilter;
    }
    return null;
  }
}
