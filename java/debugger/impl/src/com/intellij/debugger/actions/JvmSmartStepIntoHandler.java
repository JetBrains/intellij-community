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
package com.intellij.debugger.actions;

import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.AnonymousClassMethodFilter;
import com.intellij.debugger.engine.BasicStepMethodFilter;
import com.intellij.debugger.engine.LambdaMethodFilter;
import com.intellij.debugger.engine.MethodFilter;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBList;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.util.Arrays;
import java.util.List;

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.11.11
 */
public abstract class JvmSmartStepIntoHandler {
  public static ExtensionPointName<JvmSmartStepIntoHandler> EP_NAME = ExtensionPointName.create("com.intellij.debugger.jvmSmartStepIntoHandler");

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
    final List<SmartStepTarget> targets = findSmartStepTargets(position);
    if (!targets.isEmpty()) {
      final SmartStepTarget firstTarget = targets.get(0);
      if (targets.size() == 1) {
        session.stepInto(true, createMethodFilter(firstTarget));
      }
      else {
        final Editor editor = fileEditor.getEditor();
        final PsiMethodListPopupStep popupStep = new PsiMethodListPopupStep(editor, targets, new PsiMethodListPopupStep.OnChooseRunnable() {
          public void execute(SmartStepTarget chosenTarget) {
            session.stepInto(true, createMethodFilter(chosenTarget));
          }
        });
        final ListPopup popup = JBPopupFactory.getInstance().createListPopup(popupStep);
        popup.addListSelectionListener(new ListSelectionListener() {
          public void valueChanged(ListSelectionEvent e) {
            popupStep.getScopeHighlighter().dropHighlight();
            if (!e.getValueIsAdjusting()) {
              final SmartStepTarget selectedTarget = (SmartStepTarget)((JBList)e.getSource()).getSelectedValue();
              if (selectedTarget != null) {
                highlightTarget(popupStep, selectedTarget);
              }
            }
          }
        });
        highlightTarget(popupStep, firstTarget);
        final RelativePoint point = DebuggerUIUtil.calcPopupLocation(editor, position.getLine());
        popup.show(point);
      }
      return true;
    }
    return false;
  }

  private static void highlightTarget(PsiMethodListPopupStep popupStep, SmartStepTarget target) {
    final PsiElement highlightElement = target.getHighlightElement();
    if (highlightElement != null) {
      popupStep.getScopeHighlighter().highlight(highlightElement, Arrays.asList(highlightElement));
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
      return stepTarget.needsBreakpointRequest()? new AnonymousClassMethodFilter(method) : new BasicStepMethodFilter(method);
    }
    if (stepTarget instanceof LambdaSmartStepTarget) {
      final LambdaSmartStepTarget lambdaTarget = (LambdaSmartStepTarget)stepTarget;
      return new LambdaMethodFilter(lambdaTarget.getLambda(), lambdaTarget.getOrdinal());
    }
    return null;
  }
}
