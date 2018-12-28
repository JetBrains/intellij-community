/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.codeInsight.actions;

import com.intellij.codeInsight.actions.ReformatCodeAction;
import com.intellij.codeInsight.actions.ReformatFilesOptions;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Lepenkin Y.A.
 */
public class ReformatCodeActionTest extends PsiTestCase {
  private static final String[] classNames = {"Vasya", "Main", "Oiie", "Ololo"};
  private static final String[] IMPORTS_LIST = new String[]{"import java.util.List;", "import java.util.Set;", "import java.util.Map"};
  private static final String TEST_SOURCE = "%s" +
                                            "public class %s {\n" +
                                            "\n" +
                                            "public void start(String str) {\n" +
                                            "}\n" +
                                            "\n" +
                                            "public static void staticComesSecond(String[] args) {\n" +
                                            "}\n" +
                                            "\n" +
                                            "int firstInt;\n" +
                                            "static int notFirstStatic;\n" +
                                            "final static String t = \"T\";\n" +
                                            "\n" +
                                            "}\n";
  private static final String FORMATTED_SOURCE = "%s" +
                                                 "public class %s {\n" +
                                                 "\n" +
                                                 "    public void start(String str) {\n" +
                                                 "    }\n" +
                                                 "\n" +
                                                 "    public static void staticComesSecond(String[] args) {\n" +
                                                 "    }\n" +
                                                 "\n" +
                                                 "    int firstInt;\n" +
                                                 "    static int notFirstStatic;\n" +
                                                 "    final static String t = \"T\";\n" +
                                                 "\n" +
                                                 "}\n";
  private static final String TEMP_DIR_NAME = "dir";
  private PsiDirectory myWorkingDirectory;

  public void testReformatAndOptimizeMultipleFiles() throws IOException {
    List<PsiFile> files = createTestFiles(getTempRootDirectory(), classNames);
    injectMockDialogFlags(new MockReformatFileSettings().setOptimizeImports(true));
    performReformatActionOnSelectedFiles(files);
    checkFormationAndImportsOptimizationFor(files);
  }

  public void testOptimizeAndReformatOnlySelectedFiles() throws IOException {
    List<PsiFile> files = createTestFiles(getTempRootDirectory(), classNames);
    List<PsiFile> forProcessing = ContainerUtil.newArrayList(files.get(0), files.get(1));
    List<PsiFile> noProcessing = ContainerUtil.newArrayList(files.get(2), files.get(3));

    injectMockDialogFlags(new MockReformatFileSettings().setOptimizeImports(true));

    performReformatActionOnSelectedFiles(forProcessing);

    checkFormationAndImportsOptimizationFor(forProcessing);
    checkNoProcessingWasPerformedOn(noProcessing);
  }

  public void testOptimizeAndReformatInModule() throws IOException {
    Module module = createModuleWithSourceRoot("newModule");
    VirtualFile srcDir = ModuleRootManager.getInstance(module).getSourceRoots()[0];
    List<PsiFile> files = createTestFiles(srcDir, classNames);
    injectMockDialogFlags(new MockReformatFileSettings().setOptimizeImports(true));

    performReformatActionOnModule(module, ContainerUtil.newArrayList(srcDir));

    checkFormationAndImportsOptimizationFor(files);
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myWorkingDirectory = createDirectory(getProject().getBaseDir(), TEMP_DIR_NAME);
  }

  @Override
  public void tearDown() throws Exception {
    try {
      VfsTestUtil.deleteFile(myWorkingDirectory.getVirtualFile());
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      myWorkingDirectory = null;
      super.tearDown();
    }
  }

  @NotNull
  protected PsiDirectory getTempRootDirectory() {
    return myWorkingDirectory;
  }

  protected void injectMockDialogFlags(ReformatFilesOptions options) {
    ReformatCodeAction.setTestOptions(options);
  }

  protected PsiDirectory createDirectory(VirtualFile parent, String name) {
    VirtualFile dir = createChildDirectory(parent, name);
    return PsiDirectoryFactory.getInstance(getProject()).createDirectory(dir);
  }

  @NotNull
  protected List<PsiFile> createTestFiles(@NotNull VirtualFile parentDirectory, @NotNull String[] fileNames) throws IOException {
    String[] fileText = createTestJavaClassesWithAdditionalImports(fileNames);
    List<PsiFile> files = new ArrayList<>();
    for (int i = 0; i < fileNames.length; i++) {
      String fileName = fileNames[i] + ".java";
      files.add(createFile(getModule(), parentDirectory, fileName, fileText[i]));
    }
    return files;
  }

  @NotNull
  protected List<PsiFile> createTestFiles(@NotNull PsiDirectory parentDirectory, @NotNull String[] fileNames) throws IOException {
    return createTestFiles(parentDirectory.getVirtualFile(), fileNames);
  }

