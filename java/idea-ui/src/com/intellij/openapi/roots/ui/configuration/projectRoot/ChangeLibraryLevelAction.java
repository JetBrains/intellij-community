// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.LibraryTableModifiableModelProvider;
import com.intellij.openapi.roots.ui.configuration.classpath.ChangeLibraryLevelActionBase;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.LibraryProjectStructureElement;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureElement;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureElementUsage;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;

public class ChangeLibraryLevelAction extends ChangeLibraryLevelActionBase {
  private static final Logger LOG = Logger.getInstance(ChangeLibraryLevelAction.class);
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
  public void actionPerformed(@NotNull AnActionEvent e) {
    final ProjectStructureElement selectedElement = mySourceConfigurable.getSelectedElement();
    if (!(selectedElement instanceof LibraryProjectStructureElement libraryElement)) return;
    final StructureConfigurableContext context = mySourceConfigurable.myContext;
    final LibraryEx oldLibrary = (LibraryEx)context.getLibrary(libraryElement.getLibrary().getName(), mySourceConfigurable.getLevel());
    LOG.assertTrue(oldLibrary != null);

    final Library newLibrary = doCopy(oldLibrary);
    if (newLibrary == null) return;

    final Collection<ProjectStructureElementUsage> usages = context.getDaemonAnalyzer().getUsages(libraryElement);
    for (ProjectStructureElementUsage usage : usages) {
      usage.replaceElement(new LibraryProjectStructureElement(context, newLibrary));
    }

    if (!myCopy) {
      mySourceConfigurable.removeLibrary(libraryElement);
    }
    myTargetConfigurable.getProjectStructureConfigurable().selectProjectOrGlobalLibrary(newLibrary, true);
  }

  @Override
  protected boolean isEnabled() {
    return mySourceConfigurable.getSelectedElement() instanceof LibraryProjectStructureElement;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
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
