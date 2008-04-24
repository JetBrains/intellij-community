package com.intellij.util.indexing;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Set;

/**
 * @author Dmitry Avdeev
 */
public interface IndexedRootsProvider {

  ExtensionPointName<IndexedRootsProvider> EP_NAME = new ExtensionPointName<IndexedRootsProvider>("com.intellij.indexedRootsProvider");

  Set<VirtualFile> getRootsToIndex(final Project project);
}
