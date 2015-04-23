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
   * @param schemeFactory
   * @return created/updated scheme, or null if action was cancelled
   */
  @Nullable
  T importScheme(@NotNull Project project,
                 @NotNull VirtualFile selectedFile,
                 T currentScheme, SchemeFactory<T> schemeFactory) throws SchemeImportException;

  @Nullable
  String getAdditionalImportInfo(T scheme);
}
