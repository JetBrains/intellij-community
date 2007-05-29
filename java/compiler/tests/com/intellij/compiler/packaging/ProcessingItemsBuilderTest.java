/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.compiler.packaging;

import com.intellij.compiler.impl.make.BuildRecipeImpl;
import com.intellij.compiler.impl.make.FileCopyInstructionImpl;
import com.intellij.compiler.impl.make.JarAndCopyBuildInstructionImpl;
import com.intellij.compiler.impl.make.JavaeeModuleBuildInstructionImpl;
import com.intellij.compiler.impl.make.newImpl.*;
import com.intellij.mock.MockApplication;
import com.intellij.mock.MockLocalFileSystem;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.DummyCompileContext;
import com.intellij.openapi.compiler.make.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.io.FileUtil;
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
public class ProcessingItemsBuilderTest extends LiteFixture {

  protected void setUp() throws Exception {
    super.setUp();
    initApplication();
    final MockApplication application = getApplication();
    application.getPicoContainer().registerComponentInstance(LocalFileSystem.class, new MockLocalFileSystem());
  }

  public void testCopyFileToExploded() throws Exception {
    doTest(true, false, true,
           start().copy("a.jsp", "/a.jsp"));
  }

  public void testCopyDirToExploded() throws Exception {
    doTest(true, false, true,
           start().copy("dir", "/out", "a.jsp", "b.jsp"));
  }

  public void testCopyFileToJar() throws Exception {
    doTest(false, true, true,
           start().copy("a.jsp", "/a.jsp"));
  }

  public void testCopyDirToJar() throws Exception {
    doTest(false, true, true,
           start().copy("dir", "/out", "a.jsp", "b.jsp"));
  }

  public void testJarDirectory() throws Exception {
    doTest(true, true, false,
           start().jar("dir", "/path/to/inner.jar", "a.html", "b.html"));
  }

  public void testCopyFileToParent() throws Exception {
    doTest(true, true, false, start()
      .copy("a.jsp", "../a.jsp")
      .copy("b.jsp", "b.jsp"));
  }

  public void testCopyFileToParentExternal() throws Exception {
    doTest(new MockBuildConfiguration(true, true, true, "/out/exploded", "/out2/my.jar"), start()
      .copy("a.jsp", "../a.jsp"));
  }

  public void testJarFileToParent() throws Exception {
    doTest(true, true, false, start()
      .jar("dir", "../a.jsp", "a.jsp")
      .copy("b.jsp", "b.jsp"));
  }

  public void testJarFileToParentExternal() throws Exception {
    doTest(new MockBuildConfiguration(true, true, true, "/out/exploded", "/out2/my.jar"), start()
      .jar("dir", "../a.jar", "a.jsp"));
  }

  
  public void testCopyDirToEar() throws Exception {
    doTest(true, true, false, start()
      .copy("a.jsp", "/a.jsp")
      .inner("w.war", false, false, false)
        .copy("b.jsp", "/b.jsp")
        .up()
      .copy("c.jsp", "/c.jsp")
    );
  }

  public void testCopyDirToWarAndEar() throws Exception {
    doTest(true, true, false, start()
      .copy("a.jsp", "/a.jsp")
      .inner("w.war", true, true, false)
        .copy("b.jsp", "/b.jsp")
        .up()
      .copy("c.jsp", "/c.jsp")
    );
  }

  public void testJarDirToEar() throws Exception {
    doTest(true, true, false, start()
      .inner("w.war", false, false, false)
        .jar("dir", "/a.jar", "a.jsp")
        .up()
    );
  }

  public void testJarDirToWarAndEar() throws Exception {
    doTest(true, true, false, start()
      .inner("w.war", true, true, false)
        .jar("dir", "/a.jar", "a.jsp")
        .up()
    );
  }

  public void testCopyExternalDepsToEar() throws Exception {
    doTest(true, true, false, start()
      .inner("w.war", false, false, false)
        .copy("a.jsp", "/a.jsp")
        .copy("b.jar", "../b.jar")
        .up());
  }

  public void testJarExternalDepsToEar() throws Exception {
    doTest(true, true, false, start()
      .inner("w.war", false, false, false)
        .copy("a.jsp", "/a.jsp")
        .jar("dir", "../d.jar", "b.jsp", "c.jsp")
        .up());
  }

  private BuildRecipeInfo start() {
    return new BuildRecipeInfo(null);
  }

  private File getExpectedFile(final String expectedFileName) {
    return new File(PathManagerEx.getTestDataPath(), "compiler" + File.separator + "packaging" +
                                                     File.separator + expectedFileName);
  }

  private void doTest(final boolean explodedEnabled, final boolean jarEnabled, final boolean buildExternalDependencies,
                      BuildRecipeInfo info) throws IOException {
    doTest(new MockBuildConfiguration(explodedEnabled, jarEnabled, buildExternalDependencies), info);
  }
  private void doTest(final MockBuildConfiguration mockBuildConfiguration, BuildRecipeInfo info) throws IOException {
    final NewProcessingItem[] items = buildItems(info.myBuildRecipe, mockBuildConfiguration);
    final String s = printItems(items);
    final File file = getExpectedFile(getTestName(true) + ".txt");
    String expected = StringUtil.convertLineSeparators(new String(FileUtil.loadFileText(file)));
    assertEquals(expected, s);
  }


