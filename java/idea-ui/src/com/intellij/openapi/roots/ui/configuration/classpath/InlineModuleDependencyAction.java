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
package com.intellij.openapi.roots.ui.configuration.classpath;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.ClonableOrderEntry;
import com.intellij.openapi.roots.impl.OrderEntryUtil;
import com.intellij.openapi.roots.impl.ProjectRootManagerImpl;
import com.intellij.openapi.roots.impl.RootModelImpl;
import com.intellij.openapi.roots.ui.configuration.ModuleEditor;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ModuleProjectStructureElement;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;

import java.lang.reflect.Proxy;

/**
 * @author nik
 */
public class InlineModuleDependencyAction extends AnAction {
  private static final Logger LOG = Logger.getInstance(InlineModuleDependencyAction.class);
  private final ClasspathPanelImpl myClasspathPanel;

  public InlineModuleDependencyAction(ClasspathPanelImpl classpathPanel) {
    super("Inline Module Dependency", "Replace dependency on a module without source roots by the list of its dependencies", null);
    myClasspathPanel = classpathPanel;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    OrderEntry selectedEntry = myClasspathPanel.getSelectedEntry();
    if (!(selectedEntry instanceof ModuleOrderEntry)) return;

    ModuleOrderEntry entryToInline = (ModuleOrderEntry)selectedEntry;
    Module module = entryToInline.getModule();
    if (module == null) return;

    ModifiableRootModel model = myClasspathPanel.getRootModel();
    int toInlineIndex = findModuleEntryIndex(model, module);
    if (toInlineIndex == -1) return;

    model.removeOrderEntry(entryToInline);

    RootModelImpl modelImpl;
    if (Proxy.isProxyClass(model.getClass())) {
      modelImpl = (RootModelImpl)((ModuleEditor.ProxyDelegateAccessor)Proxy.getInvocationHandler(model)).getDelegate();
    }
    else {
      modelImpl = (RootModelImpl)model;
    }
    int addedCount = 0;
    ModuleRootModel otherModel = myClasspathPanel.getModuleConfigurationState().getModulesProvider().getRootModel(module);
    ProjectRootManagerImpl rootManager = ProjectRootManagerImpl.getInstanceImpl(myClasspathPanel.getProject());
    VirtualFilePointerManager virtualFilePointerManager = VirtualFilePointerManager.getInstance();
    for (OrderEntry entry : otherModel.getOrderEntries()) {
      if (entry instanceof LibraryOrderEntry || entry instanceof ModuleOrderEntry) {
        LOG.assertTrue(entry instanceof ClonableOrderEntry, entry);
        ExportableOrderEntry entryToCopy = (ExportableOrderEntry)entry;
        ExportableOrderEntry cloned = (ExportableOrderEntry)((ClonableOrderEntry)entry).cloneEntry(modelImpl, rootManager, virtualFilePointerManager);
        cloned.setExported(entryToInline.isExported() && entryToCopy.isExported());
        cloned.setScope(OrderEntryUtil.intersectScopes(entryToInline.getScope(), entryToCopy.getScope()));
        model.addOrderEntry(cloned);
        addedCount++;
      }
    }

    OrderEntry[] oldEntries = model.getOrderEntries();
    OrderEntry[] newEntries = new OrderEntry[oldEntries.length];
    System.arraycopy(oldEntries, 0, newEntries, 0, toInlineIndex);
    System.arraycopy(oldEntries, oldEntries.length - addedCount, newEntries, toInlineIndex, addedCount);
    System.arraycopy(oldEntries, toInlineIndex, newEntries, toInlineIndex + addedCount, oldEntries.length - toInlineIndex - addedCount);
    model.rearrangeOrderEntries(newEntries);

    StructureConfigurableContext context = ProjectStructureConfigurable.getInstance(myClasspathPanel.getProject()).getContext();
    context.getDaemonAnalyzer().queueUpdate(new ModuleProjectStructureElement(context, module));
  }

  private static int findModuleEntryIndex(ModifiableRootModel model, Module module) {
    OrderEntry[] entries = model.getOrderEntries();
    for (int i = 0; i < entries.length; i++) {
      OrderEntry entry = entries[i];
      if (entry instanceof ModuleOrderEntry && module.equals(((ModuleOrderEntry)entry).getModule())) {
        return i;
      }
    }
    return -1;
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(isEnabled());
  }

  private boolean isEnabled() {
    OrderEntry entry = myClasspathPanel.getSelectedEntry();
    if (!(entry instanceof ModuleOrderEntry)) return false;

    Module module = ((ModuleOrderEntry)entry).getModule();
    if (module == null) return false;

    ModuleRootModel model = myClasspathPanel.getModuleConfigurationState().getModulesProvider().getRootModel(module);
    return model.getSourceRootUrls().length == 0;
  }
}
