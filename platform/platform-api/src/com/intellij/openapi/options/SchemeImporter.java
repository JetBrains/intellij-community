// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides functionality to import a scheme from another non-IntelliJ format.
 * 
 * @author Rustam Vishnyakov
 */
public interface SchemeImporter <T extends Scheme> {

  /**
   * @return An extension of a source file which can be imported, for example, "xml".
   */
  String @NotNull [] getSourceExtensions();

  /**
   * Import a scheme from the given virtual file
   * @param project project
   * @param selectedFile  The input file to import from.
   * @param currentScheme source scheme to be updated or to base import on
   * @param schemeFactory The factory to create a scheme with a given name. Importer implementation should use this method
   *                      to create a scheme to which it will save imported values.
   * @return created/updated scheme, or null if action was cancelled
   * @see SchemeFactory
   */
  @Nullable
  T importScheme(@NotNull Project project,
                 @NotNull VirtualFile selectedFile,
                 @NotNull T currentScheme,
                 @NotNull SchemeFactory<? extends T> schemeFactory) throws SchemeImportException;

  /**
   * Called after the scheme has been imported.
   *
   * @param scheme The imported scheme.
   * @return An information message to be displayed after import.
   */
  @Nullable
  default @NlsContexts.NotificationContent String getAdditionalImportInfo(@NotNull T scheme) {
    return null;
  }

  /**
   * @return File to import scheme. If {@code null}, file chooser is shown.
   */
  @Nullable
  default VirtualFile getImportFile() {
    return null;
  }
}