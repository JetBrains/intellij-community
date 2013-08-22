package com.intellij.execution;

import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.MapDataContext;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.TempFiles;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class BaseConfigurationTestCase extends IdeaTestCase {
  protected TempFiles myTempFiles;
  private final List<Module> myModulesToDispose = new ArrayList<Module>();
  protected static final String MOCK_JUNIT = "mock JUnit";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myTempFiles = new TempFiles(myFilesToDelete);
  }

  protected void addModule(String path) throws IOException {
    addModule(path, true);
  }

  protected void addModule(String path, boolean addSource) throws IOException {
    VirtualFile module1Content = findFile(path);
    createModule(module1Content, addSource);
  }

  protected void createModule(VirtualFile module1Content, boolean addSource) throws IOException {
    Module module = createEmptyModule();
    if (addSource) {
      PsiTestUtil.addSourceRoot(module, module1Content, true);
    }
    else {
      PsiTestUtil.addContentRoot(module, module1Content);
    }

    VirtualFile mockJUnit = findFile(MOCK_JUNIT);
    ModuleRootModificationUtil.addModuleLibrary(module, mockJUnit.getUrl());
    ModuleRootModificationUtil.setModuleSdk(module, ModuleRootManager.getInstance(myModule).getSdk());
    GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module);
    VirtualFile testCase = mockJUnit.findChild("junit").findChild("framework").findChild("TestCase.java");
    assertNotNull(testCase);
    assertTrue(scope.contains(testCase));
    Module missingModule = createTempModule();
    addDependency(module, missingModule);
    ModuleManager.getInstance(myProject).disposeModule(missingModule);
  }

  protected Module createEmptyModule() throws IOException {
    Module module = createTempModule();
    myModulesToDispose.add(module);
    return module;
  }

  private Module createTempModule() throws IOException {
    return createTempModule(myTempFiles, myProject);
  }

  public static Module createTempModule(TempFiles tempFiles, final Project project) {
    final String tempPath = tempFiles.createTempPath();
    final Module[] module = new Module[1];
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        module[0] = ModuleManager.getInstance(project).newModule(tempPath, StdModuleTypes.JAVA.getId());
      }
    });
    return module[0];
  }

  protected static VirtualFile findFile(String path) {
    String filePath = PathManagerEx.getTestDataPath() + File.separator + "junit" + File.separator + "configurations" +
                      File.separator + path;
    return LocalFileSystem.getInstance().findFileByPath(filePath.replace(File.separatorChar, '/'));
  }

  @Override
  protected void tearDown() throws Exception {
    myModulesToDispose.clear();
    super.tearDown();
  }

  protected void disposeModule(Module module) {
    assertTrue(myModulesToDispose.remove(module));
    ModuleManager.getInstance(myProject).disposeModule(module);
  }

  protected Module getModule1() {
    return getModule(0);
  }

  protected Module getModule(int index) {
    return myModulesToDispose.get(index);
  }

  protected Module getModule2() {
    return getModule(1);
  }

  protected Module getModule4() {
    return getModule(3);
  }

  protected Module getModule3() {
    return getModule(2);
  }

  protected PsiClass findClass(Module module, String qualifiedName) {
    return findClass(qualifiedName, GlobalSearchScope.moduleScope(module));
  }

  protected PsiClass findClass(String qualifiedName, GlobalSearchScope scope) {
    return JavaPsiFacade.getInstance(myProject).findClass(qualifiedName, scope);
  }

  protected JUnitConfiguration createJUnitConfiguration(final PsiElement psiElement,
                                                        final Class producerClass,
                                                        final MapDataContext dataContext) {
    ConfigurationContext context = createContext(psiElement, dataContext);
    RunConfigurationProducer producer = RunConfigurationProducer.getInstance(producerClass);
    assert producer != null;
    ConfigurationFromContext fromContext = producer.createConfigurationFromContext(context);
    return (JUnitConfiguration)fromContext.getConfiguration();
  }

  protected final <T extends RunConfiguration> T createConfiguration(PsiElement psiElement) {
    return (T)createConfiguration(psiElement, new MapDataContext());
  }

  protected <T extends RunConfiguration> T createConfiguration(PsiElement psiElement, MapDataContext dataContext) {
    ConfigurationContext context = createContext(psiElement, dataContext);
    RunnerAndConfigurationSettings settings = context.getConfiguration();
    return settings == null ? null : (T)settings.getConfiguration();
  }

  public ConfigurationContext createContext(PsiElement psiClass) {
    MapDataContext dataContext = new MapDataContext();
    return createContext(psiClass, dataContext);
  }

  public ConfigurationContext createContext(PsiElement psiClass, MapDataContext dataContext) {
    dataContext.put(DataConstants.PROJECT, myProject);
    if (dataContext.getData(DataConstants.MODULE) == null) {
      dataContext.put(DataConstants.MODULE, ModuleUtilCore.findModuleForPsiElement(psiClass));
    }
    dataContext.put(Location.LOCATION, PsiLocation.fromPsiElement(psiClass));
    return ConfigurationContext.getFromContext(dataContext);
  }

  protected void addDependency(Module module, Module dependency) {
    ModuleRootModificationUtil.addDependency(module, dependency);
  }

  protected void checkPackage(String packageName, JUnitConfiguration configuration) {
    assertEquals(packageName, configuration.getPersistentData().getPackageName());
  }

  protected void checkClassName(String className, JUnitConfiguration configuration) {
    assertEquals(className, configuration.getPersistentData().getMainClassName());
  }

  protected void checkMethodName(String methodName, JUnitConfiguration configuration) {
    assertEquals(methodName, configuration.getPersistentData().getMethodName());
  }

  protected void checkTestObject(String testObjectKey, JUnitConfiguration configuration) {
    assertEquals(testObjectKey, configuration.getPersistentData().TEST_OBJECT);
  }
}
