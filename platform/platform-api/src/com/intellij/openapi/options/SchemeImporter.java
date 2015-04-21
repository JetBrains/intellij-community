package com.intellij.openapi.options;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PairConvertor;
import org.jetbrains.annotations.NotNull;

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
   * @param project
   * @param selectedFile  The input file to import from.
   * @param currentScheme
   * @param schemeCreator Callback that provides the target scheme receiving data.
   */
  boolean importScheme(@NotNull Project project,
                       @NotNull VirtualFile selectedFile,
                       T currentScheme,
                       @NotNull PairConvertor<String, Boolean, T> schemeCreator) throws SchemeImportException;
}
