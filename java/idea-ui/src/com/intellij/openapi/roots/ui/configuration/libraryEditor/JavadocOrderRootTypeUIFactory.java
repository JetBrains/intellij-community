/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 26-Dec-2007
 */
package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.ui.PathEditor;
import com.intellij.openapi.projectRoots.ui.Util;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.ui.OrderRootTypeUIFactory;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;

public class JavadocOrderRootTypeUIFactory implements OrderRootTypeUIFactory {
  private static final Icon ICON = IconLoader.getIcon("/nodes/javaDocFolder.png");

  public PathEditor createPathEditor(Sdk sdk) {
    return new JavadocPathsEditor(sdk);
  }

  @Override
  public Icon getIcon() {
    return ICON;
  }

  @Override
  public String getNodeText() {
    return ProjectBundle.message("library.javadocs.node");
  }

  static class JavadocPathsEditor extends PathEditor {
    private final Sdk mySdk;

    public JavadocPathsEditor(Sdk sdk) {
      super(ProjectBundle.message("sdk.configure.javadoc.tab"),
            JavadocOrderRootType.getInstance(),
            new FileChooserDescriptor(false, true, true, false, true, true));
      mySdk = sdk;
    }

    @Override
    protected boolean isShowUrlButton() {
      return true;
    }

    @Override
    protected void onSpecifyUrlButtonClicked() {
      VirtualFile virtualFile  = Util.showSpecifyJavadocUrlDialog(myPanel, getInitialValue());
      if(virtualFile != null){
        addElement(virtualFile);
        setModified(true);
        updateButtons();
        requestDefaultFocus();
        setSelectedRoots(new Object[]{virtualFile});
      }
    }

    private String getInitialValue() {
      if (mySdk != null) {
        final JavaSdkVersion version = JavaSdk.getInstance().getVersion(mySdk);
        if (version == JavaSdkVersion.JDK_1_5) {
          return "http://download.oracle.com/javase/1.5.0/docs/api/";
        }
        else if (version == JavaSdkVersion.JDK_1_6) {
          return "http://download.oracle.com/javase/6/docs/api/";
        }
        else if (version == JavaSdkVersion.JDK_1_7) {
          return "http://download.oracle.com/javase/7/docs/api/";
        }
      }
      return "";
    }
  }
}
