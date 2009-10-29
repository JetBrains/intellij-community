/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.compiler.packaging;

import com.intellij.compiler.impl.packagingCompiler.*;
import com.intellij.mock.MockApplication;
import com.intellij.mock.MockLocalFileSystem;
import com.intellij.mock.MockProgressIndicator;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.DummyCompileContext;
import com.intellij.openapi.compiler.make.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LiteFixture;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

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

  protected static PackagingProcessingItem[] buildItems(final BuildRecipe buildRecipe, final MockBuildConfiguration mockBuildConfiguration) {
    final MockBuildParticipant participant = new MockBuildParticipant(mockBuildConfiguration, buildRecipe);
    final DummyCompileContext compileContext = new MyDummyCompileContext();
    final OldProcessingItemsBuilderContext context = new OldProcessingItemsBuilderContext(compileContext);
    new ProcessingItemsBuilder(participant, context).build();
    buildRecipe.visitInstructions(new BuildInstructionVisitor() {
    }, false);
    return context.getProcessingItems();
  }

  protected static Map<JarInfo, Integer> fillAllJars(final PackagingProcessingItem[] items, final Set<JarInfo> jars) {
    final Map<JarInfo, Integer> deps = new HashMap<JarInfo, Integer>();
    for (PackagingProcessingItem item : items) {
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

  private static void addJars(final JarInfo jarInfo, final Set<JarInfo> jars, final Map<JarInfo, Integer> deps) {
    if (!jars.add(jarInfo)) return;
    for (JarDestinationInfo destination : jarInfo.getJarDestinations()) {
      final JarInfo info = destination.getJarInfo();
      deps.put(info, deps.get(jarInfo) + 1);
      addJars(info, jars, deps);
    }
  }

  protected static String loadText(final File file) throws IOException {
    return StringUtil.convertLineSeparators(new String(FileUtil.loadFileText(file)));
  }

  protected static List<Pair<JarInfo, String>> getJarsContent(final PackagingProcessingItem[] items) {
    Set<JarInfo> jars = new HashSet<JarInfo>();
    final Map<JarInfo, Integer> deps = fillAllJars(items, jars);

    List<Pair<JarInfo, String>> jarContent = new ArrayList<Pair<JarInfo, String>>();
    for (JarInfo jar : jars) {
      List<String> contentList = new ArrayList<String>();
      for (Pair<String, VirtualFile> pair : jar.getPackedFiles()) {
        String s = " " + pair.getSecond().getPath() + " -> " + pair.getFirst();
        contentList.add(s);
      }
      Collections.sort(contentList);
      StringBuilder content = new StringBuilder();
      for (String s : contentList) {
        content.append(s).append("\n");
      }
      jarContent.add(Pair.create(jar, content.toString()));
    }

    Collections.sort(jarContent, new Comparator<Pair<JarInfo, String>>() {
      public int compare(final Pair<JarInfo, String> o1, final Pair<JarInfo, String> o2) {
        final Integer d1 = deps.get(o1.getFirst());
        final Integer d2 = deps.get(o2.getFirst());
        if (!d1.equals(d2)) {
          return d1.compareTo(d2);
        }
        return o1.getSecond().compareTo(o2.getSecond());
      }
    });
    return jarContent;
  }

  protected static class MockBuildParticipant extends BuildParticipant {
    private final BuildConfiguration myBuildConfiguration;
    private final BuildRecipe myBuildRecipe;

    protected MockBuildParticipant(final BuildConfiguration buildConfiguration, final BuildRecipe buildRecipe) {
      myBuildConfiguration = buildConfiguration;
      myBuildRecipe = buildRecipe;
    }

    public BuildRecipe getBuildInstructions(final CompileContext context) {
      return myBuildRecipe;
    }

    public BuildConfiguration getBuildConfiguration() {
      return myBuildConfiguration;
    }

    public Module getModule() {
      return null;
    }
  }

  protected static class MockBuildConfiguration extends BuildConfiguration {
    private boolean myJarEnabled;
    private boolean myExplodedEnabled;
    private final String myExplodedPath;
    private final String myJarPath;

    protected MockBuildConfiguration(final boolean explodedEnabled, final boolean jarEnabled) {
      this(explodedEnabled, jarEnabled, "/out/exploded", "/out/my.jar");
    }

    protected MockBuildConfiguration(final boolean explodedEnabled, final boolean jarEnabled, @NonNls final String explodedPath, @NonNls String jarPath) {
      myExplodedPath = explodedPath;
      myJarPath = jarPath;
      myJarEnabled = jarEnabled;
      myExplodedEnabled = explodedEnabled;
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

  }

  protected class BuildRecipeInfo {
    private final ProcessingItemsBuilderTest.BuildRecipeInfo myParent;
    protected BuildRecipe myBuildRecipe = new BuildRecipeImpl();
    private int myCount = 2;
    @NonNls private static final String PROJECT_DIR = "project/";

    protected BuildRecipeInfo(final ProcessingItemsBuilderTest.BuildRecipeInfo parent) {
      myParent = parent;
    }

    protected ProcessingItemsBuilderTest.BuildRecipeInfo copy(String sourceDir, final String outputRelativePath, String... files) {
      final String path = PROJECT_DIR + sourceDir;
      for (String file : files) {
        VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(path + "/" + file);
        assertNotNull(virtualFile);
      }
      return add(new FileCopyInstructionImpl(new File(path), true, null, outputRelativePath, null));
    }

    protected ProcessingItemsBuilderTest.BuildRecipeInfo jar(String sourceDir, final String outputRelativePath, String... files) {
      final String path = PROJECT_DIR + sourceDir;
      for (String file : files) {
        VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(path + "/" + file);
        assertNotNull(virtualFile);
      }
      return add(new JarAndCopyBuildInstructionImpl(null, new File(path), outputRelativePath));
    }

    protected ProcessingItemsBuilderTest.BuildRecipeInfo add(final BuildInstruction instruction) {
      myBuildRecipe.addInstruction(instruction);
      return this;
    }

    protected ProcessingItemsBuilderTest.BuildRecipeInfo copy(String sourceFile, final String outputRelativePath) {
      return add(new FileCopyInstructionImpl(new File(PROJECT_DIR + sourceFile), false, null, outputRelativePath, null));
    }

    protected ProcessingItemsBuilderTest.BuildRecipeInfo up() {
      return myParent;
    }
  }

  protected static class MyDummyCompileContext extends DummyCompileContext {
    private final ProgressIndicator myProgressIndicator = new MockProgressIndicator();

    public ProgressIndicator getProgressIndicator() {
      return myProgressIndicator;
    }
  }
}
