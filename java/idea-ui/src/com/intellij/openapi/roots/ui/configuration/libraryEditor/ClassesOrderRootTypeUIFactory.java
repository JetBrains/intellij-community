/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.icons.AllIcons;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.ui.SdkPathEditor;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ui.OrderRootTypeUIFactory;
import com.intellij.openapi.vfs.impl.jrt.JrtFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBList;
import com.intellij.util.PlatformIcons;

import javax.swing.*;

/**
 * @author anna
 * @since 26-Dec-2007
 */
public class ClassesOrderRootTypeUIFactory implements OrderRootTypeUIFactory {
  @Override
  public SdkPathEditor createPathEditor(Sdk sdk) {
    FileChooserDescriptor descriptor = new FileChooserDescriptor(true, true, true, false, true, true);
    return new MySdkPathEditor(descriptor);
  }

  @Override
  public Icon getIcon() {
    return AllIcons.Nodes.CompiledClassesFolder;
  }

  @Override
  public String getNodeText() {
    return ProjectBundle.message("library.classes.node");
  }

  private static class MySdkPathEditor extends SdkPathEditor {
    public MySdkPathEditor(FileChooserDescriptor descriptor) {
      super(ProjectBundle.message("sdk.configure.classpath.tab"), OrderRootType.CLASSES, descriptor);
    }

    @Override
    protected boolean isRemoveActionEnabled(Object[] values) {
      if (!super.isRemoveActionEnabled(values)) {
        return false;
      }
      for (Object value : values) {
        if (isJrtRoot(value)) {
          return false;
        }
      }
      return true;
    }

    @Override
    protected ListCellRenderer createListCellRenderer(JBList list) {
      return new PathCellRenderer() {
        @Override
        protected String getItemText(Object value) {
          return isJrtRoot(value) ? LangBundle.message("jrt.node.long") : super.getItemText(value);
        }

        @Override
        protected Icon getItemIcon(Object value) {
          return isJrtRoot(value) ? PlatformIcons.JAR_ICON : super.getItemIcon(value);
        }
      };
    }
  }

  private static boolean isJrtRoot(Object value) {
    return value instanceof VirtualFile && JrtFileSystem.isRoot((VirtualFile)value);
  }
}
