// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers;

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.browsers.impl.WebBrowserServiceImpl;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.AncestorListenerAdapter;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.util.Url;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.AncestorEvent;

public final class StartBrowserPanel {
  private JCheckBox myStartBrowserCheckBox;
  private ComboboxWithBrowseButton myBrowserComboBox;

  private JCheckBox myStartJavaScriptDebuggerCheckBox;

  private TextFieldWithBrowseButton myUrlField;
  private BrowserSelector myBrowserSelector;

  private JPanel myRoot;

  public StartBrowserPanel() {
    myStartJavaScriptDebuggerCheckBox.setVisible(JavaScriptDebuggerStarter.Util.hasStarters());
    myRoot.addAncestorListener(new AncestorListenerAdapter() {
      @Override
      public void ancestorAdded(AncestorEvent event) {
        myRoot.removeAncestorListener(this);

        Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(myRoot));
        if (project == null) {
          DataManager.getInstance()
                     .getDataContextFromFocusAsync()
                     .onSuccess(context -> {
                       Project project1 = CommonDataKeys.PROJECT.getData(context);
                       if (project1 == null) {
                         // IDEA-118202
                         project1 = ProjectManager.getInstance().getDefaultProject();
                       }
                       setupUrlField(myUrlField, project1);
                     });
        }
        else {
          setupUrlField(myUrlField, project);
        }
      }
    });
  }

  public @NotNull JPanel getComponent() {
    return myRoot;
  }

  public @Nullable String getNormalizedUrl() {
    String url = StringUtil.nullize(myUrlField.getText(), true);
    if (url != null) {
      url = url.trim();
      if (!URLUtil.containsScheme(url)) {
        return VirtualFileManager.constructUrl(URLUtil.HTTP_PROTOCOL, url);
      }
    }
    return url;
  }

  public void setUrl(@NlsSafe @Nullable String url) {
    myUrlField.setText(url);
  }

  public boolean isSelected() {
    return myStartBrowserCheckBox.isSelected();
  }

  public void setSelected(boolean value) {
    myStartBrowserCheckBox.setSelected(value);
  }

  public JCheckBox getStartJavaScriptDebuggerCheckBox() {
    return myStartJavaScriptDebuggerCheckBox;
  }

  public BrowserSelector getBrowserSelector() {
    return myBrowserSelector;
  }

  private void createUIComponents() {
    myBrowserSelector = new BrowserSelector();
    myBrowserComboBox = (ComboboxWithBrowseButton)myBrowserSelector.getMainComponent();
    myBrowserComboBox.getComboBox().addActionListener(e -> {
      myStartJavaScriptDebuggerCheckBox.setEnabled(isDebugAllowed(myBrowserSelector.getSelected()));
    });
  }

  private static @Nullable Url virtualFileToUrl(@NotNull VirtualFile file, @NotNull Project project) {
    PsiFile psiFile = ReadAction.compute(() -> PsiManager.getInstance(project).findFile(file));
    return WebBrowserServiceImpl.getDebuggableUrl(psiFile);
  }

  public @NotNull StartBrowserSettings createSettings() {
    StartBrowserSettings browserSettings = new StartBrowserSettings();
    browserSettings.setSelected(isSelected());
    browserSettings.setBrowser(myBrowserSelector.getSelected());
    boolean isDebugEnabled = myStartJavaScriptDebuggerCheckBox.isSelected() && isDebugAllowed(myBrowserSelector.getSelected());
    browserSettings.setStartJavaScriptDebugger(isDebugEnabled);
    browserSettings.setUrl(getNormalizedUrl());
    return browserSettings;
  }

  public void setFromSettings(StartBrowserSettings settings) {
    setSelected(settings.isSelected());
    setUrl(settings.getUrl());
    myStartJavaScriptDebuggerCheckBox.setSelected(settings.isStartJavaScriptDebugger());
    myBrowserSelector.setSelected(settings.getBrowser());
  }

  // we don't allow starting debug session for the default && non-chromium browsers
  private static boolean isDebugAllowed(@Nullable WebBrowser browser) {
    return browser != null && browser.getFamily().equals(BrowserFamily.CHROME);
  }

  public static void setupUrlField(@NotNull TextFieldWithBrowseButton field, final @NotNull Project project) {
    FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, false, false) {
      @Override
      public boolean isFileSelectable(@Nullable VirtualFile file) {
        if (file == null) return false;
        return WebBrowserXmlService.getInstance().isHtmlFile(file) || virtualFileToUrl(file, project) != null;
      }
    };
    descriptor.setTitle(IdeBundle.message("javascript.debugger.settings.choose.file.title"));
    descriptor.setDescription(IdeBundle.message("javascript.debugger.settings.choose.file.subtitle"));
    descriptor.setRoots(ProjectRootManager.getInstance(project).getContentRoots());

    field.addBrowseFolderListener(new TextBrowseFolderListener(descriptor, project) {
      @Override
      protected @NotNull String chosenFileToResultingText(@NotNull VirtualFile chosenFile) {
        if (chosenFile.isDirectory()) {
          return chosenFile.getPath();
        }

        Url url = virtualFileToUrl(chosenFile, project);
        return url == null ? chosenFile.getUrl() : url.toDecodedForm();
      }
    });
  }
}