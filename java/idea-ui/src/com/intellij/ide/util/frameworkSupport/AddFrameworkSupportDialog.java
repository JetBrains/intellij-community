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
package com.intellij.ide.util.frameworkSupport;

import com.intellij.facet.impl.DefaultFacetsProvider;
import com.intellij.framework.addSupport.FrameworkSupportInModuleProvider;
import com.intellij.ide.util.newProjectWizard.AddSupportForFrameworksPanel;
import com.intellij.ide.util.newProjectWizard.impl.FrameworkSupportModelBase;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ProjectBundle;
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

/**
 * @author nik
 */
public class AddFrameworkSupportDialog extends DialogWrapper {
  private final AddSupportForFrameworksPanel myAddSupportPanel;
  private final Module myModule;

  private AddFrameworkSupportDialog(@NotNull Module module, final @NotNull String contentRootPath, final List<FrameworkSupportInModuleProvider> providers) {
    super(module.getProject(), true);
    setTitle(ProjectBundle.message("dialog.title.add.frameworks.support"));
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

  protected JComponent createCenterPanel() {
    return myAddSupportPanel.getMainPanel();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myAddSupportPanel.getFrameworksTree();
  }
}
