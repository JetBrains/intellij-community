/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.debugger.settings;

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.xdebugger.frame.XFullValueEvaluator;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author egor
 */
public abstract class CustomPopupFullValueEvaluator<T> extends XFullValueEvaluator {
  protected final EvaluationContextImpl myEvaluationContext;

  public CustomPopupFullValueEvaluator(@NotNull String linkText, @NotNull EvaluationContextImpl evaluationContext) {
    super(linkText);
    myEvaluationContext = evaluationContext;
    setShowValuePopup(false);
  }

  protected abstract T getData();

  protected abstract JComponent createComponent(T data);

  @Override
  public void startEvaluation(@NotNull final XFullValueEvaluationCallback callback) {
    myEvaluationContext.getManagerThread().schedule(new SuspendContextCommandImpl(myEvaluationContext.getSuspendContext()) {
      @Override
      public Priority getPriority() {
        return Priority.NORMAL;
      }

      @Override
      public void contextAction() throws Exception {
        if (callback.isObsolete()) return;
        final T data = getData();
        DebuggerUIUtil.invokeLater(new Runnable() {
          @Override
          public void run() {
            if (callback.isObsolete()) return;
            final JComponent comp = createComponent(data);
            Project project = myEvaluationContext.getProject();
            JBPopup popup = DebuggerUIUtil.createValuePopup(project, comp, null);
            JFrame frame = WindowManager.getInstance().getFrame(project);
            Dimension frameSize = frame.getSize();
            Dimension size = new Dimension(frameSize.width / 2, frameSize.height / 2);
            popup.setSize(size);
            callback.evaluated("");
            popup.show(new RelativePoint(frame, new Point(size.width / 2, size.height / 2)));
          }
        });
      }
    });
  }
}
