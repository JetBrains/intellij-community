// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.ApiStatus.OverrideOnly;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implement to get notified about files/directories traversed during "Scanning files to index" sessions, which happen
 * on project startup and on new files/directories added to project.<p/>
 * For example, during the project startup, all content files/directories are scanned.
 * Scanned files/directories are under content roots and not excluded or ignored.
 */
@Internal
@OverrideOnly
public interface IndexableFileScanner {
  ExtensionPointName<IndexableFileScanner> EP_NAME = ExtensionPointName.create("com.intellij.projectFileScanner");

  /**
   * Called on a background thread when a scan session is started in the given project.
   * It's guaranteed that only single {@link ScanSession} will be created inside single scanning session.
   * @param project Project the scan session is started for.
   */
  @NotNull ScanSession startSession(@NotNull Project project);

  /**
   * Single scan session lives inside a single "Scanning files to index" session.
   */
  interface ScanSession {
    /**
     * Called on a background thread (possibly different from {@link IndexableFileScanner#startSession(Project)}) when
     * visiting the given files from the given {@code indexableSetOrigin}.
     * Different origin file sets can be visited on different threads during the same scan session.
     * Please note it can be called not under read action, thus {@code indexableSetOrigin} might be invalid or the project might be already disposed.
     * <br/>
     * One can use {@code indexableSetOrigin} to determine scanned file set source: module, library, sdk, etc. {@see IndexableFilesIterator} for details.
     *
     * @param indexableSetOrigin the file set being scanned
     *
     * @return a visitor is being used for {@code fileSet} scanning or
     * {@code null} if the indexable file set should not be processed by a {@link IndexableFileScanner}.
     */
    @Nullable
    IndexableFileVisitor createVisitor(@NotNull IndexableSetOrigin indexableSetOrigin);
  }

  /**
   * File visitor that works inside single {@link IndexableFilesIterator}.
   */
  interface IndexableFileVisitor {
    /**
     * Called on a background thread when visiting the given {@code fileOrDir}.
     * Please note it can be called not under read action, thus {@code fileSet} might be invalid or the project might be already disposed.
     *
     * @param fileOrDir the file being scanned
     */
    void visitFile(@NotNull VirtualFile fileOrDir);

    /**
     * Called on a background thread when files of {@link IndexableSetOrigin} have been successfully processed.
     * <p>
     * If several {@link IndexableSetOrigin}s have common files (e.g., common jars between libraries),
     * then {@link #visitFile} will be skipped for all visitors except for the first one to scan a specific file.
     * Therefore, this method should NOT expect that {@link #visitFile} has been called for <b>all</b> the files in a provider.
     * @see IndexableFilesDeduplicateFilter
     */
    @ApiStatus.Experimental
    default void visitingFinished() {

    }
  }
}
