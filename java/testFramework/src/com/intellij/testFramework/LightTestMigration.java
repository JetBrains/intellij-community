// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.JavaTestUtil;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.testFramework.fixtures.*;
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl;
import com.siyeh.ig.LightInspectionTestCase;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.junit.ComparisonFailure;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.time.Year;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A helper class to aid migration from {@link InspectionTestCase} to {@link LightCodeInsightFixtureTestCase}.
 * This class has a global state which is not thread-safe and intended to be used from single thread only.
 */
@SuppressWarnings("UseOfSystemOutOrSystemErr")
class LightTestMigration {
  private static final boolean TREAT_MULTI_FILES_AS_MULTIPLE_TESTS = false;
  private final String myName;
  private final Class<? extends InspectionTestCase> myTestClass;
  private final Path myDir;
  private final List<InspectionToolWrapper<?, ?>> myTools;
  private final List<String> myTestNames = new ArrayList<>();

  private static LightTestMigration ourPrevious;

  static {
    Runtime.getRuntime().addShutdownHook(new Thread(LightTestMigration::flush));
  }

  private Path myBaseDir;
  private Path myBasePath;

  LightTestMigration(String name,
                     Class<? extends InspectionTestCase> testClass,
                     String dir,
                     List<InspectionToolWrapper<?, ?>> tools) {
    myName = name;
    myTestClass = testClass;
    myDir = Paths.get(dir);
    myTools = tools;
  }

  void tryMigrate() throws Exception {
    myBasePath = Paths.get(JavaTestUtil.getJavaTestDataPath());
    List<Path> files = TREAT_MULTI_FILES_AS_MULTIPLE_TESTS ? getJavaFiles(myDir) : Collections.singletonList(getSoleJavaFile(myDir));
    List<Pair<Path, String>> resultFiles = new ArrayList<>();
    for (Path file : files) {
      resultFiles.add(processSingleFileTest(file));
    }
    FileUtil.delete(myDir.toFile());
    Files.createDirectories(myDir);
    for (Pair<Path, String> file : resultFiles) {
      Files.write(file.getFirst(), file.getSecond().getBytes(StandardCharsets.UTF_8));
      System.out.println("Written: " + file.getFirst());
    }
  }

  private Pair<Path, String> processSingleFileTest(Path javaFile) throws Exception {
    String fileText = new String(Files.readAllBytes(javaFile), StandardCharsets.UTF_8);
    String testName = myName.isEmpty() ? javaFile.getFileName().toString().replaceFirst(".java$", "") : myName;
    myBaseDir = myName.isEmpty() ? myDir : myDir.getParent();
    Path targetFile = myBaseDir.resolve(testName + ".java");
    IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
    LightProjectDescriptor descriptor = new LightProjectDescriptor();
    TestFixtureBuilder<IdeaProjectTestFixture> fixtureBuilder = factory.createLightFixtureBuilder(descriptor);
    IdeaProjectTestFixture fixture = fixtureBuilder.getFixture();
    JavaCodeInsightTestFixture javaFixture =
      JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(fixture, new LightTempDirTestFixtureImpl(true));
    javaFixture.setUp();
    for (InspectionToolWrapper<?, ?> tool : myTools) {
      javaFixture.enableInspections(tool.getTool());
      final Project project = javaFixture.getProject();
      final HighlightDisplayKey displayKey = HighlightDisplayKey.find(tool.getShortName());
      final InspectionProfileImpl currentProfile = ProjectInspectionProfileManager.getInstance(project).getCurrentProfile();
      final HighlightDisplayLevel errorLevel = currentProfile.getErrorLevel(displayKey, null);
      if (errorLevel == HighlightDisplayLevel.DO_NOT_SHOW) {
        currentProfile.setErrorLevel(displayKey, HighlightDisplayLevel.WARNING, project);
      }
    }
    String expectedText = getExpectedText(javaFile, fileText, javaFixture);
    enqueue(this, testName);
    javaFixture.tearDown();
    return Pair.create(targetFile, expectedText);
  }

