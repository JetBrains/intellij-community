/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.documentation;

import com.intellij.concurrency.SensitiveProgressWrapper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiElement;
import com.intellij.ui.content.Content;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.util.Producer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.TimeUnit;

import static com.intellij.openapi.progress.util.ProgressIndicatorUtils.runInReadActionWithWriteActionPriority;

/**
 * @author gregsh
 */
public class QuickDocUtil {

  public static void updateQuickDocAsync(@NotNull final PsiElement element, @NotNull final Producer<String> docProducer) {
    final Project project = element.getProject();
    ApplicationManager.getApplication().executeOnPooledThread(() -> updateQuickDoc(project, element, docProducer.produce()));
  }

  public static void updateQuickDoc(@NotNull final Project project, @NotNull final PsiElement element, @Nullable final String documentation) {
    if (StringUtil.isEmpty(documentation)) return;
    // modal dialogs with fragment editors fix: can't guess proper modality state here
    UIUtil.invokeLaterIfNeeded(() -> {
      DocumentationComponent component = getActiveDocComponent(project);
      if (component != null) {
        component.replaceText(documentation, element);
      }
    });
  }

  @Nullable
  public static DocumentationComponent getActiveDocComponent(@NotNull Project project) {
    DocumentationManager documentationManager = DocumentationManager.getInstance(project);
    DocumentationComponent component;
    JBPopup hint = documentationManager.getDocInfoHint();
    if (hint != null) {
      component = (DocumentationComponent)((AbstractPopup)hint).getComponent();
    }
    else if (documentationManager.hasActiveDockedDocWindow()) {
      ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.DOCUMENTATION);
      Content selectedContent = toolWindow == null ? null : toolWindow.getContentManager().getSelectedContent();
      component = selectedContent == null ? null : (DocumentationComponent)selectedContent.getComponent();
    }
    else {
      component = null;
    }
    return component;
  }


  /**
   * Repeatedly tries to run given task in read action without blocking write actions (for this to work effectively the action should invoke 
   * {@link ProgressManager#checkCanceled()} or {@link ProgressIndicator#checkCanceled()} often enough).
   *
   * @param action task to run
   * @param timeout timeout in milliseconds 
   * @param pauseBetweenRetries pause between retries in milliseconds 
   * @param progressIndicator optional progress indicator, which can be used to cancel the action externally
   * @return {@code true} if the action succeeded to run without interruptions, {@code false} otherwise
   */
  public static boolean runInReadActionWithWriteActionPriorityWithRetries(@NotNull final Runnable action,
                                                                          long timeout, long pauseBetweenRetries,
                                                                          @Nullable ProgressIndicator progressIndicator) {
    boolean result;
    long deadline = System.currentTimeMillis() + timeout;
    while (!(result = runInReadActionWithWriteActionPriority(action, progressIndicator == null ? null : 
                                                                     new SensitiveProgressWrapper(progressIndicator))) &&
           (progressIndicator == null || !progressIndicator.isCanceled()) && 
            System.currentTimeMillis() < deadline) {
      try {
        TimeUnit.MILLISECONDS.sleep(pauseBetweenRetries);
      }
      catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
      }
    }
    return result;
  }

}
