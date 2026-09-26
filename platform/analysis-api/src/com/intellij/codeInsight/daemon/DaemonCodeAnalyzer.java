// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon;

import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.AnnotationSession;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Manages the background highlighting and auto-import for files displayed in editors.
 */
public abstract class DaemonCodeAnalyzer {
  /**
   * Declares that a non-physical {@link Document} - one whose file is a {@link com.intellij.testFramework.LightVirtualFile},
   * as an {@code EditorTextField}'s own document is - is an interactive editing surface of the given project.
   * <p>
   * The daemon creates highlighting passes for such a document anyway, because {@link #isHighlightingAvailable} only asks
   * whether the PSI file sends events. What it does not do is invalidate the result the way it does for an editor: every
   * listener that marks a file dirty on an edit skips a document backed by a light file, so all that reaches the file
   * status map is the narrow range of the typed characters. An annotator that looks wider than that range - a spell
   * checker, a grammar checker, anything reporting on a word or a sentence - never runs again, and its highlighting stays
   * on screen until something unrelated restarts the daemon. That is why a stale warning in such a field used to go away
   * only when a quick fix was applied. This marker opts one document into the invalidation an editor gets.
   * <p>
   * Set it on the document, not on the file, and name the project the surface belongs to: the daemon cannot derive that
   * from a light file without guessing, and it must not act on another project's document.
   */
  @ApiStatus.Internal
  public static final Key<Project> INTERACTIVE_NON_PHYSICAL_DOCUMENT = Key.create("daemon.interactive.non.physical.document");

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
   * @deprecated use {@link #restart(Object)}
   */
  @Deprecated
  public void restart() {
    restart("Global restart");
  }

  /**
   * Force re-highlighting for all files, for the {@code reason}.
   * @param reason some object which {@code .toString()} will be written to the log file, to identify the source of the daemon restart.
   *               E.g. it could be a string {@code "project roots changed"}, or an instance of quick fix class, etc.
   */
  public void restart(@NotNull @NonNls Object reason) {
    throw new AbstractMethodError();
  }

  /**
   * Force re-highlighting for a specific file.
   *
   * @deprecated use {@link #restart(PsiFile, Object)}
   */
  @Deprecated
  public void restart(@NotNull PsiFile psiFile) {
    restart(psiFile, "Global restart");
  }
  /**
   * Force re-highlighting of this particular {@code psiFile}.
   *
   * @param psiFile the file to rehighlight.
   * @param reason some object which {@code .toString()} will be written to the log file, to identify the source of the daemon restart.
   *               E.g. it could be a string {@code "project roots changed"}, or an instance of quick fix class, etc.
   */
  public void restart(@NotNull PsiFile psiFile, @NotNull @NonNls Object reason) {
    throw new AbstractMethodError();
  }

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
