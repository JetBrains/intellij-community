/*
 * Copyright 2000-2006 JetBrains s.r.o.
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

package com.intellij.openapi.actionSystem;

import com.intellij.openapi.diagnostic.Logger;

import javax.swing.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Base class for the actions, which update() method might be potentially slow to be executed synchronously in Swing UI thread.
 *
 * @author max
 */
public abstract class AsyncUpdateAction<T> extends AnAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.actionSystem.AsyncUpdateAction");

  private static final ExecutorService ourUpdaterService = Executors.newSingleThreadExecutor();

  // Async update
  public final void update(AnActionEvent e) {
    final T data = prepareDataFromContext(e);
    final Presentation originalPresentation = e.getPresentation();
    if (!forceSyncUpdate(e)) {
      final Presentation realPresentation = (Presentation)originalPresentation.clone();
      ourUpdaterService.submit(new Runnable() {
        public void run() {
          performUpdate(realPresentation, data);
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              if (originalPresentation.isVisible() != realPresentation.isVisible()) {
                LOG.error("Async update is not supported for actions that change their visibility." +
                          "Either stop extending AsyncUpdateAction or override forceSyncUpdate() to return true." +
                          "Action class is: " + AsyncUpdateAction.this.getClass().getName());
              }
              originalPresentation.copyFrom(realPresentation);
            }
          });
        }
      });

      originalPresentation.setVisible(true);
      originalPresentation.setEnabled(false);
    }
    else {
      performUpdate(originalPresentation, data);
    }
  }

  // Sync update
  public final void beforeActionPerformedUpdate(AnActionEvent e) {
    performUpdate(e.getPresentation(), prepareDataFromContext(e));
  }

  /**
   * Get all necessary data from event's DataContext to be used in <code>performUpdate()</code>, which is called asynchronously.
   * @param e action event original update() method have been called with.
   * @return prepared data for {@link #performUpdate(Presentation, T)} method.
   */
  protected abstract T prepareDataFromContext(final AnActionEvent e);

  /**
   * Perform real presentation tweaking here. Be aware of the fact this method may be called in thread other than Swing UI thread thus
   * probable restrictions like necessity to call ApplcationManager.getApplication().runReadAction() apply.
   * @param presentation Presentation object to be tweaked.
   * @param data necessary data calculated by {@link #prepareDataFromContext(AnActionEvent)}.
   */
  protected abstract void performUpdate(Presentation presentation, T data);

  /**
   * Override this method to return <code>true</code> value if update method cannot be called asynchronously for whatever reason.
   * @param e action event original update() method have been called with.
   * @return <code>false</code> if async update is possible and <code>false</code> otherwise.
   */
  protected boolean forceSyncUpdate(AnActionEvent e) {
    return false;
  }
}
