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
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.projectRoots.ui.SdkPathEditor;
import com.intellij.openapi.projectRoots.ui.Util;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.ui.OrderRootTypeUIFactory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonUpdater;
import com.intellij.ui.DumbAwareActionButton;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.util.IconUtil;

import javax.swing.*;

/**
 * @author anna
 * @since 26-Dec-2007
 */
public class JavadocOrderRootTypeUIFactory implements OrderRootTypeUIFactory {
  @Override
  public SdkPathEditor createPathEditor(Sdk sdk) {
    return new JavadocPathsEditor(sdk, FileChooserDescriptorFactory.createMultipleJavaPathDescriptor());
  }

  @Override
  public Icon getIcon() {
    return AllIcons.Nodes.JavaDocFolder;
  }

  @Override
  public String getNodeText() {
    return ProjectBundle.message("library.javadocs.node");
  }

  private static class JavadocPathsEditor extends SdkPathEditor {
    private final Sdk mySdk;

    public JavadocPathsEditor(Sdk sdk, FileChooserDescriptor descriptor) {
      super(ProjectBundle.message("sdk.configure.javadoc.tab"), JavadocOrderRootType.getInstance(), descriptor);
      mySdk = sdk;
    }

    @Override
    protected void addToolbarButtons(ToolbarDecorator toolbarDecorator) {
      AnActionButton specifyUrlButton = new DumbAwareActionButton(ProjectBundle.message("sdk.paths.specify.url.button"), IconUtil.getAddLinkIcon()) {
        @Override
        public void actionPerformed(AnActionEvent e) {
          onSpecifyUrlButtonClicked();
        }
      };
      specifyUrlButton.setShortcut(CustomShortcutSet.fromString("alt S"));
      specifyUrlButton.addCustomUpdater(new AnActionButtonUpdater() {
        @Override
        public boolean isEnabled(AnActionEvent e) {
          return myEnabled;
        }
      });
      toolbarDecorator.addExtraAction(specifyUrlButton);
    }

    private void onSpecifyUrlButtonClicked() {
      String defaultDocsUrl = mySdk == null ? "" : StringUtil.notNullize(((SdkType)mySdk.getSdkType()).getDefaultDocumentationUrl(mySdk), "");
      VirtualFile virtualFile = Util.showSpecifyJavadocUrlDialog(myPanel, defaultDocsUrl);
      if (virtualFile != null) {
        addElement(virtualFile);
        setModified(true);
        requestDefaultFocus();
        setSelectedRoots(new Object[]{virtualFile});
      }
    }
  }
}
