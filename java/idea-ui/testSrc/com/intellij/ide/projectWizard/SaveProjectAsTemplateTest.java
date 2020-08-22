// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard;

import com.intellij.application.UtilKt;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.impl.FileTemplateManagerImpl;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.projectWizard.ProjectTemplateParameterFactory;
import com.intellij.mock.MockProgressIndicator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.module.BasePackageParameterFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.ProjectTemplatesFactory;
import com.intellij.platform.templates.ArchivedTemplatesFactory;
import com.intellij.platform.templates.LocalArchivedTemplate;
import com.intellij.platform.templates.SaveProjectAsTemplateAction;
import com.intellij.project.ProjectKt;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.OpenProjectTaskBuilder;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.io.PathKt;
import com.intellij.util.text.DateFormatUtil;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

public class SaveProjectAsTemplateTest extends NewProjectWizardTestCase {
  private static final String FOO_BAR_JAVA = "foo/Bar.java";

  private static final Date TEST_DATE = new Date(0);

  public void testSaveProject() throws Exception {
    doTest(true, true, "/** No comments */\n" +
                       "\n" +
                       "/**\n" +
                       " * Created by Dmitry.Avdeev on 1/22/13.\n" +
                       " */\n" +
                       "package foo;\n" +
                       "public class Bar {\n" +
                       "}", "/** No comments */\n" +
                            "\n" +
                            "/**\n" +
                            " * Created by Vasya Pupkin on " + DateFormatUtil.formatDate(TEST_DATE) + ".\n" +
                            " */\n" +
                            "\n" +
                            "package foo;\n" +
                            "public class Bar {\n" +
                            "}");
  }

  public void testSaveProjectUnescaped() throws Exception {
    doTest(false, false, "/** No comments */\n" +
                         "\n" +
                         "/**\n" +
                         " * Created by Dmitry.Avdeev on 1/22/13.\n" +
                         " */\n" +
                         "package foo;\n" +
                         "public class Bar {\n" +
                         "}", "/** No comments */\n" +
                              "\n" +
                              "/**\n" +
                              " * Created by Vasya Pupkin on " + DateFormatUtil.formatDate(TEST_DATE) + ".\n" +
                              " */\n" +
                              "\n" +
                              "package foo;\n" +
                              "public class Bar {\n" +
                              "}");
  }

  private void doTest(boolean shouldEscape, boolean replaceParameters, String initialText, String expected) throws IOException {
    assertThat(ProjectKt.getStateStore(getProject()).getStorageScheme()).isEqualTo(StorageScheme.DIRECTORY_BASED);
    VirtualFile root = ProjectRootManager.getInstance(getProject()).getContentRoots()[0];
    Path rootFile = root.toNioPath().resolve(FOO_BAR_JAVA);
    PathKt.createFile(rootFile);
    VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(rootFile);
    assertNotNull(file);
    setFileText(file, initialText);
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    String basePackage = new BasePackageParameterFactory().detectParameterValue(getProject());
    assertEquals("foo", basePackage);

    Path zipFile = ArchivedTemplatesFactory.getTemplateFile("foo");
    UtilKt.runInAllowSaveMode(() -> {
      SaveProjectAsTemplateAction.saveProject(getProject(), zipFile, null, "bar", replaceParameters, new MockProgressIndicator(), shouldEscape);
      return Unit.INSTANCE;
    });
    assertThat(zipFile.getFileName().toString()).isEqualTo("foo.zip");
    assertThat(Files.size(zipFile)).isGreaterThan(0);

    Project fromTemplate = createProjectFromTemplate(ProjectTemplatesFactory.CUSTOM_GROUP, "foo", null);
    VirtualFile descriptionFile = SaveProjectAsTemplateAction.getDescriptionFile(fromTemplate, LocalArchivedTemplate.DESCRIPTION_PATH);
    assertNotNull(descriptionFile);
    assertEquals("bar", VfsUtilCore.loadText(descriptionFile));

    VirtualFile[] roots = ProjectRootManager.getInstance(fromTemplate).getContentRoots();
    VirtualFile child = roots[0].findFileByRelativePath(FOO_BAR_JAVA);
    assertNotNull(Arrays.asList(roots[0].getChildren()).toString(), child);
    assertEquals(expected, StringUtil.convertLineSeparators(VfsUtilCore.loadText(child)));

    assertThat(Paths.get(fromTemplate.getBasePath(), ".idea/workspace.xml")).isRegularFile();
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    SystemProperties.setTestUserName("Vasya Pupkin");
    ((FileTemplateManagerImpl)FileTemplateManager.getDefaultInstance()).setTestDate(TEST_DATE);
    PropertiesComponent.getInstance().unsetValue(ProjectTemplateParameterFactory.IJ_BASE_PACKAGE);
    PlatformTestUtil.setLongMeaninglessFileIncludeTemplateTemporarilyFor(getProject(), getProject());
  }

  @Override
  public void tearDown() throws Exception {
    try {
      SystemProperties.setTestUserName(null);
      ((FileTemplateManagerImpl)FileTemplateManager.getDefaultInstance()).setTestDate(null);
      PropertiesComponent.getInstance().unsetValue(ProjectTemplateParameterFactory.IJ_BASE_PACKAGE);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  @NotNull
  @Override
  protected Project doCreateAndOpenProject() {
    Path projectFile = getProjectDirOrFile(true);
    try {
      Files.createDirectories(projectFile.getParent().resolve(Project.DIRECTORY_STORE_FOLDER));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    return ProjectManagerEx.getInstanceEx().openProject(projectFile.getParent(), new OpenProjectTaskBuilder().build());
  }

  @NotNull
  @Override
  protected Module createMainModule() throws IOException {
    final Module module = super.createMainModule();
    ApplicationManager.getApplication().runWriteAction(() -> {
      ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
      VirtualFile baseDir = PlatformTestUtil.getOrCreateProjectBaseDir(module.getProject());
      ContentEntry entry = model.addContentEntry(baseDir);
      entry.addSourceFolder(baseDir, false);
      model.commit();
    });
    return module;
  }
}
