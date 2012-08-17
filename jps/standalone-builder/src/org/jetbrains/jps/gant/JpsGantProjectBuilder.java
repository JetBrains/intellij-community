package org.jetbrains.jps.gant;

import com.intellij.openapi.util.io.FileUtil;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.jetbrains.jps.api.BuildType;
import org.jetbrains.jps.build.Standalone;
import org.jetbrains.jps.cmdline.JpsModelLoader;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.java.JpsJavaClasspathKind;
import org.jetbrains.jps.model.java.JpsJavaDependenciesEnumerator;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.util.*;

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
  private boolean myDryRun;
  private BuildInfoPrinter myBuildInfoPrinter;

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

  public void setDryRun(boolean dryRun) {
    myDryRun = dryRun;
  }

  public void setTargetFolder(String targetFolder) {
    String url = "file://" + FileUtil.toSystemIndependentName(targetFolder);
    JpsJavaExtensionService.getInstance().getOrCreateProjectExtension(myModel.getProject()).setOutputUrl(url);
  }

  public boolean isCompressJars() {
    return myCompressJars;
  }

  public void setCompressJars(boolean compressJars) {
    myCompressJars = compressJars;
  }

  public void setBuildInfoPrinter(BuildInfoPrinter printer) {
    myBuildInfoPrinter = printer;
  }

  public void setUseInProcessJavac() {
    //doesn't make sense for new builders
  }

  public void setArrangeModuleCyclesOutputs() {
    //doesn't make sense for new builders
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
    if (myBuildInfoPrinter != null) {
      myBuildInfoPrinter.printProgressMessage(this, message);
    }
    else {
      myProject.log(message, Project.MSG_INFO);
    }
  }

  public void setDataStorageRoot(File dataStorageRoot) {
    myDataStorageRoot = dataStorageRoot;
  }

  public void cleanOutput() {
    if (myDryRun) {
      info("Cleaning skipped as we're running dry");
      return;
    }

    for (JpsModule module : myModel.getProject().getModules()) {
      for (boolean test : new boolean[]{false, true}) {
        File output = JpsJavaExtensionService.getInstance().getOutputDirectory(module, test);
        if (output != null) {
          FileUtil.delete(output);
        }
      }
    }
  }

  public void makeModule(JpsModule module) {
    runBuild(getModuleDependencies(module, false), false);
  }

  public void makeModuleTests(JpsModule module) {
    runBuild(getModuleDependencies(module, true), true);
  }

  public void buildAll() {
    runBuild(Collections.<String>emptySet(), true);
  }

  public void buildProduction() {
    runBuild(Collections.<String>emptySet(), false);
  }

  public void exportModuleOutputProperties() {
    for (JpsModule module : myModel.getProject().getModules()) {
      for (boolean test : new boolean[]{true, false}) {
        myProject.setProperty("module." + module.getName() + ".output." + (test ? "test" : "main"), getModuleOutput(module, test));
      }
    }

  }

  private static Set<String> getModuleDependencies(JpsModule module, boolean includeTests) {
    Set<JpsModule> modules = JpsJavaExtensionService.dependencies(module).recursively().includedIn(JpsJavaClasspathKind.compile(includeTests)).getModules();
    Set<String> names = new HashSet<String>();
    for (JpsModule depModule : modules) {
      names.add(depModule.getName());
    }
    return names;
  }

  private void runBuild(final Set<String> modulesSet, boolean includeTests) {
    if (!myDryRun) {
      info("Starting build, caches are saved to " + myDataStorageRoot.getAbsolutePath());
      Standalone.runBuild(myModelLoader, myDataStorageRoot, BuildType.PROJECT_REBUILD, modulesSet, Collections.<String>emptyList(),
                          includeTests);
    }
    else {
      info("Building skipped as we're running dry");
    }
  }

  public String getModuleOutput(JpsModule module, boolean forTests) {
    File directory = JpsJavaExtensionService.getInstance().getOutputDirectory(module, forTests);
    return directory != null ? directory.getAbsolutePath() : null;
  }

  public List<String> moduleRuntimeClasspath(JpsModule module, boolean forTests) {
    JpsJavaDependenciesEnumerator enumerator = JpsJavaExtensionService.dependencies(module).recursively().includedIn(JpsJavaClasspathKind.runtime(forTests));
    Collection<File> roots = enumerator.classes().getRoots();
    List<String> result = new ArrayList<String>();
    for (File root : roots) {
      result.add(root.getAbsolutePath());
    }
    return result;
  }
}
