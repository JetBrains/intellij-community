package org.jetbrains.jps.builders;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.Function;
import junit.framework.Assert;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.JpsPathUtil;
import org.jetbrains.jps.Project;
import org.jetbrains.jps.api.CanceledStatus;
import org.jetbrains.jps.cmdline.ClasspathBootstrap;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.idea.IdeaProjectLoader;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.fs.BuildFSState;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.storage.BuildDataManager;
import org.jetbrains.jps.incremental.storage.ProjectTimestamps;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.JpsJavaSdkType;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.library.JpsTypedLibrary;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.serialization.JpsProjectLoader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author nik
 */
public abstract class JpsBuildTestCase extends UsefulTestCase {
  protected JpsProject myJpsProject;
  protected JpsModel myModel;
  protected Project myProject;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myProject = new Project();
    myProject.setProjectName(getProjectName());
    myModel = JpsElementFactory.getInstance().createModel();
    myJpsProject = myModel.getProject();
    Utils.setSystemRoot(FileUtil.createTempDirectory("compile-server", null));
  }

  protected JpsSdk<JpsDummyElement> initJdk(final String name) {
    try {
      return initJdk(name, FileUtil.toSystemIndependentName(ClasspathBootstrap.getResourcePath(Object.class).getCanonicalPath()));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected JpsSdk<JpsDummyElement> initJdk(final String name, final String path) {
    String homePath = System.getProperty("java.home");
    String versionString = System.getProperty("java.version");
    JpsTypedLibrary<JpsSdk<JpsDummyElement>> jdk = myModel.getGlobal().addSdk(name, homePath, versionString, JpsJavaSdkType.INSTANCE,
                                                                              JpsElementFactory.getInstance().createDummyElement());
    jdk.addRoot(JpsPathUtil.pathToUrl(path), JpsOrderRootType.COMPILED);
    return jdk.getProperties();
  }

  protected String getProjectName() {
    return StringUtil.decapitalize(StringUtil.trimStart(getName(), "test"));
  }

  protected ProjectDescriptor createProjectDescriptor(final BuildLoggingManager buildLoggingManager) {
    try {
      final File dataStorageRoot = Utils.getDataStorageRoot(myProject);
      ProjectTimestamps timestamps = new ProjectTimestamps(dataStorageRoot);
      BuildDataManager dataManager = new BuildDataManager(dataStorageRoot, true);
      return new ProjectDescriptor(myProject, myModel, new BuildFSState(true), timestamps, dataManager, buildLoggingManager);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected void loadProject(String projectPath) {
    loadProject(projectPath, Collections.<String, String>emptyMap());
  }

  protected void loadProject(String projectPath,
                             Map<String, String> pathVariables) {
    try {
      String testDataRootPath = getTestDataRootPath();
      String fullProjectPath = FileUtil.toSystemDependentName(testDataRootPath != null ? testDataRootPath + "/" + projectPath : projectPath);
      pathVariables = addPathVariables(pathVariables);
      IdeaProjectLoader.loadFromPath(myProject, fullProjectPath, pathVariables);
      JpsProjectLoader.loadProject(myJpsProject, pathVariables, fullProjectPath);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected Map<String, String> addPathVariables(Map<String, String> pathVariables) {
    return pathVariables;
  }

  @Nullable
  protected String getTestDataRootPath() {
    return null;
  }


  protected static IncProjectBuilder createBuilder(ProjectDescriptor projectDescriptor) {
    return new IncProjectBuilder(projectDescriptor, BuilderRegistry.getInstance(), Collections.<String, String>emptyMap(), CanceledStatus.NULL, null);
  }

  protected static void doBuild(IncProjectBuilder builder, CompileScope scope, boolean shouldFail, final boolean isMake,
                                final boolean isRebuild) {
    final List<BuildMessage> errorMessages = new ArrayList<BuildMessage>();
    final List<BuildMessage> infoMessages = new ArrayList<BuildMessage>();
    builder.addMessageHandler(new MessageHandler() {
      @Override
      public void processMessage(BuildMessage msg) {
        if (msg.getKind() == BuildMessage.Kind.ERROR) {
          errorMessages.add(msg);
        }
        else {
          infoMessages.add(msg);
        }
      }
    });
    try {
      builder.build(scope, isMake, isRebuild, false);
    }
    catch (RebuildRequestedException e) {
      Assert.fail(e.getMessage());
    }
    if (shouldFail) {
      Assert.assertFalse("Build not failed as expected", errorMessages.isEmpty());
    }
    else {
      final Function<BuildMessage,String> toStringFunction = StringUtil.createToStringFunction(BuildMessage.class);
      Assert.assertTrue("Build failed. \nErrors:\n" + StringUtil.join(errorMessages, toStringFunction, "\n") +
                        "\nInfo messages:\n" + StringUtil.join(infoMessages, toStringFunction, "\n"), errorMessages.isEmpty());
    }
  }
}
