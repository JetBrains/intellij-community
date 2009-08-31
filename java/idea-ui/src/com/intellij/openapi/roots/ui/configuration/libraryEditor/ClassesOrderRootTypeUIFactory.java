/*
 * User: anna
 * Date: 26-Dec-2007
 */
package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.ui.PathEditor;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ui.configuration.OrderRootTypeUIFactory;

public class ClassesOrderRootTypeUIFactory implements OrderRootTypeUIFactory {
  public LibraryTableTreeContentElement createElement(final LibraryElement parentElement) {
    return new ClassesElement(parentElement);
  }

  public PathEditor createPathEditor() {
    return new MyPathsEditor(ProjectBundle.message("sdk.configure.classpath.tab"), OrderRootType.CLASSES, new FileChooserDescriptor(true, true, true, false, true, true), false);
  }
}