/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.compiler.packaging;

import com.intellij.testFramework.LiteFixture;
import com.intellij.openapi.compiler.make.*;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.DummyCompileContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.compiler.impl.make.BuildRecipeImpl;
import com.intellij.compiler.impl.make.FileCopyInstructionImpl;
import com.intellij.compiler.impl.make.JarAndCopyBuildInstructionImpl;
import com.intellij.compiler.impl.make.JavaeeModuleBuildInstructionImpl;
import com.intellij.compiler.impl.make.newImpl.*;
import com.intellij.mock.MockApplication;
import com.intellij.mock.MockLocalFileSystem;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;

/**
 * @author nik
 */
public abstract class IncrementalPackagingTestCase extends LiteFixture {
  protected void setUp() throws Exception {
    super.setUp();
    initApplication();
    final MockApplication application = getApplication();
    application.getPicoContainer().registerComponentInstance(LocalFileSystem.class, new MockLocalFileSystem());
  }

  protected BuildRecipeInfo start() {
    return new BuildRecipeInfo(null);
  }

  protected NewProcessingItem[] buildItems(final BuildRecipe buildRecipe, final MockBuildConfiguration mockBuildConfiguration) {
    final MockBuildParticipant participant = new MockBuildParticipant(mockBuildConfiguration, buildRecipe);
    final ProcessingItemsBuilderContext context = new ProcessingItemsBuilderContext();
    final DummyCompileContext compileContext = DummyCompileContext.getInstance();
    new ProcessingItemsBuilder(participant, compileContext, context).build();
    buildRecipe.visitInstructions(new BuildInstructionVisitor() {
      public boolean visitJ2EEModuleBuildInstruction(final JavaeeModuleBuildInstruction instruction) throws Exception {
        final BuildParticipant buildParticipant = new MockBuildParticipant(instruction.getBuildProperties(),
                                                                           instruction.getChildInstructions(compileContext));
        new ProcessingItemsBuilder(buildParticipant, compileContext, context).build();
        return true;
      }
    }, false);
    return context.getProcessingItems();
  }

  protected Map<JarInfo, Integer> fillAllJars(final NewProcessingItem[] items, final Set<JarInfo> jars) {
    final Map<JarInfo, Integer> deps = new HashMap<JarInfo, Integer>();
    for (NewProcessingItem item : items) {
      for (DestinationInfo destinationInfo : item.getDestinations()) {
        if (destinationInfo instanceof JarDestinationInfo) {
          final JarInfo info = ((JarDestinationInfo)destinationInfo).getJarInfo();
          if (!deps.containsKey(info)) {
            deps.put(info, 0);
          }
          addJars(info, jars, deps);
        }
      }
    }
    return deps;
  }

  private void addJars(final JarInfo jarInfo, final Set<JarInfo> jars, final Map<JarInfo, Integer> deps) {
    if (!jars.add(jarInfo)) return;
    for (JarDestinationInfo destination : jarInfo.getJarDestinations()) {
      final JarInfo info = destination.getJarInfo();
      deps.put(info, deps.get(jarInfo) + 1);
      addJars(info, jars, deps);
    }
  }

  protected String loadText(final File file) throws IOException {
    return StringUtil.convertLineSeparators(new String(FileUtil.loadFileText(file)));
  }

  protected static class MockBuildParticipant extends BuildParticipant {
    private BuildConfiguration myBuildConfiguration;
    private BuildRecipe myBuildRecipe;

    public MockBuildParticipant(final BuildConfiguration buildConfiguration, final BuildRecipe buildRecipe) {
      myBuildConfiguration = buildConfiguration;
      myBuildRecipe = buildRecipe;
    }

    public BuildRecipe getBuildInstructions(final CompileContext context) {
      return myBuildRecipe;
    }

    public BuildConfiguration getBuildConfiguration() {
      return myBuildConfiguration;
    }

    public String getConfigurationName() {
      throw new UnsupportedOperationException("'getConfigurationName' not implemented in " + getClass().getName());
    }

    public Module getModule() {
      return null;
    }
  }

