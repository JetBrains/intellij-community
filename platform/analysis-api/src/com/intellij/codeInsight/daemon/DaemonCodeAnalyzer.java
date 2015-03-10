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

package com.intellij.codeInsight.daemon;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Manages the background highlighting and auto-import for files displayed in editors.
 */
public abstract class DaemonCodeAnalyzer {
  public static DaemonCodeAnalyzer getInstance(Project project) {
    return project.getComponent(DaemonCodeAnalyzer.class);
  }

  public abstract void settingsChanged();

  @Deprecated
  public abstract void updateVisibleHighlighters(@NotNull Editor editor);

  public abstract void setUpdateByTimerEnabled(boolean value);
  public abstract void disableUpdateByTimer(@NotNull Disposable parentDisposable);

  public abstract boolean isHighlightingAvailable(@Nullable PsiFile file);

  public abstract void setImportHintsEnabled(@NotNull PsiFile file, boolean value);
  public abstract void resetImportHintsEnabledForProject();
  public abstract void setHighlightingEnabled(@NotNull PsiFile file, boolean value);
  public abstract boolean isImportHintsEnabled(@NotNull PsiFile file);
  public abstract boolean isAutohintsAvailable(@Nullable PsiFile file);

  /**
   * Force rehighlighting for all files.
   */
  public abstract void restart();

  /**
   * Force rehighlighting for a specific file.
   * @param file the file to rehighlight.
   */
  public abstract void restart(@NotNull PsiFile file);

  public abstract void autoImportReferenceAtCursor(@NotNull Editor editor, @NotNull PsiFile file);

  public static final Topic<DaemonListener> DAEMON_EVENT_TOPIC = Topic.create("DAEMON_EVENT_TOPIC", DaemonListener.class);

  public interface DaemonListener {
    void daemonFinished();
    void daemonCancelEventOccurred(@NotNull String reason);
  }

  public abstract static class DaemonListenerAdapter implements DaemonListener {
    @Override
    public void daemonFinished() {
    }

    @Override
    public void daemonCancelEventOccurred(@NotNull String reason) {
    }
  }
}
