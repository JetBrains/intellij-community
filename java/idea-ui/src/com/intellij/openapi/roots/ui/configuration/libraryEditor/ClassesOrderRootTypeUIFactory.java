// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.icons.AllIcons;
import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.ui.SdkPathEditor;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ui.OrderRootTypeUIFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.jrt.JrtFileSystem;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author anna
 */
public final class ClassesOrderRootTypeUIFactory implements OrderRootTypeUIFactory {
  @Override
  public SdkPathEditor createPathEditor(Sdk sdk) {
    return new MySdkPathEditor(new FileChooserDescriptor(true, true, true, false, true, true));
  }

  @Override
  public Icon getIcon() {
    return AllIcons.Nodes.CompiledClassesFolder;
  }

  @Override
  public String getNodeText() {
    return JavaUiBundle.message("library.classes.node");
  }

  private static class MySdkPathEditor extends SdkPathEditor {
    MySdkPathEditor(FileChooserDescriptor descriptor) {
      super(JavaUiBundle.message("sdk.configure.classpath.tab"), OrderRootType.CLASSES, descriptor);
    }

    @Override
    protected boolean isRemoveActionEnabled(VirtualFile[] files) {
      if (!super.isRemoveActionEnabled(files)) {
        return false;
      }
      for (VirtualFile file : files) {
        if (isJrtRoot(file)) {
          return false;
        }
      }
      return true;
    }

    @Override
    protected ListCellRenderer<VirtualFile> createListCellRenderer(JBList<VirtualFile> list) {
      return new PathCellRenderer() {
        @Override
        protected void customizeCellRenderer(@NotNull JList<? extends VirtualFile> list, VirtualFile file, int index, boolean selected, boolean focused) {
          super.customizeCellRenderer(list, file, index, selected, focused);
          if (isJrtRoot(file)) {
            setIcon(AllIcons.Nodes.Module);
          }
        }
      };
    }
  }

  private static boolean isJrtRoot(VirtualFile file) {
    return file != null && JrtFileSystem.isModuleRoot(file);
  }
}