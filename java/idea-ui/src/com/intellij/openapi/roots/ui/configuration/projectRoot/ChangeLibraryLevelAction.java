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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.ui.configuration.LibraryTableModifiableModelProvider;
import com.intellij.openapi.roots.ui.configuration.classpath.ChangeLibraryLevelActionBase;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

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
    super(project, targetConfigurable.getLibraryTablePresentation().getDisplayName(true), targetConfigurable.getLevel());
    myParentComponent = parentComponent;
    mySourceConfigurable = sourceConfigurable;
    myTargetConfigurable = targetConfigurable;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Object selected = mySourceConfigurable.getSelectedObject();
    if (!(selected instanceof LibraryEx)) return;
    final LibraryEx library = (LibraryEx)selected;
    final LibraryEx actualLibrary = (LibraryEx)mySourceConfigurable.myContext.getLibrary(library.getName(), mySourceConfigurable.getLevel());
    LOG.assertTrue(actualLibrary != null);
    doAction(library);
  }

  @Override
  protected boolean isEnabled() {
    return mySourceConfigurable.getSelectedObject() instanceof LibraryEx;
  }

  @Override
  protected boolean isCopy() {
    return true;
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