  private void generateClassTemplate() {
    final String pathSpec;
    Set<Class<?>> importedClasses =
      StreamEx.of(myTools.stream().<Class<?>>map(wrapper -> wrapper.getTool().getClass()))
        .append(LightCodeInsightFixtureTestCase.class, LightProjectDescriptor.class, NotNull.class)
        .toSet();
    if (myBaseDir.startsWith(myBasePath)) {
      Path relativePath = myBasePath.relativize(myBaseDir);
      importedClasses.add(JavaTestUtil.class);
      pathSpec = "JavaTestUtil.getRelativeJavaTestDataPath() + \"/" +
                 StringUtil.escapeStringCharacters(relativePath.toString().replace('\\', '/')) +
                 '"';
    }
    else {
      Path basePath = Paths.get(PathManagerEx.getCommunityHomePath());
      if (myBaseDir.startsWith(basePath)) {
        Path relativePath = basePath.relativize(myBaseDir);
        String pathText = '/' + relativePath.toString().replace('\\', '/');
        if (pathText.startsWith(LightInspectionTestCase.INSPECTION_GADGETS_TEST_DATA_PATH)) {
          importedClasses.add(LightInspectionTestCase.class);
          pathSpec = "LightInspectionTestCase.INSPECTION_GADGETS_TEST_DATA_PATH + \"" +
                     StringUtil.escapeStringCharacters(
                       pathText.substring(LightInspectionTestCase.INSPECTION_GADGETS_TEST_DATA_PATH.length())) + '"';
        }
        else {
          pathSpec = '"' + StringUtil.escapeStringCharacters(pathText) + '"';
        }
      }
      else {
        pathSpec = "\"!!! Unable to convert path!!! " + StringUtil.escapeStringCharacters(myBaseDir.toString()) + '"';
      }
    }
    String imports = generateImports(importedClasses);
    String testMethods = myTestNames.stream().map(name -> "  public void test"+name+"() {\n    doTest();\n  }\n\n").collect(Collectors.joining());
    String inspections =
      myTools.stream().map(InspectionToolWrapper::getTool).map(InspectionProfileEntry::getClass).map(Class::getSimpleName)
        .map(name -> "new " + name + "()").collect(Collectors.joining(", "));
    String year = Year.now().toString();
    String classTemplate = MessageFormat.format(
      "// Copyright 2000-{6} JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.\n" +
      "package {0};\n\n" +
      "{1}" +
      "\n" +
      "public class {2} extends LightCodeInsightFixtureTestCase '{'\n" +
      "  @Override\n" +
      "  protected String getBasePath() '{'\n" +
      "    return {3};\n" +
      "  '}'\n" +
      "\n" +
      "  private void doTest() '{'\n" +
      "    myFixture.enableInspections({4});\n" +
      "    myFixture.testHighlighting(getTestName(false) + \".java\");\n" +
      "  '}'\n" +
      "\n" +
      "{5}" +
      "'}'\n",
      myTestClass.getPackage().getName(), imports, myTestClass.getSimpleName(), pathSpec, inspections, testMethods, year);
    System.out.println("Class template: (" + myTestClass.getSimpleName() + ".java)");
    System.out.println("==============================");
    System.out.println(classTemplate);
    System.out.println("==============================");
  }

  private String generateImports(Set<Class<?>> importedClasses) {
    return importedClasses.stream()
      .filter(cls -> !cls.getPackage().equals(myTestClass.getPackage()))
      .map(Class::getName)
      .sorted()
      .map(name -> "import " + name + ";\n").collect(Collectors.joining());
  }


  private static String getExpectedText(Path javaFile, String fileText, JavaCodeInsightTestFixture javaFixture) {
    javaFixture.configureByText(javaFile.getFileName().toString(), fileText);
    try {
      javaFixture.testHighlighting(true, false, false);
    }
    catch (ComparisonFailure e) {
      // Seems that expected and actual are switched
      return e.getActual();
    }
    return fileText;
  }

  private static Path getSoleJavaFile(Path dir) throws IOException {
    List<Path> javaFiles = getJavaFiles(dir);
    if (javaFiles.size() > 1) {
      throw new RuntimeException("Unable to migrate: more than one Java file found in " + dir);
    }
    return javaFiles.get(0);
  }

  @NotNull
  private static List<Path> getJavaFiles(Path dir) throws IOException {
    List<Path> javaFiles = Files.walk(dir).filter(p -> p.toString().endsWith(".java")).filter(p -> Files.isRegularFile(p))
      .collect(Collectors.toList());
    if (javaFiles.isEmpty()) {
      throw new RuntimeException("Unable to migrate: no Java files found in " + dir);
    }
    return javaFiles;
  }

  private static void enqueue(LightTestMigration migration, String testName) {
    if (ourPrevious == null || !ourPrevious.myTestClass.equals(migration.myTestClass)) {
      flush();
      ourPrevious = migration;
    }
    ourPrevious.myTestNames.add(testName);
  }

  private static void flush() {
    if (ourPrevious != null) {
      ourPrevious.generateClassTemplate();
      ourPrevious = null;
    }
  }
}
