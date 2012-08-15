package org.jetbrains.jps.gant;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.jetbrains.jps.api.BuildType;
import org.jetbrains.jps.build.Standalone;
import org.jetbrains.jps.cmdline.JpsModelLoader;
import org.jetbrains.jps.model.JpsModel;

import java.io.File;
import java.util.Collections;

/**
 * @author nik
 */
public class JpsGantProjectBuilder {
  private final Project myProject;
  private final JpsModel myModel;
  private final org.jetbrains.jps.Project myOldProject;
  private boolean myCompressJars;
  private File myDataStorageRoot;
  private JpsModelLoader myModelLoader;

  public JpsGantProjectBuilder(Project project, JpsModel model, org.jetbrains.jps.Project oldProject) {
    myProject = project;
    myModel = model;
    myOldProject = oldProject;
    myModelLoader = new JpsModelLoader() {
      @Override
      public JpsModel loadModel() {
        return myModel;
      }

      @Override
      public org.jetbrains.jps.Project loadOldProject() {
        return myOldProject;
      }
    };
  }

  public boolean isCompressJars() {
    return myCompressJars;
  }

  public void setCompressJars(boolean compressJars) {
    myCompressJars = compressJars;
  }

  public void error(String message) {
    throw new BuildException(message);
  }

  public void warning(String message) {
    myProject.log(message, Project.MSG_WARN);
  }

  public void info(String message) {
    myProject.log(message, Project.MSG_INFO);
  }

  public void stage(String message) {
    myProject.log(message, Project.MSG_INFO);
  }

  public void setDataStorageRoot(File dataStorageRoot) {
    myDataStorageRoot = dataStorageRoot;
  }

  public void rebuildModules() {
    Standalone.runBuild(myModelLoader, myDataStorageRoot, BuildType.PROJECT_REBUILD, Collections.<String>emptySet(), Collections.<String>emptyList());
  }
}
