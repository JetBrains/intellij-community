package com.intellij.openapi.roots.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;

/**
 *  @author dsl
 */
public class ModuleRootEventImpl extends ModuleRootEvent {
  private final boolean myFiletypes;
  private final boolean myDumbness;

  public ModuleRootEventImpl (final Project project, boolean filetypes) {
    this(project, filetypes, false);
  }

  public ModuleRootEventImpl(Project project, boolean filetypes, boolean dumbness) {
    super(project);
    myFiletypes = filetypes;
    myDumbness = dumbness;
  }

  public boolean isCausedByFileTypesChange() {
    return myFiletypes;
  }

  @Override
  public boolean isCausedByDumbnessChange() {
    return myDumbness;
  }

}
