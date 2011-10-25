package com.intellij.ide.util.newProjectWizard;

import com.intellij.ide.util.importProject.ProjectDescriptor;

import java.util.Collection;

/**
 * @author nik
 */
public interface ProjectFromSourcesBuilder {
  Collection<DetectedProjectRoot> getProjectRoots(ProjectStructureDetector detector);

  ProjectDescriptor getProjectDescriptor(ProjectStructureDetector detector);

  String getBaseProjectPath();
}
