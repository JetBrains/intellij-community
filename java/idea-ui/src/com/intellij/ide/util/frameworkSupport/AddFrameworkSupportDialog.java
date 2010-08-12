/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package com.intellij.ide.util.frameworkSupport;

import com.intellij.CommonBundle;
import com.intellij.ide.util.newProjectWizard.AddSupportForFrameworksPanel;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainerFactory;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * @author nik
 */
public class AddFrameworkSupportDialog extends DialogWrapper {
  private final AddSupportForFrameworksPanel myAddSupportPanel;
  private final Module myModule;

  private AddFrameworkSupportDialog(@NotNull Module module, final String contentRootPath, final List<FrameworkSupportProvider> providers) {
    super(module.getProject(), true);
    setTitle(ProjectBundle.message("dialog.title.add.frameworks.support"));
    myModule = module;
    myAddSupportPanel = new AddSupportForFrameworksPanel(providers, LibrariesContainerFactory.createContainer(module.getProject()), null,
                                                         new Computable<String>() {
      public String compute() {
        return contentRootPath;
      }
    }) {

      @Override
      protected void onFrameworkStateChanged() {
        setOKActionEnabled(isOKActionEnabled());
      }
    };
    
    setOKActionEnabled(isOKActionEnabled());
    Disposer.register(myDisposable, myAddSupportPanel);
    init();
  }

  @Nullable
  public static AddFrameworkSupportDialog createDialog(@NotNull Module module) {
    VirtualFile[] roots = ModuleRootManager.getInstance(module).getContentRoots();
    if (roots.length == 0) return null;

    List<FrameworkSupportProvider> providers = FrameworkSupportUtil.getProviders(module);
    if (providers.isEmpty()) return null;

    return new AddFrameworkSupportDialog(module, roots[0].getPath(), providers);
  }

  public static boolean isAvailable(@NotNull Module module) {
    VirtualFile[] roots = ModuleRootManager.getInstance(module).getContentRoots();
    return roots.length != 0 && FrameworkSupportUtil.hasProviders(module);
  }

  @Override
  public boolean isOKActionEnabled() {
    return myAddSupportPanel.hasSelectedFrameworks();
  }

  protected void doOKAction() {
    if (myAddSupportPanel.hasSelectedFrameworks()) {
      if (!myAddSupportPanel.downloadLibraries()) {
        int answer = Messages.showYesNoDialog(myAddSupportPanel.getMainPanel(),
                                              ProjectBundle.message("warning.message.some.required.libraries.wasn.t.downloaded"),
                                              CommonBundle.getWarningTitle(), Messages.getWarningIcon());
        if (answer != 0) {
          return;
        }
      }

      new WriteAction() {
        protected void run(final Result result) {
          ModifiableRootModel model = ModuleRootManager.getInstance(myModule).getModifiableModel();
          myAddSupportPanel.addSupport(myModule, model);
          model.commit();
        }
      }.execute();
    }
    super.doOKAction();
  }

  @Override
  protected String getDimensionServiceKey() {
    return "#com.intellij.ide.util.frameworkSupport.AddFrameworkSupportDialog";
  }

  @Override
  protected String getHelpId() {
    return "reference.frameworks.support.dialog";
  }

  protected JComponent createCenterPanel() {
    return myAddSupportPanel.getMainPanel();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myAddSupportPanel.getFrameworksTree();
  }
}