  protected void performReformatActionOnSelectedFiles(List<PsiFile> files) {
    final AnAction action = getReformatCodeAction();
    action.actionPerformed(createEventFor(action, getVirtualFileArrayFrom(files), getProject()));
  }

  protected void performReformatActionOnModule(Module module, List<VirtualFile> files) {
    final AnAction action = getReformatCodeAction();
    action.actionPerformed(createEventFor(action, files, getProject(), new AdditionalEventInfo().setModule(module)));
  }

  protected void checkFormationAndImportsOptimizationFor(@NotNull List<PsiFile>... fileCollection) {
    for (List<PsiFile> files : fileCollection) {
      for (PsiFile file : files) {
        String className = getClassNameFromJavaFile(file);
        assertEquals(getFormattedAndImportOptimizedJavaSourceFor(className), file.getText());
      }
    }
  }

  protected void checkNoProcessingWasPerformedOn(@NotNull List<PsiFile>... fileCollections) {
    for (List<PsiFile> files : fileCollections) {
      for (PsiFile file : files) {
        String className = getClassNameFromJavaFile(file);
        assertEquals(getUntouchedJavaSourceForTotalProcessing(className), file.getText());
      }
    }
  }

  protected AnActionEvent createEventFor(AnAction action, final VirtualFile[] files, final Project project) {
    return AnActionEvent.createFromAnAction(action, null, "", dataId -> {
      if (CommonDataKeys.VIRTUAL_FILE_ARRAY.is(dataId)) return files;
      if (CommonDataKeys.PROJECT.is(dataId)) return project;
      return null;
    });
  }

  protected AnActionEvent createEventFor(AnAction action, final List<VirtualFile> files, final Project project, @NotNull final AdditionalEventInfo eventInfo) {
    return AnActionEvent.createFromAnAction(action, null, "", dataId -> {
      if (CommonDataKeys.VIRTUAL_FILE_ARRAY.is(dataId)) return files.toArray(VirtualFile.EMPTY_ARRAY);
      if (CommonDataKeys.PROJECT.is(dataId)) return project;
      if (CommonDataKeys.EDITOR.is(dataId)) return eventInfo.getEditor();
      if (LangDataKeys.MODULE_CONTEXT.is(dataId)) return eventInfo.getModule();
      if (CommonDataKeys.PSI_ELEMENT.is(dataId)) return eventInfo.getElement();
      return null;
    });
  }

  @NotNull
  protected String getFormattedAndImportOptimizedJavaSourceFor(String className) {
    return String.format(FORMATTED_SOURCE, "", className);
  }

  @NotNull
  protected String getClassNameFromJavaFile(@NotNull PsiFile file) {
    return file.getName().split("\\.")[0];
  }

  @NotNull
  protected String[] createTestJavaClassesWithAdditionalImports(String[] classNames) {
    String[] classes = new String[classNames.length];
    for (int i = 0; i < classNames.length; i++) {
      classes[i] = getUntouchedJavaSourceForTotalProcessing(classNames[i]);
    }
    return classes;
  }

  protected Module createModuleWithSourceRoot(String newModuleName) {
    PsiDirectory dir = createDirectory(getTempRootDirectory().getVirtualFile(), newModuleName);
    String path = dir.getVirtualFile().getPath() + "/" + newModuleName + ".iml";

    Module module = WriteAction
      .compute(() -> ModuleManager.getInstance(getProject()).newModule(path, StdModuleTypes.JAVA.getId()));
    PsiDirectory src = createDirectory(dir.getVirtualFile(), "src");

    PsiTestUtil.addSourceRoot(module, src.getVirtualFile());
    return module;
  }

  private static AnAction getReformatCodeAction() {
    final String actionId = IdeActions.ACTION_EDITOR_REFORMAT;
    return ActionManager.getInstance().getAction(actionId);
  }

  @NotNull
  protected static String getUntouchedJavaSourceForTotalProcessing(String className) {
    return createTestJavaClassTextFor(getImportListFrom(IMPORTS_LIST), className);
  }

  @NotNull
  private static String createTestJavaClassTextFor(String imports, String className) {
    return String.format(TEST_SOURCE, imports, className);
  }

  @NotNull
  private static String getImportListFrom(String[] imports) {
    StringBuilder sb = new StringBuilder();
    for (String anImport : imports) {
      sb.append(anImport).append('\n');
    }
    return String.valueOf(sb);
  }

  private static VirtualFile[] getVirtualFileArrayFrom(List<PsiFile> files) {
    VirtualFile[] array = new VirtualFile[files.size()];
    int i = 0;
    for (PsiFile f : files) {
      array[i++] = f.getVirtualFile();
    }
    return array;
  }
}
