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
import com.intellij.openapi.module.LanguageLevelUtil;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.ui.PathEditor;
import com.intellij.openapi.projectRoots.ui.Util;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.ui.configuration.OrderRootTypeUIFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;

public class JavadocOrderRootTypeUIFactory implements OrderRootTypeUIFactory {
  public LibraryTableTreeContentElement createElement(final LibraryElement parentElement) {
    return new JavadocElement(parentElement);
  }

  public PathEditor createPathEditor(Sdk sdk) {
    return new JavadocPathsEditor(sdk);
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
        final String versionString = mySdk.getVersionString();
        if (versionString != null) {
          final LanguageLevel level = LanguageLevelUtil.getDefaultLanguageLevel(versionString);
          if (level == LanguageLevel.JDK_1_5) {
            return "http://java.sun.com/j2se/1.5.0/docs/api/";
          } else if (level == LanguageLevel.JDK_1_6) {
            return "http://java.sun.com/j2se/6/docs/api/";
          }
        }
      }
      return "";
    }
  }
}