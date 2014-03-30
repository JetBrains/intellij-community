/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractLayoutCodeProcessorTest extends PsiTestCase {

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

  private static final String FORMATED_SOURCE = "%s" +
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

  private static final String ARRANGED_AND_FORMATTED = "public class %s {\n" +
                                                       "\n" +
                                                       "    final static String t = \"T\";\n" +
                                                       "    static int notFirstStatic;\n" +
                                                       "    int firstInt;\n" +
                                                       "\n" +
                                                       "    public static void staticComesSecond(String[] args) {\n" +
                                                       "    }\n" +
                                                       "\n" +
                                                       "    public void start(String str) {\n" +
                                                       "    }\n" +
                                                       "}";
  private static final String TEMP_DIR_NAME = "dir";
  private PsiDirectory myWorkingDirectory;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myWorkingDirectory = createDirectory(getProject().getBaseDir(), TEMP_DIR_NAME);
  }

  @Override
  public void tearDown() throws Exception {
    super.tearDown();
    delete(myWorkingDirectory.getVirtualFile());
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
    List<PsiFile> files = new ArrayList<PsiFile>();
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

  protected void performReformatActionOnSelectedFile(PsiFile file) {
    final AnAction action = getReformatCodeAction();
    action.actionPerformed(createEventFor(action, ContainerUtil.newArrayList(file), getProject(), new AdditionalEventInfo().setPsiElement(file)));
  }

  protected void performReformatActionOnModule(Module module, List<PsiFile> files) {
    final AnAction action = getReformatCodeAction();
    action.actionPerformed(createEventFor(action, files, getProject(), new AdditionalEventInfo().setModule(module)));
  }

  private static AnAction getReformatCodeAction() {
    final String actionId = IdeActions.ACTION_EDITOR_REFORMAT;
    return ActionManager.getInstance().getAction(actionId);
  }

  protected void performReformatActionOnFileInEditor(PsiFile file) {
    final AnAction action = getReformatCodeAction();
    Document document = PsiDocumentManager.getInstance(getProject()).getDocument(file);
    Editor editor = EditorFactory.getInstance().createEditor(document);
    action.actionPerformed(createEventFor(action, ContainerUtil.newArrayList(file), getProject(), new AdditionalEventInfo().setEditor(editor)));
    EditorFactory.getInstance().releaseEditor(editor);
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

  protected void checkRearrangeReformatAndOptimizeImportsHappend(@NotNull List<PsiFile>... fileCollections) {
    for (List<PsiFile> files : fileCollections) {
      for (PsiFile file : files) {
        String className = getClassNameFromJavaFile(file);
        assertEquals(getFormattedAndImportOptimizedAndRearrangedJavaSourceFor(className), file.getText());
      }
    }
  }

  protected AnActionEvent createEventFor(AnAction action, final VirtualFile[] files, final Project project) {
    return new AnActionEvent(null, new DataContext() {
      @Nullable
      @Override
      public Object getData(@NonNls String dataId) {
        if (CommonDataKeys.VIRTUAL_FILE_ARRAY.is(dataId)) return files;
        if (CommonDataKeys.PROJECT.is(dataId)) return project;
        return null;
      }
    }, "", action.getTemplatePresentation(), ActionManager.getInstance(), 0);
  }

  protected AnActionEvent createEventFor(AnAction action, List<PsiFile> files, final Project project, @NotNull final AdditionalEventInfo eventInfo) {
    final VirtualFile[] vFilesArray = getVirtualFileArrayFrom(files);
    return new AnActionEvent(null, new DataContext() {
      @Nullable
      @Override
      public Object getData(@NonNls String dataId) {
        if (CommonDataKeys.VIRTUAL_FILE_ARRAY.is(dataId)) return vFilesArray;
        if (CommonDataKeys.PROJECT.is(dataId)) return project;
        if (CommonDataKeys.EDITOR.is(dataId)) return eventInfo.getEditor();
        if (LangDataKeys.MODULE_CONTEXT.is(dataId)) return eventInfo.getModule();
        if (CommonDataKeys.PSI_ELEMENT.is(dataId)) return eventInfo.getElement();
        return null;
      }
    }, "", action.getTemplatePresentation(), ActionManager.getInstance(), 0);
  }


  @NotNull
  protected String getUntouchedJavaSourceForTotalProcessing(String className) {
    return createTestJavaClassTextFor(getImportListFrom(IMPORTS_LIST), className);
  }

  @NotNull
  protected String getFormattedAndImportOptimizedJavaSourceFor(String className) {
    return String.format(FORMATED_SOURCE, "", className);
  }

  @NotNull
  protected String getFormattedAndImportOptimizedAndRearrangedJavaSourceFor(String className) {
    return String.format(ARRANGED_AND_FORMATTED, className);
  }

  @NotNull
  private String createTestJavaClassTextFor(String imports, String className) {
    return String.format(TEST_SOURCE, imports, className);
  }

  @NotNull
  private String getImportListFrom(String[] imports) {
    StringBuilder sb = new StringBuilder();
    for (String anImport : imports) {
      sb.append(anImport).append('\n');
    }
    return String.valueOf(sb);
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

  private VirtualFile[] getVirtualFileArrayFrom(List<PsiFile> files) {
    VirtualFile[] array = new VirtualFile[files.size()];
    int i = 0;
    for (PsiFile f : files) {
      array[i++] = f.getVirtualFile();
    }
    return array;
  }

  protected Module createModuleWithSourceRoot(String newModuleName) {
    PsiDirectory dir = createDirectory(getTempRootDirectory().getVirtualFile(), newModuleName);
    String path = dir.getVirtualFile().getPath() + "/" + newModuleName + ".iml";

    Module module = ModuleManager.getInstance(getProject()).newModule(path, StdModuleTypes.JAVA.getId());
    PsiDirectory src = createDirectory(dir.getVirtualFile(), "src");

    PsiTestUtil.addSourceRoot(module, src.getVirtualFile());
    return module;
  }

  class TestFileStructure {
      private int myLevel;
      @NotNull private PsiDirectory myRoot;
      @NotNull private PsiDirectory myCurrentLevelDirectory;
      private List<List<PsiFile>> myFilesForLevel = new ArrayList<List<PsiFile>>();

      TestFileStructure(@NotNull PsiDirectory root) {
        myRoot = root;
        myCurrentLevelDirectory = root;
        myFilesForLevel.add(new ArrayList<PsiFile>());
        myLevel = 0;
      }

      TestFileStructure addTestFilesToCurrentDirectory(String[] names) throws IOException {
        getFilesAtLevel(myLevel).addAll(createTestFiles(myCurrentLevelDirectory, names));
        return this;
      }

      TestFileStructure createDirectoryAndMakeItCurrent(String name) {
        myLevel++;
        myFilesForLevel.add(new ArrayList<PsiFile>());
        myCurrentLevelDirectory = createDirectory(myCurrentLevelDirectory.getVirtualFile(), name);
        return this;
      }

      List<PsiFile> getFilesAtLevel(int level) {
        assert (myLevel >= level);
        return myFilesForLevel.get(level);
      }

      List<PsiFile> getAllFiles() {
        List<PsiFile> all = new ArrayList<PsiFile>();
        for (List<PsiFile> files: myFilesForLevel) {
          all.addAll(files);
        }
        return all;
      }
    }
}


class AdditionalEventInfo {
  @Nullable private Editor myEditor;
  @Nullable private Module myModule;
  @Nullable private PsiElement myElement;

  @Nullable
  Module getModule() {
    return myModule;
  }

  AdditionalEventInfo setModule(@Nullable Module module) {
    myModule = module;
    return this;
  }

  @Nullable
  Editor getEditor() {
    return myEditor;
  }

  @Nullable
  PsiElement getElement() {
    return myElement;
  }

  AdditionalEventInfo setPsiElement(@Nullable PsiElement element) {
    myElement = element;
    return this;
  }

  AdditionalEventInfo setEditor(@Nullable Editor editor) {
    myEditor = editor;
    return this;
  }
}

class MockReformatFileSettings implements LayoutCodeOptions {
  private boolean myProcessWholeFile;
  private boolean myProcessDirectories;
  private boolean myRearrange;
  private boolean myIncludeSubdirs;
  private boolean myOptimizeImports;
  private boolean myProcessOnlyChangedText;
  private boolean myIsOK = true;

  @Override
  public boolean isProcessWholeFile() {
    return myProcessWholeFile;
  }

  MockReformatFileSettings setProcessWholeFile(boolean processWholeFile) {
    myProcessWholeFile = processWholeFile;
    return this;
  }

  @Override
  public boolean isProcessDirectory() {
    return myProcessDirectories;
  }

  MockReformatFileSettings setProcessDirectory(boolean processDirectories) {
    myProcessDirectories = processDirectories;
    return this;
  }

  @Override
  public boolean isRearrangeEntries() {
    return myRearrange;
  }

  @Override
  public boolean isIncludeSubdirectories() {
    return myIncludeSubdirs;
  }

  @Override
  public boolean isOptimizeImports() {
    return myOptimizeImports;
  }

  @NotNull
  MockReformatFileSettings setOptimizeImports(boolean optimizeImports) {
    myOptimizeImports = optimizeImports;
    return this;
  }

  @Override
  public boolean isProcessOnlyChangedText() {
    return myProcessOnlyChangedText;
  }

  @NotNull
  MockReformatFileSettings setProcessOnlyChangedText(boolean processOnlyChangedText) {
    myProcessOnlyChangedText = processOnlyChangedText;
    return this;
  }

  @NotNull
  MockReformatFileSettings setRearrange(boolean rearrange) {
    myRearrange = rearrange;
    return this;
  }

  @NotNull
  MockReformatFileSettings setIncludeSubdirs(boolean includeSubdirs) {
    myIncludeSubdirs = includeSubdirs;
    return this;
  }
}


