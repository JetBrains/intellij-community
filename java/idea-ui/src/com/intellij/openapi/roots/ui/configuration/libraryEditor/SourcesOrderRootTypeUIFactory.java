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

import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.ui.SdkPathEditor;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ui.OrderRootTypeUIFactory;
import com.intellij.openapi.roots.ui.configuration.LibrarySourceRootDetectorUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PlatformIcons;

import javax.swing.*;
import java.awt.*;

/**
 * @author anna
 */
public final class SourcesOrderRootTypeUIFactory implements OrderRootTypeUIFactory {
  @Override
  public SdkPathEditor createPathEditor(final Sdk sdk) {
    FileChooserDescriptor descriptor = new FileChooserDescriptor(true, true, true, false, true, true);
    return new SourcesPathEditor(sdk, descriptor);
  }

  @Override
  public Icon getIcon() {
    return PlatformIcons.SOURCE_FOLDERS_ICON;
  }

  @Override
  public String getNodeText() {
    return JavaUiBundle.message("library.sources.node");
  }

  private static class SourcesPathEditor extends SdkPathEditor {
    private final Sdk mySdk;

    SourcesPathEditor(Sdk sdk, FileChooserDescriptor descriptor) {
      super(JavaUiBundle.message("sdk.configure.sourcepath.tab"), OrderRootType.SOURCES, descriptor);
      mySdk = sdk;
    }

    @Override
    protected VirtualFile[] adjustAddedFileSet(final Component component, final VirtualFile[] files) {
      if (mySdk.getSdkType() instanceof JavaSdkType) {
        return LibrarySourceRootDetectorUtil.scanAndSelectDetectedJavaSourceRoots(component, files);
      }
      return super.adjustAddedFileSet(component, files);
    }
  }
}
