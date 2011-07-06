/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.impl.OrderEntryUtil;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.LibraryTableModifiableModelProvider;
import com.intellij.openapi.roots.ui.configuration.ModuleEditor;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.artifacts.UsageInArtifact;
import com.intellij.openapi.roots.ui.configuration.classpath.ChangeLibraryLevelActionBase;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.*;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingElementFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;

/**
 * @author nik
 */
public class ChangeLibraryLevelAction extends ChangeLibraryLevelActionBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.ui.configuration.projectRoot.ChangeLibraryLevelAction");
  private final JComponent myParentComponent;
  private final BaseLibrariesConfigurable mySourceConfigurable;
  private final BaseLibrariesConfigurable myTargetConfigurable;

  public ChangeLibraryLevelAction(@NotNull Project project, @NotNull JComponent parentComponent,
                                  @NotNull BaseLibrariesConfigurable sourceConfigurable,
                                  @NotNull BaseLibrariesConfigurable targetConfigurable) {
    super(project, targetConfigurable.getLibraryTablePresentation().getDisplayName(true), targetConfigurable.getLevel(),
          sourceConfigurable instanceof GlobalLibrariesConfigurable);
    myParentComponent = parentComponent;
    mySourceConfigurable = sourceConfigurable;
    myTargetConfigurable = targetConfigurable;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final ProjectStructureElement selectedElement = mySourceConfigurable.getSelectedElement();
    if (!(selectedElement instanceof LibraryProjectStructureElement)) return;
    final StructureConfigurableContext context = mySourceConfigurable.myContext;
    final Library originalLibrary = ((LibraryProjectStructureElement)selectedElement).getLibrary();
    final LibraryEx oldLibrary = (LibraryEx)context.getLibrary(originalLibrary.getName(), mySourceConfigurable.getLevel());
    LOG.assertTrue(oldLibrary != null);
    final Library newLibrary = doCopy(oldLibrary);
    if (newLibrary == null) return;

    final ModulesConfigurator configurator = context.getModulesConfigurator();
    final Collection<ProjectStructureElementUsage> usages = context.getDaemonAnalyzer().getUsages(selectedElement);
    for (ProjectStructureElementUsage usage : usages) {
      if (usage instanceof UsageInModuleClasspath) {
        final Module module = ((UsageInModuleClasspath)usage).getModule();
        final ModuleEditor editor = configurator.getModuleEditor(module);
        if (editor != null) {
          final ModifiableRootModel rootModel = editor.getModifiableRootModelProxy();
          OrderEntryUtil.replaceLibrary(rootModel, oldLibrary, newLibrary);
          context.getDaemonAnalyzer().queueUpdate(new ModuleProjectStructureElement(context, module));
        }
      }
      else if (usage instanceof UsageInArtifact) {
        final PackagingElement<?> libraryElement = PackagingElementFactory.getInstance().createLibraryFiles(newLibrary.getName(),
                                                                                                            newLibrary.getTable().getTableLevel(), null);
        ((UsageInArtifact)usage).replaceElement(libraryElement);
      }
    }

    if (!myCopy) {
      mySourceConfigurable.getModelProvider().getModifiableModel().removeLibrary(originalLibrary);
      context.getDaemonAnalyzer().removeElement(selectedElement);
      mySourceConfigurable.removeLibraryNode(originalLibrary);
    }
    ProjectStructureConfigurable.getInstance(myProject).selectProjectOrGlobalLibrary(newLibrary, true);
  }

  @Override
  protected boolean isEnabled() {
    return mySourceConfigurable.getSelectedElement() instanceof LibraryProjectStructureElement;
  }

  @Override
  protected LibraryTableModifiableModelProvider getModifiableTableModelProvider() {
    return myTargetConfigurable.getModelProvider();
  }

  @Override
  protected JComponent getParentComponent() {
    return myParentComponent;
  }
}
