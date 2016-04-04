package com.intellij.openapi.options;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides functionality to import a scheme from another non-IntelliJ IDEA format.
 * 
 * @author Rustam Vishnyakov
 */
public interface SchemeImporter <T extends Scheme> {

  /**
   * @return An extension of a source file which can be imported, for example, "xml".
   */
  @NotNull
  String[] getSourceExtensions();

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
                 @NotNull SchemeFactory<T> schemeFactory) throws SchemeImportException;

  /**
   * Called after the scheme has been imported.
   * @param scheme The imported scheme.
   * @return An information message to be displayed after import.
   */
  @Nullable
  String getAdditionalImportInfo(@NotNull T scheme);
}