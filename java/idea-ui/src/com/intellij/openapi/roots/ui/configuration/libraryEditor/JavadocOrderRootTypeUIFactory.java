// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.icons.AllIcons;
import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.projectRoots.ui.SdkPathEditor;
import com.intellij.openapi.projectRoots.ui.Util;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.ui.OrderRootTypeUIFactory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.util.IconUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author anna
 */
public final class JavadocOrderRootTypeUIFactory implements OrderRootTypeUIFactory {
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
    return JavaUiBundle.message("library.javadocs.node");
  }

  private static class JavadocPathsEditor extends SdkPathEditor {
    private final Sdk mySdk;

    JavadocPathsEditor(Sdk sdk, FileChooserDescriptor descriptor) {
      super(JavaUiBundle.message("sdk.configure.javadoc.tab"), JavadocOrderRootType.getInstance(), descriptor);
      mySdk = sdk;
    }

    @Override
    protected void addToolbarButtons(ToolbarDecorator toolbarDecorator) {
      AnAction specifyUrlButton = new DumbAwareAction(JavaUiBundle.messagePointer("sdk.paths.specify.url.button"), IconUtil.getAddLinkIcon()) {
        @Override
        public void update(@NotNull AnActionEvent e) {
          e.getPresentation().setEnabled(myEnabled);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          onSpecifyUrlButtonClicked();
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
          return ActionUpdateThread.EDT;
        }
      };
      specifyUrlButton.setShortcutSet(CustomShortcutSet.fromString("alt S"));
      toolbarDecorator.addExtraAction(specifyUrlButton);
    }

    private void onSpecifyUrlButtonClicked() {
      String defaultDocsUrl = mySdk == null ? "" : StringUtil.notNullize(((SdkType)mySdk.getSdkType()).getDefaultDocumentationUrl(mySdk));
      VirtualFile virtualFile = Util.showSpecifyJavadocUrlDialog(myPanel, defaultDocsUrl);
      if (virtualFile != null) {
        addElement(virtualFile);
        setModified(true);
        requestDefaultFocus();
        setSelectedRoots(new VirtualFile[]{virtualFile});
      }
    }

    @Override
    protected VirtualFile[] adjustAddedFileSet(Component component, VirtualFile[] files) {
      JavadocQuarantineStatusCleaner.cleanIfNeeded(files);

      for (int i = 0; i < files.length; i++) {
        VirtualFile file = files[i], docRoot = null;

        if (file.getName().equalsIgnoreCase("docs")) {
          docRoot = file.findChild("api");
        }
        else if (file.getFileSystem() instanceof ArchiveFileSystem && file.getParent() == null) {
          docRoot = file.findFileByRelativePath("docs/api");
        }

        if (docRoot != null) files[i] = docRoot;
      }

      return files;
    }
  }
}