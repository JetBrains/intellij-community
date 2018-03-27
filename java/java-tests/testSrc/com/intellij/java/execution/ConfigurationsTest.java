/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.java.execution;

import com.intellij.application.options.ModuleDescriptionsComboBox;
import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunConfigurationConfigurableAdapter;
import com.intellij.execution.application.ApplicationConfigurable;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.application.ApplicationConfigurationType;
import com.intellij.execution.configurations.*;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.execution.junit.*;
import com.intellij.execution.junit2.configuration.JUnitConfigurable;
import com.intellij.execution.junit2.configuration.JUnitConfigurationModel;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.testframework.SearchForTestsTask;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.execution.ui.CommonJavaParametersPanel;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.compiler.CompilerMessage;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.rt.ant.execution.SegmentedOutputStream;
import com.intellij.rt.execution.junit.JUnitStarter;
import com.intellij.testFramework.CompilerTester;
import com.intellij.testFramework.MapDataContext;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.ui.EditorTextFieldWithBrowseButton;
import com.intellij.util.Assertion;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import junit.framework.TestCase;
import org.jdom.Element;

import java.io.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

public class ConfigurationsTest extends BaseConfigurationTestCase {
  private final Assertion CHECK = new Assertion();

  private Sdk myJdk;
  private static final String INNER_TEST_NAME = "test1.InnerTest.Inner";
  private static final String RT_INNER_TEST_NAME = "test1.InnerTest$Inner";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    addModule("module1");
    addModule("module2");
    addModule("module3");
    assignJdk(getModule1());
  }

  public void testCreateConfiguration() throws ExecutionException {
    Module module1 = getModule1();
    PsiClass psiClass = findTestA(module1);
    JUnitConfiguration configuration = createConfiguration(psiClass);
    assertEquals(Collections.singleton(module1), ContainerUtilRt.newHashSet(configuration.getModules()));
    checkClassName(psiClass.getQualifiedName(), configuration);
    assertEquals(psiClass.getName(), configuration.getName());
    checkTestObject(JUnitConfiguration.TEST_CLASS, configuration);
    Module module2 = getModule2();
    Assertion.compareUnordered(new Module[]{module1, module2}, configuration.getValidModules());

    PsiClass innerTest = findClass(module1, INNER_TEST_NAME);
    configuration = createJUnitConfiguration(innerTest, TestInClassConfigurationProducer.class, new MapDataContext());
    checkClassName(RT_INNER_TEST_NAME, configuration);
    checkCanRun(configuration);

    PsiMethod[] testMethod = innerTest.findMethodsByName("test", false);
    assertEquals(1, testMethod.length);
    configuration = createConfiguration(testMethod[0]);
    checkClassName(RT_INNER_TEST_NAME, configuration);
    checkMethodName("test", configuration);
    checkTestObject(JUnitConfiguration.TEST_METHOD, configuration);
    checkCanRun(configuration);

    PsiMethod mainMethod = innerTest.findMethodsByName("main", false)[0];
    ApplicationConfiguration appConfiguration = createConfiguration(mainMethod);
    assertEquals(RT_INNER_TEST_NAME, appConfiguration.getMainClassName());
    checkCanRun(configuration);
  }

  public void testModulesSelector() throws ConfigurationException {
    if (PlatformTestUtil.COVERAGE_ENABLED_BUILD) return;

    Module module1 = getModule1();
    Module module2 = getModule2();
    JUnitConfigurable editor = new JUnitConfigurable(myProject);
    try {
      JUnitConfiguration configuration = createConfiguration(findTestA(module2));
      editor.getComponent(); // To get all the watchers installed.
      Configurable configurable = new RunConfigurationConfigurableAdapter(editor, configuration);
      ModuleDescriptionsComboBox comboBox = editor.getModulesComponent();
      configurable.reset();
      assertFalse(configurable.isModified());
      assertEquals(module2.getName(), comboBox.getSelectedModuleName());
      assertEquals(ModuleManager.getInstance(myProject).getModules().length + 1, comboBox.getModel().getSize()); //no module
      comboBox.setSelectedModule(module1);
      assertTrue(configurable.isModified());
      configurable.apply();
      assertFalse(configurable.isModified());
      assertEquals(Collections.singleton(module1), ContainerUtilRt.newHashSet(configuration.getModules()));
    }
    finally {
      Disposer.dispose(editor);
    }
  }

  public void testCantCreateConfiguration() {
    PsiClass objectClass =
      JavaPsiFacade.getInstance(myProject).findClass(CommonClassNames.JAVA_LANG_OBJECT, GlobalSearchScope.allScope(myProject));
    assertNull(createConfiguration(objectClass));
    assertNull(createConfiguration(JUnitUtil.getContainingPackage(objectClass)));
  }

  public void testRunningJUnit() throws ExecutionException {
    PsiClass testA = findTestA(getModule1());
    JUnitConfiguration configuration = createConfiguration(testA);
    JavaParameters parameters = checkCanRun(configuration);
    assertEquals("[-Didea.test.cyclic.buffer.size=1048576]", parameters.getVMParametersList().toString());
    final SegmentedOutputStream notifications = new SegmentedOutputStream(System.out);
    assertTrue(JUnitStarter.checkVersion(parameters.getProgramParametersList().getArray(),
                                         new PrintStream(notifications)));
    assertTrue(parameters.getProgramParametersList().getList().contains(testA.getQualifiedName()));
    assertEquals(JUnitStarter.class.getName(), parameters.getMainClass());
    assertEquals(myJdk.getHomeDirectory().getPresentableUrl(), parameters.getJdkPath());
  }

  public void testRunningAllInPackage() throws IOException, ExecutionException {
    Module module1 = getModule1();
    GlobalSearchScope module1AndLibraries = GlobalSearchScope.moduleWithLibrariesScope(module1);
    PsiClass testCase = findClass(TestCase.class.getName(), module1AndLibraries);
    PsiClass psiClass = findTestA(module1);
    PsiClass psiClass2 = findTestA(getModule2());
    PsiClass derivedTest = findClass(module1, "test1.DerivedTest");
    PsiClass baseTestCase = findClass("junit.framework.ThirdPartyClass", module1AndLibraries);
    PsiClass testB = findClass(getModule3(), "test1.TestB");
    assertNotNull(testCase);
    assertNotNull(derivedTest);
    assertNotNull(psiClass);
    assertTrue(psiClass.isInheritor(testCase, false));
    assertEquals(baseTestCase, derivedTest.getSuperClass());
    assertTrue(baseTestCase.isInheritor(testCase, true));
    assertTrue(derivedTest.isInheritor(testCase, true));
    PsiPackage psiPackage = JUnitUtil.getContainingPackage(psiClass);
    JUnitConfiguration configuration = createConfiguration(psiPackage, module1);
    JavaParameters parameters = checkCanRun(configuration);
    List<String> lines = extractAllInPackageTests(parameters, psiPackage);
    Assertion.compareUnordered(
      //category, filters, classNames...
      new Object[]{"", "", psiClass.getQualifiedName(), derivedTest.getQualifiedName(), RT_INNER_TEST_NAME,
        testB.getQualifiedName()},
      lines);
  }

  public void testRunAllInPackageWhenPackageIsEmptyInModule() throws ExecutionException {
    assignJdk(getModule2());
    JUnitConfiguration configuration =
      new JUnitConfiguration("", myProject, JUnitConfigurationType.getInstance().getConfigurationFactories()[0]);
    configuration.getPersistentData().TEST_OBJECT = JUnitConfiguration.TEST_PACKAGE;
    configuration.getPersistentData().PACKAGE_NAME = "test2";
    configuration.getPersistentData().setScope(TestSearchScope.WHOLE_PROJECT);
    assertEmpty(configuration.getModules());
    checkCanRun(configuration);
    configuration.getPersistentData().PACKAGE_NAME = "noTests";
//    checkCantRun(configuration, "No tests found in the package '");

    configuration.getPersistentData().PACKAGE_NAME = "com.abcent";
    checkCantRun(configuration, "Package 'com.abcent' does not exist");
  }

  public void testAllInPackageForCommonAncestorModule() throws IOException, ExecutionException {
    disposeModule(getModule2());
    addModule("module5", true);
    Module ancestor = getModule1();
    Module child1 = getModule2();
    Module child2 = getModule3();
    addDependency(ancestor, child1);
    addDependency(ancestor, child2);
    PsiPackage psiPackage = JavaPsiFacade.getInstance(myProject).findPackage("test1");
    JUnitConfiguration configuration = createJUnitConfiguration(psiPackage, AllInPackageConfigurationProducer.class, new MapDataContext());
    configuration.getPersistentData().setScope(TestSearchScope.WHOLE_PROJECT);
    assertNotNull(configuration);
    checkPackage(psiPackage.getQualifiedName(), configuration);
    assertEmpty(configuration.getModules());
    JavaParameters parameters = checkCanRun(configuration);
    List<String> tests = extractAllInPackageTests(parameters, psiPackage);
    String childTest1 = findClass(child1, "test1.TestB").getQualifiedName();
    String childTest2 = findClass(child2, "test1.Test5").getQualifiedName();
    String ancestorTest = findClass(ancestor, "test1.TestA").getQualifiedName();
    CHECK.containsAll(tests, new Object[]{ancestorTest, childTest1, childTest2});
  }

  public void testConstructors() throws IOException, ExecutionException {
    addModule("module6", true);
    PsiPackage psiPackage = JavaPsiFacade.getInstance(myProject).findPackage("test1");
    JUnitConfiguration configuration = createJUnitConfiguration(psiPackage, AllInPackageConfigurationProducer.class, new MapDataContext());
    configuration.getPersistentData().setScope(TestSearchScope.SINGLE_MODULE);
    configuration.setModule(getModule(3));
    assertNotNull(configuration);
    checkPackage(psiPackage.getQualifiedName(), configuration);
    JavaParameters parameters = checkCanRun(configuration);
    List<String> tests = extractAllInPackageTests(parameters, psiPackage);
    CHECK.containsAll(tests, new Object[]{"test1.TestCaseInheritor"});
  }

  public void testClasspathConfiguration() throws CantRunException {
    JavaParameters parameters = new JavaParameters();
    RunConfigurationModule module = new JavaRunConfigurationModule(myProject, false);
    Module module1 = getModule1();
    Module module2 = getModule2();
    addDependency(module1, module2);
    Module module3 = getModule3();
    addDependency(module2, module3);
    addDependency(module1, module3);
    addOutputs(module1, 1);
    addOutputs(module2, 2);
    addOutputs(module3, 3);
    module.setModule(module1);
    parameters.configureByModule(module.getModule(), JavaParameters.JDK_AND_CLASSES_AND_TESTS);
    ArrayList<String> classPath = new ArrayList<>();
    StringTokenizer tokenizer = new StringTokenizer(parameters.getClassPath().getPathsString(), File.pathSeparator);
    while (tokenizer.hasMoreTokens()) {
      String token = tokenizer.nextToken();
      classPath.add(token);
    }
    CHECK.singleOccurence(classPath, getOutput(module1, false));
    CHECK.singleOccurence(classPath, getOutput(module1, false));
    CHECK.singleOccurence(classPath, getOutput(module1, true));
    CHECK.singleOccurence(classPath, getOutput(module2, false));
    CHECK.singleOccurence(classPath, getOutput(module2, true));
    CHECK.singleOccurence(classPath, getOutput(module3, false));
    CHECK.singleOccurence(classPath, getOutput(module3, true));
    CHECK.singleOccurence(classPath, getFSPath(findFile(MOCK_JUNIT)));
  }

  public void testExternalizeJUnitConfiguration() {
    Module module = getModule1();
    JUnitConfiguration oldRc = createConfiguration(findTestA(module));
    oldRc.setWorkingDirectory(module.getModuleFilePath());

    RunManagerImpl runManager = new RunManagerImpl(myProject);
    Element element = new RunnerAndConfigurationSettingsImpl(runManager, oldRc, false).writeScheme();

    RunnerAndConfigurationSettingsImpl settings = new RunnerAndConfigurationSettingsImpl(runManager);
    settings.readExternal(element, false);
    JUnitConfiguration newRc = (JUnitConfiguration)settings.getConfiguration();

    checkTestObject(oldRc.getPersistentData().TEST_OBJECT, newRc);
    assertThat(newRc.getModules()).containsOnly(module);
    checkClassName(oldRc.getPersistentData().getMainClassName(), newRc);
  }

  public void testTestClassPathWhenRunningConfigurations() throws ExecutionException {
    addModule("module4", false);
    Module module4 = getModule4();
    assignJdk(module4);
    addSourcePath(module4, "testSrc", true);
    addSourcePath(module4, "src", false);
    String output = setCompilerOutput(module4, "classes", false);
    String testOuput = setCompilerOutput(module4, "testClasses", true);

    ApplicationConfiguration applicationConfiguration = createConfiguration(findClass(module4, "Application"));
    JavaParameters parameters = checkCanRun(applicationConfiguration);
    String classPath = parameters.getClassPath().getPathsString();
    checkDoesNotContain(classPath, testOuput);
    checkContains(classPath, output);

    JUnitConfiguration junitConfiguration =
      createJUnitConfiguration(findClass(module4, "TestApplication"), TestInClassConfigurationProducer.class, new MapDataContext());
    parameters = checkCanRun(junitConfiguration);
    classPath = parameters.getClassPath().getPathsString();
    checkContains(classPath, testOuput);
    checkContains(classPath, output);

    applicationConfiguration.setMainClassName(junitConfiguration.getPersistentData().getMainClassName());
    classPath = checkCanRun(applicationConfiguration).getClassPath().getPathsString();
    checkContains(classPath, testOuput);
    checkContains(classPath, output);
  }

  public void testSameTestAndCommonOutput() throws ExecutionException {
    addModule("module4", false);
    Module module = getModule4();
    assignJdk(module);
    addSourcePath(module, "src", false);
    addSourcePath(module, "testSrc", false);
    String output = setCompilerOutput(module, "classes", false);
    assertEquals(output, setCompilerOutput(module, "classes", true));

    RunConfiguration configuration = createConfiguration(findClass(module, "Application"));
    JavaParameters javaParameters = checkCanRun(configuration);
    checkContains(javaParameters.getClassPath().getPathsString(), output);

    configuration = createConfiguration(findClass(module, "TestApplication"));
    javaParameters = checkCanRun(configuration);
    checkContains(javaParameters.getClassPath().getPathsString(), output);
  }

  public void testCreatingApplicationConfiguration() throws ConfigurationException {
    if (PlatformTestUtil.COVERAGE_ENABLED_BUILD) return;

    ApplicationConfiguration configuration = new ApplicationConfiguration(null, myProject, ApplicationConfigurationType.getInstance());
    ApplicationConfigurable editor = new ApplicationConfigurable(myProject);
    try {
      editor.getComponent(); // To get all the watchers installed.
      Configurable configurable = new RunConfigurationConfigurableAdapter(editor, configuration);
      configurable.reset();
      CommonJavaParametersPanel javaParameters = editor.getCommonProgramParameters();
      javaParameters.setProgramParameters("prg");
      javaParameters.setVMParameters("vm");
      javaParameters.setWorkingDirectory("dir");
      assertTrue(configurable.isModified());
      configurable.apply();
      assertEquals("prg", configuration.getProgramParameters());
      assertEquals("vm", configuration.getVMParameters());
      assertEquals("dir", configuration.getWorkingDirectory());
    }
    finally {
      Disposer.dispose(editor);
    }
  }

  public void testCreateInnerPackageLocalApplication() throws ExecutionException {
    PsiClass psiClass = findClass(getModule1(), "test2.NotATest.InnerApplication");
    assertNotNull(psiClass);
    ApplicationConfiguration configuration = createConfiguration(psiClass);
    assertEquals("test2.NotATest$InnerApplication", configuration.getMainClassName());
    checkCanRun(configuration);
  }

  public void testEditJUnitConfiguration() throws ConfigurationException {
    if (PlatformTestUtil.COVERAGE_ENABLED_BUILD) return;

    PsiClass testA = findTestA(getModule2());
    JUnitConfiguration configuration = createConfiguration(testA);
    JUnitConfigurable editor = new JUnitConfigurable(myProject);
    try {
      Configurable configurable = new RunConfigurationConfigurableAdapter(editor, configuration);
      configurable.reset();
      final EditorTextFieldWithBrowseButton component =
        ((LabeledComponent<EditorTextFieldWithBrowseButton>)editor.getTestLocation(JUnitConfigurationModel.CLASS)).getComponent();
      assertEquals(testA.getQualifiedName(), component.getText());
      PsiClass otherTest = findClass(getModule2(), "test2.Test2");
      component.setText(otherTest.getQualifiedName());
      configurable.apply();
      assertEquals(otherTest.getName(), configuration.getName());
      String specialName = "My name";
      configuration.setName(specialName);
      configuration.setNameChangedByUser(true);
      configurable.reset();
      component.setText(testA.getQualifiedName());
      configurable.apply();
      assertEquals(specialName, configuration.getName());
    }
    finally {
      Disposer.dispose(editor);
    }
  }

  public void testRunThirdPartyApplication() throws ExecutionException {
    ApplicationConfiguration configuration =
      new ApplicationConfiguration("Third party", myProject, ApplicationConfigurationType.getInstance());
    configuration.setModule(getModule1());
    configuration.setMainClassName("third.party.Main");
    checkCanRun(configuration);
  }

  public void testAllInPackageForProject() throws ExecutionException {
    // module1 -> module2 -> module3
    // module5
    addModule("module5");
    addDependency(getModule1(), getModule2());
    addDependency(getModule2(), getModule3());
    String[][] outputs = new String[4][];
    for (int i = 0; i < 4; i++) {
      outputs[i] = addOutputs(getModule(i), i + 1);
    }


    PsiPackage defaultPackage = JavaPsiFacade.getInstance(myProject).findPackage("");
    JUnitConfiguration configuration =
      createJUnitConfiguration(defaultPackage, AllInPackageConfigurationProducer.class, new MapDataContext());
    configuration.getPersistentData().setScope(TestSearchScope.WHOLE_PROJECT);
    JavaParameters javaParameters = checkCanRun(configuration);
    String classPath = javaParameters.getClassPath().getPathsString();
    assertEquals(-1, classPath.indexOf(JarFileSystem.PROTOCOL_PREFIX));
    assertEquals(-1, classPath.indexOf(LocalFileSystem.PROTOCOL_PREFIX));
    for (int i = 0; i < 4; i++) {
      checkContains(classPath, outputs[i][0]);
      checkContains(classPath, outputs[i][1]);
    }
  }

  public void testOriginalModule() {
    ModuleRootModificationUtil.addDependency(getModule1(), getModule2(), DependencyScope.TEST, true);
    ModuleRootModificationUtil.addDependency(getModule2(), getModule3(), DependencyScope.TEST, false);
    assertTrue(ModuleBasedConfiguration.canRestoreOriginalModule(getModule1(), new Module[] {getModule2()}));
    assertTrue(ModuleBasedConfiguration.canRestoreOriginalModule(getModule1(), new Module[] {getModule3()}));

    //not exported but on the classpath
    addModule("module4");
    ModuleRootModificationUtil.addDependency(getModule3(), getModule4(), DependencyScope.TEST, false);
    assertTrue(ModuleBasedConfiguration.canRestoreOriginalModule(getModule1(), new Module[] {getModule4()}));

    addModule("module5");
    assertFalse(ModuleBasedConfiguration.canRestoreOriginalModule(getModule1(), new Module[] {getModule(4)}));

    assertFalse(ModuleBasedConfiguration.canRestoreOriginalModule(getModule2(), new Module[] {getModule1()}));
  }

  private void assignJdk(Module module) {
    myJdk = ModuleRootManager.getInstance(myModule).getSdk();
    ModuleRootModificationUtil.setModuleSdk(module, myJdk);
  }

  private static String getOutput(Module module1, boolean test) {
    CompilerModuleExtension compilerModuleExtension = CompilerModuleExtension.getInstance(module1);
    assertNotNull(compilerModuleExtension);
    VirtualFile output = test ? compilerModuleExtension.getCompilerOutputPathForTests() : compilerModuleExtension.getCompilerOutputPath();
    return getFSPath(output);
  }

  private static String getFSPath(VirtualFile output) {
    return PathUtil.getLocalPath(output);
  }

  private String[] addOutputs(Module module, int index) {
    String[] outputs = new String[2];
    String prefix = "outputs" + File.separatorChar;
    VirtualFile generalOutput = findFile(prefix + "general " + index);
    VirtualFile testOutput = findFile(prefix + "tests" + index);
    outputs[0] = generalOutput.getPresentableUrl();
    outputs[1] = testOutput.getPresentableUrl();
    PsiTestUtil.setCompilerOutputPath(module, generalOutput.getUrl(), false);
    PsiTestUtil.setCompilerOutputPath(module, testOutput.getUrl(), true);
    Disposer.register(getTestRootDisposable(), new Disposable() {
      @Override
      public void dispose() {
        for (File file : new File(outputs[1]).listFiles()) {
          if (file.getName().equals("keep.dir")) continue;
          FileUtil.delete(file);
        }
      }
    });
    return outputs;
  }

  private static JavaParameters checkCanRun(RunConfiguration configuration) throws ExecutionException {
    final RunProfileState state;
    state = ExecutionEnvironmentBuilder.create(DefaultRunExecutor.getRunExecutorInstance(), configuration).build().getState();
    assertNotNull(state);
    assertTrue(state instanceof JavaCommandLine);
    if (state instanceof TestPackage) {
      @SuppressWarnings("UnusedDeclaration")
      final JavaParameters parameters = ((TestPackage)state).getJavaParameters();
      final SearchForTestsTask task = ((TestPackage)state).createSearchingForTestsTask();
      assertNotNull(task);
      Project project = configuration.getProject();
      try {
        CompilerTester tester = new CompilerTester(project, Arrays.asList(ModuleManager.getInstance(project).getModules()));
        try {
          List<CompilerMessage> messages = tester.make();
          assertFalse(messages.stream().anyMatch(message -> message.getCategory() == CompilerMessageCategory.ERROR));
          task.startSearch();
        }
        finally {
          tester.tearDown();
        }
      }
      catch (Exception e) {
        fail(e.getMessage());
      }
    }
    try {
      configuration.checkConfiguration();
    }
    catch (RuntimeConfigurationError e) {
      fail("cannot run: " + e.getMessage());
    }
    catch (RuntimeConfigurationException e) {
      //ignore
    }
    return ((JavaCommandLine)state).getJavaParameters();
  }

  private void checkCantRun(RunConfiguration configuration, String reasonBeginning) throws ExecutionException {
    //MockRunRequest request = new MockRunRequest(myProject);
    //CantRunException rejectReason;
    //try {
    //  configuration.runRequested(request);
    //  rejectReason = request.myRejectReason;
    //}
    //catch (CantRunException e) {
    //  rejectReason = e;
    //}
    //if (rejectReason == null) fail("Should not run");
    //rejectReason.getMessage().startsWith(reasonBeginning);

    try {
      configuration.checkConfiguration();
    }
    catch (RuntimeConfigurationException e) {
      assertTrue(e.getLocalizedMessage().startsWith(reasonBeginning));
      return;
    }

    RunProfileState state = configuration.getState(DefaultRunExecutor.getRunExecutorInstance(), new ExecutionEnvironmentBuilder(myProject, DefaultRunExecutor.getRunExecutorInstance()).runProfile(configuration).build());
    assertTrue(state instanceof JavaCommandLine);

    try {
      ((JavaCommandLine)state).getJavaParameters();
    }
    catch (Throwable e) {
      assertTrue(e.getLocalizedMessage().startsWith(reasonBeginning));
      return;
    }

    fail("Should not run");
  }

  private static String setCompilerOutput(Module module, String path, boolean testOutput) {
    VirtualFile output = ModuleRootManager.getInstance(module).getContentEntries()[0].getFile().findChild(path);
    assertNotNull(output);
    PsiTestUtil.setCompilerOutputPath(module, output.getUrl(), testOutput);
    return output.getPath().replace('/', File.separatorChar);
  }

  private static void addSourcePath(Module module, String path, boolean testSource) {
    final ContentEntry entry = ModuleRootManager.getInstance(module).getContentEntries()[0];
    VirtualFile child = entry.getFile().findChild(path);
    assertNotNull(child);
    PsiTestUtil.addSourceRoot(module, child, testSource);
  }

  private JUnitConfiguration createConfiguration(PsiPackage psiPackage, Module module) {
    JUnitConfiguration configuration =
      new JUnitConfiguration("", myProject, JUnitConfigurationType.getInstance().getConfigurationFactories()[0]);
    configuration.getPersistentData().TEST_OBJECT = JUnitConfiguration.TEST_PACKAGE;
    configuration.getPersistentData().PACKAGE_NAME = psiPackage.getQualifiedName();
    configuration.getPersistentData().setScope(TestSearchScope.WHOLE_PROJECT);
    configuration.setModule(module);
    return configuration;
  }

  private PsiClass findTestA(Module module) {
    return findClass(module, "test1.TestA");
  }

  private static List<String> readLinesFrom(File file) throws IOException {
    if (!file.exists()) file.createNewFile();
    ArrayList<String> result = new ArrayList<>();
    BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
    try {
      String line;
      while ((line = reader.readLine()) != null) result.add(line);
      return result;
    }
    finally {
      reader.close();
    }
  }

  private static List<String> extractAllInPackageTests(JavaParameters parameters, PsiPackage psiPackage)
    throws IOException {
    String filePath = ContainerUtil.find(parameters.getProgramParametersList().getArray(),
                                         value -> StringUtil.startsWithChar(value, '@') && !StringUtil.startsWith(value, "@w@")).substring(1);
    List<String> lines = readLinesFrom(new File(filePath));
    assertEquals(psiPackage.getQualifiedName(), lines.get(0));
    //lines.remove(0);
    lines.remove(0);
    return lines;
  }

  private static void checkContains(String string, String fragment) {
    assertTrue(fragment + " in " + string, string.contains(fragment));
  }

  private static void checkDoesNotContain(String string, String fragment) {
    assertFalse(fragment + " in " + string, string.contains(fragment));
  }

  @Override
  protected void tearDown() throws Exception {
    myJdk = null;
    super.tearDown();
  }
}
