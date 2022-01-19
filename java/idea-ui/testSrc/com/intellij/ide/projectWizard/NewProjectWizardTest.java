// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard;

import com.intellij.ide.projectWizard.generators.IntelliJJavaNewProjectWizardData;
import com.intellij.ide.wizard.LanguageNewProjectWizardData;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.ui.UIBundle;
import com.intellij.util.SystemProperties;

/**
 * @author Dmitry Avdeev
 */
public class NewProjectWizardTest extends NewProjectWizardTestCase {
  public void testCreateProject() throws Exception {
    Project project = createProject(step -> {
      if (step instanceof ProjectTypeStep) {
        assertTrue(((ProjectTypeStep)step).setSelectedTemplate(UIBundle.message("label.project.wizard.project.generator.name"), null));
      }
    });
    assertNotNull(project);
    Sdk sdk = ProjectRootManager.getInstance(project).getProjectSdk();
    assertNotNull(sdk);
    JavaSdkVersion version = JavaSdk.getInstance().getVersion(sdk);
    assertNotNull(version);
    assertEquals(version.getMaxLanguageLevel(), LanguageLevelProjectExtension.getInstance(project).getLanguageLevel());
  }

  public void testModuleJdk() throws Exception {
    configureJdk();

    Project project = createProject(step -> {});
    final var manager = ModuleManager.getInstance(project);

    // Default module should have null JDK
    assertSize(1, manager.getModules());
    final var module = manager.getModules()[0];
    assertTrue(ModuleRootManager.getInstance(module).isSdkInherited());
  }

  public void testNewModuleJdk() throws Exception {
    final Sdk otherSdk = new SimpleJavaSdkType().createJdk("_other", SystemProperties.getJavaHome());
    final Sdk defaultSdk = ProjectJdkTable.getInstance().findJdk(DEFAULT_SDK);
    final String moduleName = "new_module";

    ApplicationManager.getApplication().runWriteAction(() -> {
      addSdk(defaultSdk);
      addSdk(otherSdk);
    });

    // Project with default JDK
    Project project = createProjectFromTemplate(step -> {
      IntelliJJavaNewProjectWizardData.setSdk(step, defaultSdk);
    });
    assertEquals(ProjectRootManager.getInstance(project).getProjectSdk().getName(), defaultSdk.getName());

    // Module with custom JDK
    createModuleFromTemplate(project, step -> {
      LanguageNewProjectWizardData.setLanguage(step, "Java");
      IntelliJJavaNewProjectWizardData.setSdk(step, otherSdk);
      IntelliJJavaNewProjectWizardData.setModuleName(step, moduleName);
    });

    final var manager = ModuleManager.getInstance(project);
    assertSize(2, manager.getModules());

    // Module JDK should be _other
    final var module = manager.findModuleByName(moduleName);
    assertNotNull(module);
    assertFalse(ModuleRootManager.getInstance(module).isSdkInherited());
    assertEquals(ModuleRootManager.getInstance(module).getSdk().getName(), otherSdk.getName());
  }

  public void testDefaultLanguageLevel13() throws Exception {
    Project project = doLanguageLevelTest(LanguageLevel.JDK_1_3, false);
    assertFalse(LanguageLevelProjectExtension.getInstance(project).getDefault());
  }

  public void testDefaultLanguageLevel19() throws Exception {
    Project project = doLanguageLevelTest(LanguageLevel.JDK_1_9, false);
    assertFalse(LanguageLevelProjectExtension.getInstance(project).getDefault());
  }

  public void testDefaultLanguageLevel() throws Exception {
    final Sdk defaultSdk = ProjectJdkTable.getInstance().findJdk(DEFAULT_SDK);
    setProjectSdk(ProjectManager.getInstance().getDefaultProject(), defaultSdk);

    JavaSdkVersion version = JavaSdk.getInstance().getVersion(defaultSdk);
    assertNotNull(version);

    Project project = doLanguageLevelTest(version.getMaxLanguageLevel(), true);
    Boolean def = LanguageLevelProjectExtension.getInstance(project).getDefault();
    assertNotNull(def);
    assertTrue(def);
  }

  public void testChangeSdk() throws Exception {
    Project project = createProject(step -> {});
    Sdk jdk17 = IdeaTestUtil.getMockJdk17();
    addSdk(jdk17);
    setProjectSdk(project, jdk17);
    LanguageLevelProjectExtension extension = LanguageLevelProjectExtension.getInstance(project);
    assertTrue(extension.isDefault());
    assertNotSame(LanguageLevel.JDK_1_8, extension.getLanguageLevel());
    Sdk jdk18 = IdeaTestUtil.getMockJdk18();
    addSdk(jdk18);
    setProjectSdk(project, jdk18);
    assertEquals(LanguageLevel.JDK_1_8, extension.getLanguageLevel());
  }

  public void testMigrateFromOldDefaults() throws Exception {
    LanguageLevelProjectExtension defaultExt = LanguageLevelProjectExtension.getInstance(ProjectManager.getInstance().getDefaultProject());
    defaultExt.setLanguageLevel(LanguageLevel.JDK_1_4);
    defaultExt.setDefault(null); // emulate migration from previous build

    Project project = createProject(step -> {});
    LanguageLevelProjectExtension extension = LanguageLevelProjectExtension.getInstance(project);
    Sdk sdk = ProjectRootManager.getInstance(project).getProjectSdk();
    JavaSdkVersion version = JavaSdk.getInstance().getVersion(sdk);
    assertEquals(version.getMaxLanguageLevel(), extension.getLanguageLevel());
  }

  public void testSampleCode() throws Exception {
    Project project = createProjectFromTemplate(step -> {
      IntelliJJavaNewProjectWizardData.setAddSampleCode(step, true);
    });
    final var mainSearch = FilenameIndex.getVirtualFilesByName("Main.java", GlobalSearchScope.projectScope(project));
    assertFalse(mainSearch.isEmpty());
  }

  private static void setProjectSdk(final Project project, final Sdk jdk17) {
    ApplicationManager.getApplication().runWriteAction(() -> ProjectRootManager.getInstance(project).setProjectSdk(jdk17));
  }

  private Project doLanguageLevelTest(LanguageLevel languageLevel, boolean detect) throws Exception {
    ProjectManager projectManager = ProjectManager.getInstance();
    Project defaultProject = projectManager.getDefaultProject();

    LanguageLevel old = LanguageLevelProjectExtension.getInstance(defaultProject).getLanguageLevel();
    try {
      LanguageLevelProjectExtension.getInstance(defaultProject).setLanguageLevel(languageLevel);
      LanguageLevelProjectExtension.getInstance(defaultProject).setDefault(detect);
      Project project = createProject(step -> {});
      assertEquals(languageLevel, LanguageLevelProjectExtension.getInstance(project).getLanguageLevel());
      return project;
    }
    finally {
      LanguageLevelProjectExtension.getInstance(defaultProject).setLanguageLevel(old);
      LanguageLevelProjectExtension.getInstance(defaultProject).setDefault(true);
    }
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    configureJdk();
  }
}
