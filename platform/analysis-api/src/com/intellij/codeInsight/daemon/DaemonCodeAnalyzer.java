// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon;

import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.AnnotationSession;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Manages the background highlighting and auto-import for files displayed in editors.
 */
public abstract class DaemonCodeAnalyzer {
  public static DaemonCodeAnalyzer getInstance(Project project) {
    return project.getService(DaemonCodeAnalyzer.class);
  }

  public abstract void settingsChanged();

  @ApiStatus.Internal
  public abstract void setUpdateByTimerEnabled(boolean value);

  public abstract void disableUpdateByTimer(@NotNull Disposable parentDisposable);

  public abstract boolean isHighlightingAvailable(@NotNull PsiFile psiFile);

  public abstract void setImportHintsEnabled(@NotNull PsiFile psiFile, boolean value);

  @Deprecated(forRemoval = true)
  @ApiStatus.Internal
  public abstract void resetImportHintsEnabledForProject();

  public abstract void setHighlightingEnabled(@NotNull PsiFile psiFile, boolean value);

  public abstract boolean isImportHintsEnabled(@NotNull PsiFile psiFile);

  public abstract boolean isAutohintsAvailable(@NotNull PsiFile psiFile);

  /**
   * Force re-highlighting for all files.
   *
   * @see #restart(PsiFile)
   */
  public abstract void restart();

  /**
   * Force re-highlighting for a specific file.
   *
   * @param psiFile the file to rehighlight.
   */
  public abstract void restart(@NotNull PsiFile psiFile);

  public abstract void autoImportReferenceAtCursor(@NotNull Editor editor, @NotNull PsiFile psiFile);

  @ApiStatus.Internal
  public boolean isRunning() {
    return false;
  }

  @Topic.ProjectLevel
  public static final Topic<DaemonListener> DAEMON_EVENT_TOPIC = new Topic<>(DaemonListener.class, Topic.BroadcastDirection.NONE, true);

  /**
   * Project-level listener for various events during daemon lifecycle.
   */
  public interface DaemonListener {

    /**
     * Fired when the background code analysis is being scheduled for the specified set of files.
     *
     * @param fileEditors The list of files that will be analyzed during the current execution of the daemon.
     */
    default void daemonStarting(@NotNull Collection<? extends @NotNull FileEditor> fileEditors) {
    }

    /**
     * @see DaemonListener#daemonFinished(Collection)
     */
    default void daemonFinished() {
    }

    /**
     * Fired when the background code analysis is stopped because it was completed successfully without exceptions.
     *
     * @param fileEditors The list of files analyzed during the current execution of the daemon.
     */
    default void daemonFinished(@NotNull Collection<? extends @NotNull FileEditor> fileEditors) {
      daemonFinished();
    }

    /**
     * Fired when the daemon is canceled because of user tries to type something into the document or other reasons.
     *
     * @implNote Please don't do anything remotely expensive in your listener implementation
     * because it's called in the background thread under the read action,
     * and if it's not fast enough, it could slow down the highlighting process and hurt overall responsiveness.
     */
    default void daemonCanceled(@NotNull String reason, @NotNull Collection<? extends @NotNull FileEditor> fileEditors) {
      daemonCancelEventOccurred(reason);
    }

    /**
     * @see DaemonListener#daemonCanceled(String, Collection)
     */
    default void daemonCancelEventOccurred(@NotNull String reason) {
    }

    /**
     * Internal class for reporting annotator-related statistics
     */
    @ApiStatus.Internal
    final class AnnotatorStatistics {
      /** the annotator this statistics is generated for */
      public final Annotator annotator;
      /** timestamp (in {@link System#nanoTime} sense) of the {@link #annotator} creation */
      public long annotatorStartStamp = System.nanoTime();
      /** timestamp (in {@link System#nanoTime} sense) of the first call to {@link com.intellij.lang.annotation.AnnotationHolder#newAnnotation} by this annotator in this annotation session (or {@code 0} if there were no annotations produced) */
      public long firstAnnotationStamp;
      /** the annotation passed to the first call to {@link com.intellij.lang.annotation.AnnotationHolder#newAnnotation} by this annotator in this annotation session (or {@code null} if there were no annotations produced) */
      public Annotation firstAnnotation;
      /** timestamp (in {@link System#nanoTime} sense) of the last call to {@link com.intellij.lang.annotation.AnnotationHolder#newAnnotation} by this annotator in this annotation session (or {@code 0} if there were no annotations produced) */
      public long lastAnnotationStamp;
      /** the annotation passed to the last call to {@link com.intellij.lang.annotation.AnnotationHolder#newAnnotation} by this annotator in this annotation session (or {@code null} if there were no annotations produced) */
      public Annotation lastAnnotation;
      /** timestamp (in {@link System#nanoTime} sense) of the moment when all the {@link Annotator#annotate(PsiElement, AnnotationHolder)} methods are called */
      public long annotatorFinishStamp;

      public AnnotatorStatistics(@NotNull Annotator annotator) {
        this.annotator = annotator;
      }

      @Override
      public String toString() {
        return "AnnotatorStatistics{" +
               "annotator=" + annotator +
               ", annotatorStartStamp=" + annotatorStartStamp +
               ", firstAnnotationStamp=" + firstAnnotationStamp +
               ", firstAnnotation=" + firstAnnotation +
               ", lastAnnotationStamp=" + lastAnnotationStamp +
               ", lastAnnotation=" + lastAnnotation +
               ", annotatorFinishStamp=" + annotatorFinishStamp +
               '}';
      }
    }

    @ApiStatus.Internal
    default void daemonAnnotatorStatisticsGenerated(@NotNull AnnotationSession session,
                                                    @NotNull Collection<? extends AnnotatorStatistics> statistics,
                                                    @NotNull PsiFile psiFile) {
    }
  }
}
