// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.actions;

import com.intellij.codeInsight.actions.ReformatCodeAction;
import com.intellij.codeInsight.actions.ReformatFilesOptions;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.intellij.testFramework.JavaPsiTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.VfsTestUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Lepenkin Y.A.
 */
public class ReformatCodeActionTest extends JavaPsiTestCase {
  private static final String[] classNames = {"Vasya", "Main", "Oiie", "Ololo"};
  private static final String[] IMPORTS_LIST = new String[]{"import java.util.List;", "import java.util.Set;", "import java.util.Map"};
  private static final String TEST_SOURCE = """
    %spublic class %s {

    public void start(String str) {
    }

    public static void staticComesSecond(String[] args) {
    }

    int firstInt;
    static int notFirstStatic;
    final static String t = "T";

    }
    """;
  private static final String FORMATTED_SOURCE = """
    %spublic class %s {

        public void start(String str) {
        }

        public static void staticComesSecond(String[] args) {
        }

        int firstInt;
        static int notFirstStatic;
        final static String t = "T";

    }
    """;
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
    List<PsiFile> forProcessing = List.of(files.get(0), files.get(1));
    List<PsiFile> noProcessing = List.of(files.get(2), files.get(3));

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

    performReformatActionOnModule(module, List.of(srcDir));

    checkFormationAndImportsOptimizationFor(files);
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myWorkingDirectory = createDirectory(getOrCreateProjectBaseDir(), TEMP_DIR_NAME);
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

  protected void injectMockDialogFlags(@NotNull ReformatFilesOptions options) {
    ReformatCodeAction.setTestOptions(options);
  }

  protected PsiDirectory createDirectory(VirtualFile parent, String name) {
    VirtualFile dir = createChildDirectory(parent, name);
    return PsiDirectoryFactory.getInstance(getProject()).createDirectory(dir);
  }

  @NotNull
  protected List<PsiFile> createTestFiles(@NotNull VirtualFile parentDirectory, String @NotNull [] fileNames) throws IOException {
    String[] fileText = createTestJavaClassesWithAdditionalImports(fileNames);
    List<PsiFile> files = new ArrayList<>();
    for (int i = 0; i < fileNames.length; i++) {
      String fileName = fileNames[i] + ".java";
      files.add(createFile(getModule(), parentDirectory, fileName, fileText[i]));
    }
    return files;
  }

  @NotNull
  protected List<PsiFile> createTestFiles(@NotNull PsiDirectory parentDirectory, String @NotNull [] fileNames) throws IOException {
    return createTestFiles(parentDirectory.getVirtualFile(), fileNames);
  }

  private static void checkReformatActionAvailableAndPerform(@NotNull AnAction action, @NotNull AnActionEvent event) {
    action.update(event);
    assertTrue("Reformat code action is not enabled", event.getPresentation().isEnabled());
    action.actionPerformed(event);
  }

  protected void performReformatActionOnSelectedFiles(List<PsiFile> files) {
    final AnAction action = getReformatCodeAction();
    final AnActionEvent event = createEventFor(action, getVirtualFileArrayFrom(files), getProject());
    checkReformatActionAvailableAndPerform(action, event);
  }

  protected void performReformatActionOnModule(Module module, List<VirtualFile> files) {
    final AnAction action = getReformatCodeAction();
    AnActionEvent event = createEventFor(action, files, getProject(), new AdditionalEventInfo().setModule(module));
    checkReformatActionAvailableAndPerform(action, event);
  }

  protected void checkFormationAndImportsOptimizationFor(List<PsiFile> @NotNull ... fileCollection) {
    for (List<PsiFile> files : fileCollection) {
      for (PsiFile file : files) {
        String className = getClassNameFromJavaFile(file);
        assertEquals(getFormattedAndImportOptimizedJavaSourceFor(className), file.getText());
      }
    }
  }

  protected void checkNoProcessingWasPerformedOn(List<PsiFile> @NotNull ... fileCollections) {
    for (List<PsiFile> files : fileCollections) {
      for (PsiFile file : files) {
        String className = getClassNameFromJavaFile(file);
        assertEquals(getUntouchedJavaSourceForTotalProcessing(className), file.getText());
      }
    }
  }

  private static @NotNull AnActionEvent createEventFor(@NotNull AnAction action,
                                                       VirtualFile @NotNull [] files,
                                                       @NotNull Project project) {
    return AnActionEvent.createFromAnAction(action, null, "", SimpleDataContext.builder()
      .add(CommonDataKeys.VIRTUAL_FILE_ARRAY, files)
      .add(CommonDataKeys.PROJECT, project)
      .build());
  }

  private static @NotNull AnActionEvent createEventFor(@NotNull AnAction action,
                                                       @NotNull List<VirtualFile> files,
                                                       @NotNull Project project,
                                                       @NotNull AdditionalEventInfo eventInfo) {
    return AnActionEvent.createFromAnAction(action, null, "", SimpleDataContext.builder()
      .add(CommonDataKeys.VIRTUAL_FILE_ARRAY, files.toArray(VirtualFile.EMPTY_ARRAY))
      .add(CommonDataKeys.PROJECT, project)
      .add(CommonDataKeys.EDITOR, eventInfo.getEditor())
      .add(LangDataKeys.MODULE_CONTEXT, eventInfo.getModule())
      .add(CommonDataKeys.PSI_ELEMENT, eventInfo.getElement())
      .build());
  }

  @NotNull
  protected String getFormattedAndImportOptimizedJavaSourceFor(String className) {
    return String.format(FORMATTED_SOURCE, "", className);
  }

  @NotNull
  protected String getClassNameFromJavaFile(@NotNull PsiFile file) {
    return file.getName().split("\\.")[0];
  }

  protected String @NotNull [] createTestJavaClassesWithAdditionalImports(String[] classNames) {
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
      .compute(() -> ModuleManager.getInstance(getProject()).newModule(path, JavaModuleType.getModuleType().getId()));
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