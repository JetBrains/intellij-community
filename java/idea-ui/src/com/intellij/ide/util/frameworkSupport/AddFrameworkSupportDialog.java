// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.frameworkSupport;

import com.intellij.facet.impl.DefaultFacetsProvider;
import com.intellij.framework.addSupport.FrameworkSupportInModuleProvider;
import com.intellij.ide.JavaUiBundle;
import com.intellij.ide.util.newProjectWizard.AddSupportForFrameworksPanel;
import com.intellij.ide.util.newProjectWizard.impl.FrameworkSupportModelBase;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainerFactory;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public final class AddFrameworkSupportDialog extends DialogWrapper {
  private final AddSupportForFrameworksPanel myAddSupportPanel;
  private final Module myModule;

  private AddFrameworkSupportDialog(@NotNull Module module, final @NotNull String contentRootPath, final List<FrameworkSupportInModuleProvider> providers) {
    super(module.getProject(), true);
    setTitle(JavaUiBundle.message("dialog.title.add.frameworks.support"));
    myModule = module;
    final LibrariesContainer container = LibrariesContainerFactory.createContainer(module.getProject());
    final FrameworkSupportModelBase model = new FrameworkSupportModelImpl(module.getProject(), contentRootPath, container);
    myAddSupportPanel = new AddSupportForFrameworksPanel(providers, model, false, null) {
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

    List<FrameworkSupportInModuleProvider> providers = FrameworkSupportUtil.getProviders(module, DefaultFacetsProvider.INSTANCE);
    if (providers.isEmpty()) return null;

    return new AddFrameworkSupportDialog(module, roots[0].getPath(), providers);
  }

  public static boolean isAvailable(@NotNull Module module) {
    VirtualFile[] roots = ModuleRootManager.getInstance(module).getContentRoots();
    return roots.length != 0 && FrameworkSupportUtil.hasProviders(module, DefaultFacetsProvider.INSTANCE);
  }

  @Override
  public boolean isOKActionEnabled() {
    return myAddSupportPanel.hasSelectedFrameworks();
  }

  @Override
  protected void doOKAction() {
    if (myAddSupportPanel.hasSelectedFrameworks()) {
      if (!myAddSupportPanel.validate()) return;
      if (!myAddSupportPanel.downloadLibraries(myAddSupportPanel.getMainPanel())) return;

      WriteAction.run(() -> {
        ModifiableRootModel model = ModuleRootManager.getInstance(myModule).getModifiableModel();
        myAddSupportPanel.addSupport(myModule, model);
        model.commit();
      });
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

  @Override
  protected JComponent createCenterPanel() {
    return myAddSupportPanel.getMainPanel();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myAddSupportPanel.getFrameworksTree();
  }
}