  protected static class MockBuildConfiguration extends BuildConfiguration {
    private boolean myJarEnabled;
    private boolean myExplodedEnabled;
    private boolean myBuildExternalDependencies;
    private final String myExplodedPath;
    private final String myJarPath;

    public MockBuildConfiguration(final boolean explodedEnabled, final boolean jarEnabled, final boolean buildExternalDependencies) {
      this(explodedEnabled, jarEnabled, buildExternalDependencies, "/out/exploded", "/out/my.jar");
    }

    public MockBuildConfiguration(final boolean explodedEnabled, final boolean jarEnabled, final boolean buildExternalDependencies,
                                  final String explodedPath, String jarPath) {
      myExplodedPath = explodedPath;
      myJarPath = jarPath;
      myJarEnabled = jarEnabled;
      myExplodedEnabled = explodedEnabled;
      myBuildExternalDependencies = buildExternalDependencies;
    }

    @NonNls
    public String getArchiveExtension() {
      return "jar";
    }

    @Nullable
    public String getJarPath() {
      return myJarPath;
    }

    @Nullable
    public String getExplodedPath() {
      return myExplodedPath;
    }

    public boolean isJarEnabled() {
      return myJarEnabled;
    }

    public boolean isExplodedEnabled() {
      return myExplodedEnabled;
    }

    public boolean isBuildExternalDependencies() {
      return myBuildExternalDependencies;
    }
  }

  protected class BuildRecipeInfo {
    private ProcessingItemsBuilderTest.BuildRecipeInfo myParent;
    protected BuildRecipe myBuildRecipe = new BuildRecipeImpl();
    private int myCount = 2;
    @NonNls private static final String PROJECT_DIR = "project/";

    public BuildRecipeInfo(final ProcessingItemsBuilderTest.BuildRecipeInfo parent) {
      myParent = parent;
    }

    protected ProcessingItemsBuilderTest.BuildRecipeInfo copy(String sourceDir, final String outputRelativePath, String... files) {
      final String path = PROJECT_DIR + sourceDir;
      for (String file : files) {
        LocalFileSystem.getInstance().findFileByPath(path + "/" + file);
      }
      return add(new FileCopyInstructionImpl(new File(path), true, null, outputRelativePath, null));
    }

    protected ProcessingItemsBuilderTest.BuildRecipeInfo jar(String sourceDir, final String outputRelativePath, String... files) {
      final String path = PROJECT_DIR + sourceDir;
      for (String file : files) {
        LocalFileSystem.getInstance().findFileByPath(path + "/" + file);
      }
      return add(new JarAndCopyBuildInstructionImpl(null, new File(path), outputRelativePath, null));
    }

    protected ProcessingItemsBuilderTest.BuildRecipeInfo add(final BuildInstruction instruction) {
      myBuildRecipe.addInstruction(instruction);
      return this;
    }

    protected ProcessingItemsBuilderTest.BuildRecipeInfo copy(String sourceFile, final String outputRelativePath) {
      return add(new FileCopyInstructionImpl(new File(PROJECT_DIR + sourceFile), false, null, outputRelativePath, null));
    }

    protected ProcessingItemsBuilderTest.BuildRecipeInfo inner(String relativeOutput, boolean explodedEnabled, boolean jarEnabled, boolean buildExternalDependencies) {
      final ProcessingItemsBuilderTest.BuildRecipeInfo inner = new ProcessingItemsBuilderTest.BuildRecipeInfo(this);
      final MockBuildConfiguration configuration = new MockBuildConfiguration(explodedEnabled, jarEnabled, buildExternalDependencies,
                                                                              "/out" + myCount + "/exploded", "/out" + myCount + "/my.jar");
      myCount++;
      final MockBuildParticipant participant = new MockBuildParticipant(configuration, inner.myBuildRecipe);
      add(new JavaeeModuleBuildInstructionImpl(null, participant, relativeOutput));
      return inner;
    }

    protected ProcessingItemsBuilderTest.BuildRecipeInfo up() {
      return myParent;
    }
  }
}
