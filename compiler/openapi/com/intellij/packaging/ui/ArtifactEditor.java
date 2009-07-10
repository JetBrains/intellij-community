package com.intellij.packaging.ui;

import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.roots.libraries.Library;

/**
 * @author nik
 */
public interface ArtifactEditor {

  void putLibraryIntoDefaultLocation(@NotNull Library library);

}
