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
import com.intellij.debugger.engine.MethodFilter;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.psi.PsiMethod;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.11.11
 */
public abstract class JvmSmartStepIntoHandler {
  public static ExtensionPointName<JvmSmartStepIntoHandler> EP_NAME = ExtensionPointName.create("com.intellij.debugger.jvmSmartStepIntoHandler");

  @NotNull
  public abstract List<StepTarget> findSmartStepTargets(SourcePosition position);

  public abstract boolean isAvailable(SourcePosition position);

  public interface StepTarget {
    @NotNull
    PsiMethod getMethod();

    boolean needsBreakpointRequest();

    boolean equals(Object another);

    int hashCode();
  }

  /**
   * Override this if you haven't PsiMethod, like in Kotlin.
   * @param position
   * @param session
   * @param fileEditor
   * @return false to continue for another handler or for default action (step into)
   */
  public boolean doSmartStep(SourcePosition position, final DebuggerSession session, TextEditor fileEditor) {
    final List<StepTarget> targets = findSmartStepTargets(position);
    if (!targets.isEmpty()) {
      if (targets.size() == 1) {
        session.stepInto(true, createMethodFilter(targets.get(0)));
      }
      else {
        final PsiMethodListPopupStep popupStep = new PsiMethodListPopupStep(targets, new PsiMethodListPopupStep.OnChooseRunnable() {
          public void execute(StepTarget chosenTarget) {
            session.stepInto(true, createMethodFilter(chosenTarget));
          }
        });
        final ListPopup popup = JBPopupFactory.getInstance().createListPopup(popupStep);
        final RelativePoint point = DebuggerUIUtil.calcPopupLocation(fileEditor.getEditor(), position.getLine());
        popup.show(point);
      }
      return true;
    }
    return false;
  }

  /**
   * Override in case if your JVMNames slightly different then it can be provided by getJvmSignature method.
   *
   * @param stepTarget
   * @return SmartStepFilter
   */
  protected MethodFilter createMethodFilter(StepTarget stepTarget) {
    return new MethodFilter(stepTarget.getMethod(), stepTarget.needsBreakpointRequest());
  }
}