  private NewProcessingItem[] buildItems(final BuildRecipe buildRecipe, final MockBuildConfiguration mockBuildConfiguration) {
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

  private String printItems(NewProcessingItem[] items) {

    List<Pair<JarInfo, String>> jarContent = getJarsContent(items);

    Map<JarInfo, Integer> jar2Num = new HashMap<JarInfo, Integer>();
    for (int i = 0; i < jarContent.size(); i++) {
      Pair<JarInfo, String> pair = jarContent.get(i);
      jar2Num.put(pair.getFirst(), i);
    }

    List<String> output = new ArrayList<String>();
    for (NewProcessingItem item : items) {
      for (DestinationInfo destination : item.getDestinations()) {
        String o = item.getFile().getPath() + " -> " + destination.getOutputPath();
        final String d = printDestination(jar2Num, destination, false);
        output.add(o + (d.length() > 0 ? " (" + d + ")" : ""));
      }
    }
    Collections.sort(output);

    final StringBuilder builder = new StringBuilder();
    for (String s : output) {
      builder.append(s).append("\n");
    }

    for (Pair<JarInfo, String> pair : jarContent) {
      builder.append("\n");
      builder.append("jar#").append(jar2Num.get(pair.getFirst())).append("\n");
      builder.append(pair.getSecond()).append("->").append("\n");

      List<String> to = new ArrayList<String>();
      for (DestinationInfo destinationInfo : pair.getFirst().getJarDestinations()) {
        to.add(printDestination(jar2Num, destinationInfo, true));
      }
      Collections.sort(to);

      for (String s : to) {
        builder.append("  ").append(s).append(";\n");
      }
    }
    return builder.toString();
  }

  private List<Pair<JarInfo, String>> getJarsContent(final NewProcessingItem[] items) {
    Set<JarInfo> jars = new HashSet<JarInfo>();
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

    List<Pair<JarInfo, String>> jarContent = new ArrayList<Pair<JarInfo, String>>();
    for (JarInfo jar : jars) {
      List<String> contentList = new ArrayList<String>();
      for (Pair<String, VirtualFile> pair : jar.getContent()) {
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

  private void addJars(final JarInfo jarInfo, final Set<JarInfo> jars, final Map<JarInfo, Integer> deps) {
    if (!jars.add(jarInfo)) return;
    for (DestinationInfo destination : jarInfo.getJarDestinations()) {
      if (destination instanceof JarDestinationInfo) {
        final JarInfo info = ((JarDestinationInfo)destination).getJarInfo();
        deps.put(info, deps.get(jarInfo) + 1);
        addJars(info, jars, deps);
      }
    }
  }

  private String printDestination(final Map<JarInfo, Integer> jar2Num, final DestinationInfo destination, boolean detailed) {
    String s = "";
    if (destination instanceof JarDestinationInfo) {
      final JarDestinationInfo jarDestination = (JarDestinationInfo)destination;
      s = "jar#" + jar2Num.get(jarDestination.getJarInfo()) + jarDestination.getPathInJar() +
          (detailed ? " in " + jarDestination.getOutputFile().getPath() : "") + "";
    }
    else if (detailed) {
      final VirtualFile outputFile = destination.getOutputFile();
      assertNotNull(outputFile);
      assertEquals(destination.getOutputPath(), outputFile.getPath());
      s = destination.getOutputPath();
    }
    return s;
  }

  private static class MockBuildParticipant extends BuildParticipant {
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

  private static class MockBuildConfiguration extends BuildConfiguration {
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

  private class BuildRecipeInfo {
    private BuildRecipeInfo myParent;
    private BuildRecipe myBuildRecipe = new BuildRecipeImpl();
    private int myCount = 2;
    @NonNls private static final String PROJECT_DIR = "project/";

    public BuildRecipeInfo(final BuildRecipeInfo parent) {
      myParent = parent;
    }

    private BuildRecipeInfo copy(String sourceDir, final String outputRelativePath, String... files) {
      final String path = PROJECT_DIR + sourceDir;
      for (String file : files) {
        LocalFileSystem.getInstance().findFileByPath(path + "/" + file);
      }
      return add(new FileCopyInstructionImpl(new File(path), true, null, outputRelativePath, null));
    }

    private BuildRecipeInfo jar(String sourceDir, final String outputRelativePath, String... files) {
      final String path = PROJECT_DIR + sourceDir;
      for (String file : files) {
        LocalFileSystem.getInstance().findFileByPath(path + "/" + file);
      }
      return add(new JarAndCopyBuildInstructionImpl(null, new File(path), outputRelativePath, null));
    }

    private BuildRecipeInfo add(final BuildInstruction instruction) {
      myBuildRecipe.addInstruction(instruction);
      return this;
    }

    private BuildRecipeInfo copy(String sourceFile, final String outputRelativePath) {
      return add(new FileCopyInstructionImpl(new File(PROJECT_DIR + sourceFile), false, null, outputRelativePath, null));
    }

    private BuildRecipeInfo jar(String sourceFile, final String outputRelativePath) {
      return add(new JarAndCopyBuildInstructionImpl(null, new File(PROJECT_DIR + sourceFile), outputRelativePath, null));
    }

    private BuildRecipeInfo inner(String relativeOutput, boolean explodedEnabled, boolean jarEnabled, boolean buildExternalDependencies) {
      final BuildRecipeInfo inner = new BuildRecipeInfo(this);
      final MockBuildConfiguration configuration = new MockBuildConfiguration(explodedEnabled, jarEnabled, buildExternalDependencies,
                                                                              "/out" + myCount + "/exploded", "/out" + myCount + "/my.jar");
      myCount++;
      final MockBuildParticipant participant = new MockBuildParticipant(configuration, inner.myBuildRecipe);
      add(new JavaeeModuleBuildInstructionImpl(null, participant, relativeOutput));
      return inner;
    }

    private BuildRecipeInfo up() {
      return myParent;
    }
  }
}
