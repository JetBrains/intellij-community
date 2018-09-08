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
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.AnnotationOrderRootType;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.testFramework.fixtures.*;
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.ComparisonFailure;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A helper class to aid migration from {@link InspectionTestCase} to {@link LightCodeInsightFixtureTestCase}
 */
@SuppressWarnings("UseOfSystemOutOrSystemErr")
class LightTestMigration {
  private final String myName;
  private final Class<? extends InspectionTestCase> myTestClass;
  private final Path myDir;
  private final List<InspectionToolWrapper<?, ?>> myTools;
  private final Sdk mySdk;
  private static final List<String> ourTestNames = new ArrayList<>();

  private static final Map<String, String> JDK_MAP = EntryStream.of(
    "java 1.7", "JAVA_1_7",
    "java 1.8", "JAVA_8",
    "java 9", "JAVA_9"
  ).toMap();
  private Path myBaseDir;
  private Path myBasePath;

  LightTestMigration(String name,
                     Class<? extends InspectionTestCase> testClass,
                     String dir,
                     List<InspectionToolWrapper<?, ?>> tools,
                     Sdk sdk) {
    myName = name;
    myTestClass = testClass;
    myDir = Paths.get(dir);
    myTools = tools;
    mySdk = sdk;
  }

  void tryMigrate() throws Exception {
    myBasePath = Paths.get(JavaTestUtil.getJavaTestDataPath());
    Path javaFile = getSoleJavaFile(myDir);
    String fileText = new String(Files.readAllBytes(javaFile), StandardCharsets.UTF_8);
    String testName = myName.isEmpty() ? javaFile.getFileName().toString().replaceFirst(".java$", "") : myName;
    myBaseDir = myName.isEmpty() ? myDir : myDir.getParent();
    Path targetFile = myBaseDir.resolve(testName + ".java");
    IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
    LightProjectDescriptor descriptor = new LightProjectDescriptor() {
      @Nullable
      @Override
      public Sdk getSdk() {
        return mySdk;
      }
    };
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
    FileUtil.delete(myDir.toFile());
    Files.createDirectories(myDir);
    Files.write(targetFile, expectedText.getBytes(StandardCharsets.UTF_8));
    System.out.println("Written: " + targetFile);
    if (ourTestNames.isEmpty()) {
      Runtime.getRuntime().addShutdownHook(new Thread(this::generateClassTemplate));
    }
    ourTestNames.add(testName);
    javaFixture.tearDown();
  }

  private void generateClassTemplate() {
    String pathSpec;
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
        pathSpec = "\"/" + StringUtil.escapeStringCharacters(relativePath.toString().replace('\\', '/')) + '"';
      }
      else {
        pathSpec = "\"!!! Unable to convert path!!! " + StringUtil.escapeStringCharacters(myBaseDir.toString()) + '"';
      }
    }
    String imports = generateImports(importedClasses);
    String testMethods = ourTestNames.stream().map(name -> "  public void test"+name+"() {\n    doTest();\n  }\n\n").collect(Collectors.joining());
    String inspections =
      myTools.stream().map(InspectionToolWrapper::getTool).map(InspectionProfileEntry::getClass).map(Class::getSimpleName)
        .map(name -> "new " + name + "()").collect(Collectors.joining(", "));
    System.out.println("JDK version: " + mySdk.getVersionString());
    String guessedJdk = guessJdk();
    String year = Year.now().toString();
    String classTemplate = MessageFormat.format(
      "// Copyright 2000-{7} JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.\n" +
      "package {0};\n\n" +
      "{1}" +
      "\n" +
      "public class {2} extends LightCodeInsightFixtureTestCase '{'\n" +
      "  @Override\n" +
      "  protected String getBasePath() '{'\n" +
      "    return {3};\n" +
      "  '}'\n" +
      "\n" +
      "  @NotNull\n" +
      "  @Override\n" +
      "  protected LightProjectDescriptor getProjectDescriptor() '{'\n" +
      "    return {6};\n" +
      "  '}'\n" +
      "\n" +
      "  private void doTest() '{'\n" +
      "    myFixture.enableInspections({4});\n" +
      "    myFixture.testHighlighting(getTestName(false) + \".java\");\n" +
      "  '}'\n" +
      "\n" +
      "{5}" +
      "'}'\n",
      myTestClass.getPackage().getName(), imports, myTestClass.getSimpleName(), pathSpec, inspections, testMethods, guessedJdk, year);
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

  private String guessJdk() {
    String guessedJdk = JDK_MAP.getOrDefault(mySdk.getVersionString(), "JAVA_LATEST");
    if (mySdk.getRootProvider().getFiles(AnnotationOrderRootType.getInstance()).length != 0) {
      guessedJdk += "_ANNOTATED";
    }
    return guessedJdk;
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
    List<Path> javaFiles = Files.walk(dir).filter(p -> p.toString().endsWith(".java")).filter(p -> Files.isRegularFile(p))
      .collect(Collectors.toList());
    if (javaFiles.isEmpty()) {
      throw new RuntimeException("Unable to migrate: no Java files found in " + dir);
    }
    if (javaFiles.size() > 1) {
      throw new RuntimeException("Unable to migrate: more than one Java file found in " + dir);
    }
    return javaFiles.get(0);
  }
}
