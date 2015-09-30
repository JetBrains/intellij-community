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
import com.intellij.openapi.project.DumbModePermission;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.LibraryTableModifiableModelProvider;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.classpath.ChangeLibraryLevelActionBase;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.*;
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
    final LibraryProjectStructureElement libraryElement = (LibraryProjectStructureElement)selectedElement;
    final LibraryEx oldLibrary = (LibraryEx)context.getLibrary(libraryElement.getLibrary().getName(), mySourceConfigurable.getLevel());
    LOG.assertTrue(oldLibrary != null);

    DumbService.allowStartingDumbModeInside(DumbModePermission.MAY_START_BACKGROUND, new Runnable() {
      @Override
      public void run() {
        final Library newLibrary = doCopy(oldLibrary);
        if (newLibrary == null) return;

        final Collection<ProjectStructureElementUsage> usages = context.getDaemonAnalyzer().getUsages(libraryElement);
        for (ProjectStructureElementUsage usage : usages) {
          usage.replaceElement(new LibraryProjectStructureElement(context, newLibrary));
        }

        if (!myCopy) {
          mySourceConfigurable.removeLibrary(libraryElement);
        }
        ProjectStructureConfigurable.getInstance(myProject).selectProjectOrGlobalLibrary(newLibrary, true);
      }
    });
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
