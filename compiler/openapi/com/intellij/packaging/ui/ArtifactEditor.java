package com.intellij.packaging.ui;

import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.packaging.elements.CompositePackagingElement;

import java.util.List;

/**
 * @author nik
 */
public interface ArtifactEditor {

  void putLibraryIntoDefaultLocation(@NotNull Library library);

  void addToClasspath(CompositePackagingElement<?> element, List<String> classpath);
}
