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
package com.intellij.java.find.impl;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.find.*;
import com.intellij.find.impl.FindInProjectUtil;
import com.intellij.find.impl.FindResultImpl;
import com.intellij.find.replaceInProject.ReplaceInProjectManager;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.DumbServiceImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import com.intellij.psi.search.scope.packageSet.PackageSetFactory;
import com.intellij.psi.search.scope.packageSet.ParsingException;
import com.intellij.testFramework.*;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl;
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.FindUsagesProcessPresentation;
import com.intellij.usages.Usage;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.WaitFor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * @author MYakovlev
 * @since Oct 17, 2002
 */
public class FindManagerTest extends DaemonAnalyzerTestCase {
  private FindManager myFindManager;
  private VirtualFile[] mySourceDirs;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFindManager = FindManager.getInstance(myProject);
  }

  @Override
  protected void tearDown() throws Exception {
    myFindManager = null;
    super.tearDown();
  }

  public void testFindString() throws InterruptedException {
    FindModel findModel = FindManagerTestUtils.configureFindModel("done");

    String text = "public static class MyClass{\n/*done*/\npublic static void main(){}}";
    FindResult findResult = myFindManager.findString(text, 0, findModel);
    assertTrue(findResult.isStringFound());

    findModel = new FindModel();
    findModel.setStringToFind("done");
    findModel.setWholeWordsOnly(false);
    findModel.setFromCursor(true);
    findModel.setGlobal(true);
    findModel.setMultipleFiles(false);
    findModel.setProjectScope(true);

    findResult = myFindManager.findString(text, 40, findModel);
    assertFalse(findResult.isStringFound());

    findModel = new FindModel();
    findModel.setStringToFind("done");
    findModel.setWholeWordsOnly(false);
    findModel.setFromCursor(true);
    findModel.setGlobal(true);
    findModel.setMultipleFiles(false);
    findModel.setProjectScope(true);
    findModel.setForward(false);

    findResult = myFindManager.findString(text, 40, findModel);
    assertTrue(findResult.isStringFound());

    findModel = new FindModel();
    findModel.setStringToFind("done");
    findModel.setWholeWordsOnly(true);
    findModel.setFromCursor(false);
    findModel.setGlobal(true);
    findModel.setMultipleFiles(false);
    findModel.setProjectScope(true);

    findResult = myFindManager.findString(text, 0, findModel);
    assertTrue(findResult.isStringFound());

    findModel = new FindModel();
    findModel.setStringToFind("don");
    findModel.setWholeWordsOnly(true);
    findModel.setFromCursor(false);
    findModel.setGlobal(true);
    findModel.setMultipleFiles(false);
    findModel.setProjectScope(true);

    final FindResult[] findResultArr = new FindResult[1];
    Thread thread = findInNewThread(findModel, myFindManager, text, 0, findResultArr);
    new WaitFor(30 *1000){
      @Override
      protected boolean condition() {
        return findResultArr[0] != null;
      }
    }.assertCompleted();

    assertFalse(findResultArr[0].isStringFound());
    thread.join();
  }

  private static Thread findInNewThread(final FindModel model,
                                  final FindManager findManager,
                                  final CharSequence text,
                                  final int offset,
                                  final FindResult[] op_result){
    op_result[0] = null;
    Thread findThread = new Thread("find man test"){
      @Override
      public void run(){
        op_result[0] = findManager.findString(text, offset, model);
      }
    };
    findThread.start();
    return findThread;
  }

  public void testFindUsages() {
    initProject("findManager", "src", "src1");
    String projectDir = FileUtil.toSystemDependentName(PathManagerEx.getTestDataPath() + "/find/findManager");

    FindModel findModel = new FindModel();
    findModel.setStringToFind("done");
    findModel.setWholeWordsOnly(false);
    findModel.setFromCursor(false);
    findModel.setGlobal(true);
    findModel.setMultipleFiles(true);
    findModel.setProjectScope(true);
    findModel.setDirectoryName(projectDir + File.separatorChar + "src1");
    findModel.setWithSubdirectories(true);
    checkFindUsages(12, findModel);

    findModel.setFromCursor(false);
    findModel.setGlobal(true);
    findModel.setMultipleFiles(true);
    findModel.setProjectScope(false);
    findModel.setDirectoryName(projectDir + File.separatorChar + "src1");
    findModel.setWithSubdirectories(true);
    checkFindUsages(6, findModel);

    findModel.setWholeWordsOnly(true);
    checkFindUsages(5, findModel);
  }

  private void checkFindUsages(int expectedResults, FindModel findModel) {
    Collection<UsageInfo> usages = findUsages(findModel);
    assertEquals(expectedResults, usages.size());
  }

  private List<UsageInfo> findUsages(@NotNull FindModel findModel) {
    List<UsageInfo> result = Collections.synchronizedList(new ArrayList<>());
    final CommonProcessors.CollectProcessor<UsageInfo> collector = new CommonProcessors.CollectProcessor<>(result);
    FindInProjectUtil
      .findUsages(findModel, myProject, collector, new FindUsagesProcessPresentation(FindInProjectUtil.setupViewPresentation(true, findModel)));
    return result;
  }

  public void testFindWholeWordsInProperties() {
    initProject("findInPath", "src");
    searchProperty("xx.yy");
    searchProperty(".yy");
    searchProperty("xx.");
  }

  private void searchProperty(String query) {
    FindModel findModel = new FindModel();
    findModel.setStringToFind(query);
    findModel.setWholeWordsOnly(true);
    findModel.setFromCursor(false);
    findModel.setGlobal(true);
    findModel.setMultipleFiles(true);
    findModel.setProjectScope(true);
    findModel.setDirectoryName(mySourceDirs[0].getPath());
    findModel.setWithSubdirectories(true);

    List<UsageInfo> usages = findUsages(findModel);
    assertEquals(2, usages.size());
    if (!(usages.get(0).getFile() instanceof PsiJavaFile)) {
      Collections.swap(usages, 0, 1);
    }

    PsiElement refElement = getParentFromUsage(usages.get(0));
    assertTrue(refElement instanceof PsiLiteralExpression);
    assertEquals("xx.yy", ((PsiLiteralExpression)refElement).getValue());

    VirtualFile file = mySourceDirs[0].findFileByRelativePath("x/dd.properties");
    assertNotNull(file);
    PropertiesFile propertiesFile = (PropertiesFile)PsiManager.getInstance(myProject).findFile(file);
    assertNotNull(propertiesFile);
    refElement = getParentFromUsage(usages.get(1));
    assertTrue(refElement instanceof IProperty);
    assertSame(propertiesFile.findPropertyByKey("xx.yy"), refElement);
  }

  private static PsiElement getParentFromUsage(UsageInfo usage) {
    ProperTextRange range = usage.getRangeInElement();
    assertNotNull(range);
    PsiElement element = usage.getElement();
    assertNotNull(element);
    PsiElement elementAt = element.findElementAt(range.getStartOffset());
    assertNotNull(elementAt);
    return elementAt.getParent();
  }

  public void testFindInClassHierarchy() {
    initProject("findInClassHierarchy", "src");

    FindModel findModel = new FindModel();
    findModel.setStringToFind("instanceof");
    findModel.setWholeWordsOnly(true);
    findModel.setFromCursor(false);
    findModel.setGlobal(true);
    findModel.setMultipleFiles(true);
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
    final GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());
    final PsiClass baseClass = facade.findClass("A", scope);
    final PsiClass implClass = facade.findClass("AImpl", scope);
    findModel.setCustomScope(new LocalSearchScope(new PsiElement[]{baseClass, implClass}));

    List<UsageInfo> usages = findUsages(findModel);
    assertEquals(2, usages.size());

    final PsiClass aClass = facade.findClass("B", scope);
    findModel.setCustomScope(new LocalSearchScope(aClass));

    assertSize(1, findUsages(findModel));
  }

  public void testDollars() throws Exception {
    createFile(myModule, "A.java", "foo foo$ $foo");
    createFile(myModule, "A.txt", "foo foo$ $foo");

    FindModel findModel = new FindModel();
    findModel.setWholeWordsOnly(true);
    findModel.setFromCursor(false);
    findModel.setGlobal(true);
    findModel.setMultipleFiles(true);
    findModel.setProjectScope(true);

    findModel.setStringToFind("foo");
    assertSize(2, findUsages(findModel));

    findModel.setStringToFind("foo$");
    assertSize(2, findUsages(findModel));

    findModel.setStringToFind("$foo");
    assertSize(2, findUsages(findModel));
  }

  public void testFindInOpenedFilesIncludesNoneProjectButOpenedFile() throws IOException {
    File dir = createTempDirectory();
    File file = new File(dir.getPath(), "A.test1234");
    file.createNewFile();
    FileUtil.writeToFile(file, "foo fo foo");
    VirtualFile nonProjectFile = VfsUtil.findFileByIoFile(file, true);
    assertNotNull(nonProjectFile);

    FindModel findModel = new FindModel();
    findModel.setStringToFind("fo");
    findModel.setWholeWordsOnly(true);
    findModel.setFromCursor(false);
    findModel.setGlobal(true);
    findModel.setMultipleFiles(true);
    findModel.setCustomScope(true);
    findModel.setCustomScope(GlobalSearchScope.filesScope(myProject, ContainerUtil.list(nonProjectFile)));

    assertSize(1, findUsages(findModel));
  }

  public void testWholeWordsInNonIndexedFiles() throws Exception {
    createFile(myModule, "A.test123", "foo fo foo");

    // don't use createFile here because it creates PsiFile and runs file type autodetection
    // in real life some files might not be autodetected as plain text until the search starts 
    VirtualFile custom = new WriteCommandAction<VirtualFile>(myProject) {
      @Override
      protected void run(@NotNull Result<VirtualFile> result) throws Throwable {
        File dir = createTempDirectory();
        File file = new File(dir.getPath(), "A.test1234");
        file.createNewFile();
        FileUtil.writeToFile(file, "foo fo foo");
        addSourceContentToRoots(myModule, VfsUtil.findFileByIoFile(dir, true));
        result.setResult(VfsUtil.findFileByIoFile(file, true));
      }
    }.execute().getResultObject();
    
    assertNull(FileDocumentManager.getInstance().getCachedDocument(custom));
    assertEquals(PlainTextFileType.INSTANCE, custom.getFileType());

    FindModel findModel = new FindModel();
    findModel.setWholeWordsOnly(true);
    findModel.setFromCursor(false);
    findModel.setGlobal(true);
    findModel.setMultipleFiles(true);
    findModel.setProjectScope(true);

    findModel.setStringToFind("fo");
    assertSize(2, findUsages(findModel));
    
    // and we should get the same with text loaded
    assertNotNull(FileDocumentManager.getInstance().getDocument(custom));
    assertEquals(FileTypes.PLAIN_TEXT, custom.getFileType());

    assertSize(2, findUsages(findModel));
  }

  public void testNonRecursiveDirectory() throws Exception {
    VirtualFile root = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(createTempDirectory());
    addSourceContentToRoots(myModule, root);

    VirtualFile foo = createChildDirectory(root, "foo");
    VirtualFile bar = createChildDirectory(foo, "bar");
    createFile(myModule, root, "A.txt", "goo doo");
    createFile(myModule, foo, "A.txt", "goo doo");
    createFile(myModule, bar, "A.txt", "doo goo");

    FindModel findModel = FindManagerTestUtils.configureFindModel("done");
    findModel.setProjectScope(false);
    findModel.setDirectoryName(foo.getPath());
    findModel.setStringToFind("doo");

    findModel.setWithSubdirectories(true);
    assertSize(2, findUsages(findModel));

    findModel.setWithSubdirectories(false);
    assertSize(1, findUsages(findModel));
  }

  public void testNonSourceContent() throws Exception {
    VirtualFile root = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(createTempDirectory());
    PsiTestUtil.addContentRoot(myModule, root);

    createFile(myModule, root, "A.txt", "goo doo");

    FindModel findModel = FindManagerTestUtils.configureFindModel("goo");
    findModel.setProjectScope(false);
    findModel.setModuleName(myModule.getName());

    assertSize(1, findUsages(findModel));
  }

  public void testReplaceRegexp() {
    FindModel findModel = new FindModel();
    findModel.setStringToFind("bug_(?=here)");
    findModel.setStringToReplace("x_$0t");
    findModel.setWholeWordsOnly(false);
    findModel.setFromCursor(false);
    findModel.setGlobal(true);
    findModel.setMultipleFiles(false);
    findModel.setProjectScope(true);
    findModel.setRegularExpressions(true);
    findModel.setPromptOnReplace(false);

    myFindManager.setFindNextModel(null);
    myFindManager.getFindInFileModel().copyFrom(findModel);

    String text = "bug_here\nbug_here";
    configureByText(FileTypes.PLAIN_TEXT, text);
    assertTrue(FindUtil.replace(getProject(), getEditor(), 0, findModel));

    assertEquals("x_bug_there\nx_bug_there", getEditor().getDocument().getText());
  }

  public void testReplaceRegexp1() {
    FindModel findModel = new FindModel();
    findModel.setStringToFind("bug_(?=here)");
    findModel.setStringToReplace("$0");
    findModel.setWholeWordsOnly(false);
    findModel.setFromCursor(false);
    findModel.setGlobal(true);
    findModel.setMultipleFiles(false);
    findModel.setProjectScope(true);
    findModel.setRegularExpressions(true);
    findModel.setPromptOnReplace(false);

    myFindManager.setFindNextModel(null);
    myFindManager.getFindInFileModel().copyFrom(findModel);

    String text = "bug_here\nbug_here";
    configureByText(FileTypes.PLAIN_TEXT, text);
    assertTrue(FindUtil.replace(getProject(), getEditor(), 0, findModel));

    assertEquals(text, getEditor().getDocument().getText());
  }

  public void testReplaceWithLocalLowerCase() {
    doTestRegexpReplace("SOMETHING", "ome", "\\l$0", "SoMETHING");
  }

  public void testReplaceWithLocalUpperCase() {
    doTestRegexpReplace("something", "ome", "\\u$0", "sOmething");
  }

  public void testReplaceWithRegionLowerCase() {
    doTestRegexpReplace("SOMETHING", "ome", "\\L$0\\E", "SomeTHING");
  }

  public void testReplaceWithRegionUpperCase() {
    doTestRegexpReplace("something", "ome", "\\U$0\\E", "sOMEthing");
  }

  public void testReplaceRegexpWithNewLine() {
    FindModel findModel = new FindModel();
    findModel.setStringToFind("xxx");
    findModel.setStringToReplace("xxx\\n");
    findModel.setWholeWordsOnly(false);
    findModel.setFromCursor(false);
    findModel.setGlobal(true);
    findModel.setMultipleFiles(false);
    findModel.setProjectScope(true);
    findModel.setRegularExpressions(true);
    findModel.setPromptOnReplace(false);

    myFindManager.setFindNextModel(null);
    myFindManager.getFindInFileModel().copyFrom(findModel);

    String text = "xxx";
    configureByText(FileTypes.PLAIN_TEXT, text);
    assertTrue(FindUtil.replace(getProject(), getEditor(), 0, findModel));

    assertEquals(text+"\n", getEditor().getDocument().getText());
  }

  public void testReplaceWithRegExp() {
    doTestRegexpReplace("#  Base", "(?<!^) ", "  ", "#    Base");
  }

  private  void initProject(String folderName, final String... sourceDirs) {
    final String testDir = JavaTestUtil.getJavaTestDataPath() + "/find/" + folderName;
    ApplicationManager.getApplication().runWriteAction(() -> {
      try{
        mySourceDirs = new VirtualFile[sourceDirs.length];
        for (int i = 0; i < sourceDirs.length; i++){
          String sourcePath = testDir + "/" + sourceDirs[i];
          mySourceDirs[i] = LocalFileSystem.getInstance().refreshAndFindFileByPath(FileUtil.toSystemIndependentName(sourcePath));
        }

        VirtualFile projectDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(FileUtil.toSystemIndependentName(testDir));
        Sdk jdk = IdeaTestUtil.getMockJdk17();
        PsiTestUtil.removeAllRoots(myModule, jdk);
        PsiTestUtil.addContentRoot(myModule, projectDir);
        for (VirtualFile sourceDir : mySourceDirs) {
          PsiTestUtil.addSourceRoot(myModule, sourceDir);
        }
      }
      catch (Exception e){
        throw new RuntimeException(e);
      }
    });
  }

  public void testReplaceAll() {
    final FindModel findModel = new FindModel();
    String toFind = "xxx";
    @SuppressWarnings("SpellCheckingInspection") String toReplace = "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX";
    findModel.setStringToFind(toFind);
    findModel.setStringToReplace(toReplace);
    findModel.setWholeWordsOnly(true);
    findModel.setFromCursor(false);
    findModel.setGlobal(true);
    findModel.setMultipleFiles(false);
    findModel.setProjectScope(true);
    findModel.setRegularExpressions(false);
    findModel.setPromptOnReplace(false);

    myFindManager.setFindNextModel(null);
    myFindManager.getFindInFileModel().copyFrom(findModel);

    String text = StringUtil.repeat(toFind + "\n",6);
    configureByText(FileTypes.PLAIN_TEXT, text);

    final List<Usage> usages = FindUtil.findAll(getProject(), myEditor, findModel);
    assertNotNull(usages);
    CommandProcessor.getInstance().executeCommand(getProject(), () -> {
      for (Usage usage : usages) {
        try {
          ReplaceInProjectManager.getInstance(getProject()).replaceUsage(usage, findModel, Collections.emptySet(), false);
        }
        catch (FindManager.MalformedReplacementStringException e) {
          throw new RuntimeException(e);
        }
      }
    }, "", null);
    String newText = StringUtil.repeat(toReplace + "\n",6);
    assertEquals(newText, getEditor().getDocument().getText());
  }

  public void testFindInFileUnderLibraryUnderProject() {
    initProject("libUnderProject", "src");
    String libDir = JavaTestUtil.getJavaTestDataPath() + "/find/libUnderProject/lib";
    PsiTestUtil.addLibrary(myModule, "lib", libDir, new String[]{""}, ArrayUtil.EMPTY_STRING_ARRAY);

    FindModel findModel = new FindModel();
    findModel.setStringToFind("TargetWord");
    findModel.setFromCursor(false);
    findModel.setGlobal(true);
    findModel.setMultipleFiles(true);

    findModel.setWholeWordsOnly(false);
    assertSize(2, findUsages(findModel));

    findModel.setWholeWordsOnly(true);
    assertSize(2, findUsages(findModel));

    findModel.setWholeWordsOnly(false);
    findModel.setRegularExpressions(true);
    findModel.setStringToFind("Ta(rgetWord)");
    assertSize(2, findUsages(findModel));
  }

  public void testLocalScopeSearchPerformance() throws Exception {
    final int fileCount = 3000;
    final int lineCount = 5000;
    TempDirTestFixture fixture = new LightTempDirTestFixtureImpl();
    fixture.setUp();

    try {
      String sampleText = StringUtil.repeat("zoo TargetWord foo bar goo\n", lineCount);
      for (int i = 0; i < fileCount; i++) {
        fixture.createFile("a" + i + ".txt", sampleText);
      }
      PsiTestUtil.addSourceContentToRoots(myModule, fixture.getFile(""));

      VirtualFile file = fixture.createFile("target.txt", sampleText);
      PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
      assertNotNull(psiFile);
      final FindModel findModel = new FindModel();
      findModel.setStringToFind("TargetWord");
      findModel.setWholeWordsOnly(true);
      findModel.setFromCursor(false);
      findModel.setGlobal(true);
      findModel.setMultipleFiles(true);
      findModel.setCustomScope(true);

      ThrowableRunnable test = () -> assertSize(lineCount, findUsages(findModel));

      findModel.setCustomScope(GlobalSearchScope.fileScope(psiFile));
      PlatformTestUtil.startPerformanceTest("find usages in global", 400, test).attempts(2).usesAllCPUCores().assertTiming();

      findModel.setCustomScope(new LocalSearchScope(psiFile));
      PlatformTestUtil.startPerformanceTest("find usages in local", 120, test).attempts(2).usesAllCPUCores().assertTiming();
    }
    finally {
      fixture.tearDown();
    }
  }
  
  public void testFindInCommentsInJsInsideHtml() {
    FindModel findModel = FindManagerTestUtils.configureFindModel("@param t done");

    String text = "<script>\n" +
                  "/**\n" +
                  " * @param t done\n" +
                  " * @param t done\n" +
                  " * @param t done\n" +
                  "*/</script>";
    findModel.setSearchContext(FindModel.SearchContext.IN_COMMENTS);
    FindManager findManager = FindManager.getInstance(myProject);
    FindManagerTestUtils.runFindForwardAndBackward(findManager, findModel, text, "html");

    findModel.setRegularExpressions(true);
    FindManagerTestUtils.runFindForwardAndBackward(findManager, findModel, text, "html");

    FindManagerTestUtils.runFindForwardAndBackward(findManager, findModel, text, "php");
    findModel.setRegularExpressions(false);
    FindManagerTestUtils.runFindForwardAndBackward(findManager, findModel, text, "php");
  }
  
  public void testFindInCommentsAndLiterals() {
    FindModel findModel = FindManagerTestUtils.configureFindModel("done");

    String text = "\"done done done\" /* done done done */";
    FindManagerTestUtils.runFindInCommentsAndLiterals(myFindManager, findModel, text);

    findModel.setRegularExpressions(true);
    FindManagerTestUtils.runFindInCommentsAndLiterals(myFindManager, findModel, text);
  }

  public void testReplacePreserveCase() {
    configureByText(FileTypes.PLAIN_TEXT, "Bar bar BAR");
    FindModel model = new FindModel();
    model.setStringToFind("bar");
    model.setStringToReplace("foo");
    model.setPromptOnReplace(false);
    model.setPreserveCase(true);

    FindUtil.replace(myProject, myEditor, 0, model);
    assertEquals("Foo foo FOO", myEditor.getDocument().getText());

    model.setStringToFind("Foo");
    model.setStringToReplace("Bar");
    FindUtil.replace(myProject, myEditor, 0, model);
    assertEquals("Bar bar BAR", myEditor.getDocument().getText());

    configureByText(FileTypes.PLAIN_TEXT, "Bar bar");

    model.setStringToFind("bar");
    model.setStringToReplace("fooBar");

    FindUtil.replace(myProject, myEditor, 0, model);
    assertEquals("FooBar fooBar", myEditor.getDocument().getText());

    configureByText(FileTypes.PLAIN_TEXT, "abc1 Abc1 ABC1");

    model.setStringToFind("ABC1");
    model.setStringToReplace("DEF1");

    FindUtil.replace(myProject, myEditor, 0, model);
    assertEquals("def1 Def1 DEF1", myEditor.getDocument().getText());
  }

  public void testFindWholeWords() {
    configureByText(FileTypes.PLAIN_TEXT, "-- -- ---");
    FindModel model = new FindModel();
    model.setStringToFind("--");
    model.setWholeWordsOnly(true);

    List<Usage> usages = FindUtil.findAll(myProject, myEditor, model);
    assertNotNull(usages);
    assertEquals(2, usages.size());

    configureByText(FileTypes.PLAIN_TEXT, "myproperty=@AspectJ");
    model = new FindModel();
    model.setStringToFind("@AspectJ");
    model.setWholeWordsOnly(true);
    usages = FindUtil.findAll(myProject, myEditor, model);
    assertNotNull(usages);
    assertEquals(1, usages.size());
  }

  public void testFindInCurrentFileOutsideProject() throws Exception {
    final TempDirTestFixture tempDirFixture = new TempDirTestFixtureImpl();
    tempDirFixture.setUp();
    try {
      VirtualFile file = tempDirFixture.createFile("a.txt", "foo bar foo");
      FindModel findModel = FindManagerTestUtils.configureFindModel("foo");
      findModel.setWholeWordsOnly(true);
      findModel.setCustomScope(true);
      findModel.setCustomScope(new LocalSearchScope(PsiManager.getInstance(myProject).findFile(file)));
      assertSize(2, findUsages(findModel));
    }
    finally {
      tempDirFixture.tearDown();
    }
  }

  public void testFindInDirectoryOutsideProject() throws Exception {
    final TempDirTestFixture tempDirFixture = new TempDirTestFixtureImpl();
    tempDirFixture.setUp();
    try {
      tempDirFixture.createFile("a.txt", "foo bar foo");
      FindModel findModel = FindManagerTestUtils.configureFindModel("foo");
      findModel.setWholeWordsOnly(true);
      findModel.setProjectScope(false);
      findModel.setDirectoryName(tempDirFixture.getFile("").getPath());
      assertSize(2, findUsages(findModel));
    }
    finally {
      tempDirFixture.tearDown();
    }
  }

  public void testFindInExcludedDirectory() throws Exception {
    VirtualFile root = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(createTempDirectory());
    addSourceContentToRoots(myModule, root);
    VirtualFile excluded = createChildDirectory(root, "excluded");
    createFile(myModule, excluded, "a.txt", "foo bar foo");
    PsiTestUtil.addExcludedRoot(myModule, excluded);

    FindModel findModel = FindManagerTestUtils.configureFindModel("foo");
    findModel.setWholeWordsOnly(true);
    findModel.setProjectScope(false);
    findModel.setDirectoryName(excluded.getPath());
    assertSize(2, findUsages(findModel));

    findModel.setDirectoryName(root.getPath());
    assertSize(0, findUsages(findModel));
    Registry.get("find.search.in.excluded.dirs").setValue(true, getTestRootDisposable());
    assertSize(2, findUsages(findModel));
  }

  public void testFindInJavaDocs() {
    FindModel findModel = FindManagerTestUtils.configureFindModel("done");
    String text = "/** done done done */";

    findModel.setSearchContext(FindModel.SearchContext.IN_COMMENTS);
    FindManagerTestUtils.runFindForwardAndBackward(myFindManager, findModel, text);

    findModel.setRegularExpressions(true);
    FindManagerTestUtils.runFindForwardAndBackward(myFindManager, findModel, text);
  }

  public void testFindInCommentsProperlyWorksWithOffsets() {
    FindModel findModel = FindManagerTestUtils.configureFindModel("done");

    String prefix = "/*";
    String text = prefix + "done*/";

    findModel.setSearchContext(FindModel.SearchContext.IN_COMMENTS);
    LightVirtualFile file = new LightVirtualFile("A.java", text);

    FindResult findResult = myFindManager.findString(text, prefix.length(), findModel, file);
    assertTrue(findResult.isStringFound());

    findModel.setRegularExpressions(true);
    findResult = myFindManager.findString(text, prefix.length(), findModel, file);
    assertTrue(findResult.isStringFound());
  }

  public void testFindInUserFileType() {
    FindModel findModel = FindManagerTestUtils.configureFindModel("done");
    String text = "\"done done\"; 'done'; // done\n" +
                  "/* done\n" +
                  "done */";
    FindManagerTestUtils.runFindInCommentsAndLiterals(myFindManager, findModel, text, "cs");
  }

  public void testFindInLiteralToSkipQuotes() {
    FindModel findModel = FindManagerTestUtils.configureFindModel("^done$");

    findModel.setRegularExpressions(true);
    findModel.setSearchContext(FindModel.SearchContext.IN_STRING_LITERALS);
    String text = "\"done\"; 'done'; 'done' \"done2\"";
    FindManagerTestUtils.runFindForwardAndBackward(myFindManager, findModel, text, "java");

    findModel.setStringToFind("done");
    findModel.setWholeWordsOnly(true);
    findModel.setRegularExpressions(false);
    text = "\"\"; \"done\"; 'done'; 'done' \"done2\"";
    FindManagerTestUtils.runFindForwardAndBackward(myFindManager, findModel, text, "java");
  }

  public void testFindInJavaDoc() {
    FindModel findModel = FindManagerTestUtils.configureFindModel("do ne");
    findModel.setWholeWordsOnly(true);

    String text = "/** do ne do ne do ne */";

    findModel.setSearchContext(FindModel.SearchContext.IN_COMMENTS);
    FindManagerTestUtils.runFindForwardAndBackward(myFindManager, findModel, text, "java");
  }

  public void testPlusWholeWordsOnly() throws Exception {
    createFile(myModule, "A.java", "3 + '+' + 2");

    FindModel findModel = FindManagerTestUtils.configureFindModel("'+' +");
    findModel.setMultipleFiles(true);

    assertSize(1, findUsages(findModel));

    findModel.setCaseSensitive(true);
    assertSize(1, findUsages(findModel));

    findModel.setWholeWordsOnly(true);
    assertSize(1, findUsages(findModel));
  }

  public void testRegExpInString() {
    FindModel findModel = FindManagerTestUtils.configureFindModel("^*$");

    String prefix = "<foo bar=\"";
    String text = prefix + "\" />";

    findModel.setSearchContext(FindModel.SearchContext.IN_STRING_LITERALS);
    findModel.setRegularExpressions(true);
    LightVirtualFile file = new LightVirtualFile("A.xml", text);

    FindResult findResult = myFindManager.findString(text, prefix.length(), findModel, file);
    assertTrue(findResult.isStringFound());
  }

  public void testFindExceptComments() {
    FindModel findModel = FindManagerTestUtils.configureFindModel("done");

    String prefix = "/*";
    String text = prefix + "done*/done";

    findModel.setSearchContext(FindModel.SearchContext.EXCEPT_COMMENTS);
    LightVirtualFile file = new LightVirtualFile("A.java", text);

    FindResult findResult = myFindManager.findString(text, prefix.length(), findModel, file);
    assertTrue(findResult.isStringFound());
    assertTrue(findResult.getStartOffset() > prefix.length());

    findModel.setRegularExpressions(true);
    findResult = myFindManager.findString(text, prefix.length(), findModel, file);
    assertTrue(findResult.isStringFound());
    assertTrue(findResult.getStartOffset() > prefix.length());
  }

  public void testFindExceptComments2() {
    FindModel findModel = FindManagerTestUtils.configureFindModel("oo");

    String text = "//oooo";

    findModel.setSearchContext(FindModel.SearchContext.EXCEPT_COMMENTS);
    LightVirtualFile file = new LightVirtualFile("A.java", text);

    FindResult findResult = myFindManager.findString(text, 0, findModel, file);
    assertFalse(findResult.isStringFound());
  }

  public void testFindExceptLiterals() {
    FindModel findModel = FindManagerTestUtils.configureFindModel("done");

    String prefix = "\"";
    String text = prefix + "done\"done";

    findModel.setSearchContext(FindModel.SearchContext.EXCEPT_STRING_LITERALS);
    LightVirtualFile file = new LightVirtualFile("A.java", text);

    FindResult findResult = myFindManager.findString(text, prefix.length(), findModel, file);
    assertTrue(findResult.isStringFound());
    assertTrue(findResult.getStartOffset() > prefix.length());

    findModel.setRegularExpressions(true);
    findResult = myFindManager.findString(text, prefix.length(), findModel, file);
    assertTrue(findResult.isStringFound());
    assertTrue(findResult.getStartOffset() > prefix.length());
  }

  public void testNoExceptionDuringReplaceAll() {
    configureByText(FileTypes.PLAIN_TEXT, "something\telse");
    FindModel model = new FindModel();
    model.setStringToFind("m");
    model.setStringToReplace("M");
    model.setPromptOnReplace(false);
    FindUtil.replace(myProject, myEditor, 0, model);
    assertEquals("soMething\telse", myEditor.getDocument().getText());
  }

  public void testSearchInDumbMode() throws Exception {
    createFile("a.java", "foo bar foo true");
    FindModel findModel = FindManagerTestUtils.configureFindModel("true");
    findModel.setWholeWordsOnly(true);
    DumbServiceImpl.getInstance(getProject()).setDumb(true);
    try {
      assertSize(1, findUsages(findModel));
    }
    finally {
      DumbServiceImpl.getInstance(getProject()).setDumb(false);
    }
  }

  public void testNoFilesFromAdditionalIndexedRootsWithCustomExclusionScope() throws ParsingException {
    FindModel findModel = FindManagerTestUtils.configureFindModel("Object.prototype.__defineGetter__");
    PackageSet compile = PackageSetFactory.getInstance().compile("!src[subdir]:*..*");
    findModel.setCustomScope(GlobalSearchScopesCore.filterScope(myProject, new NamedScope.UnnamedScope(compile)));
    findModel.setCustomScope(true);
    assertSize(0, findUsages(findModel));
  }

  public void testRegexReplacementStringForIndices() {
    assertEquals("public static   MyType my   = 1;", FindInProjectUtil.buildStringToFindForIndicesFromRegExp("public static (@A)? MyType my\\w+?  = 1;", myProject));
    assertEquals(" Foo ", FindInProjectUtil.buildStringToFindForIndicesFromRegExp("\\bFoo\\b", myProject));
    assertEquals("", FindInProjectUtil.buildStringToFindForIndicesFromRegExp("foo|bar", myProject));
    assertEquals(" Exit Foo Bar Baz", FindInProjectUtil.buildStringToFindForIndicesFromRegExp("\\nExit\\tFoo\\rBar\\fBaz", myProject));
    assertEquals(" Foo Bar Baz Exit", FindInProjectUtil.buildStringToFindForIndicesFromRegExp("\\012Foo\\u000ABar\\x0ABaz\\aExit", myProject));
    assertEquals(" Foo Bar BazCooBoo", FindInProjectUtil.buildStringToFindForIndicesFromRegExp("\\1Foo\\sBar\\DBaz\\QCoo\\E\\QBoo", myProject));
  }

  public void testCreateFileMaskCondition() {
    Condition<CharSequence> condition = FindInProjectUtil.createFileMaskCondition("*.java, *.js, !Foo.java, !*.min.js");
    assertTrue(condition.value("Bar.java"));
    assertTrue(!condition.value("Bar.javac"));
    assertTrue(!condition.value("Foo.java"));
    assertTrue(!condition.value("Foo.jav"));
    assertTrue(!condition.value("Foo.min.js"));
    assertTrue(condition.value("Foo.js"));

    condition = FindInProjectUtil.createFileMaskCondition("!Foo.java");
    assertTrue(condition.value("Bar.java"));
    assertTrue(!condition.value("Foo.java"));
    assertTrue(condition.value("Foo.js"));
    assertTrue(condition.value("makefile"));
  }

  public void testRegExpSOEWhenMatch() {
    String text = "package com.intellij.demo;\n" +
                  "\n";
    for(int i = 0; i < 10; ++i) text += text;

    FindModel findModel = FindManagerTestUtils.configureFindModel(";((?:\\n|.)*export default )(?:DS.Model)(.extend((?:\\n|.)*assets: DS.attr(?:\\n|.)*});)");
    findModel.setRegularExpressions(true);

    FindResult findResult = myFindManager.findString(text, 0, findModel, null);
    assertTrue(!findResult.isStringFound());
  }

  public void testRegExpSOEWhenMatch2() {
    String text = "package com.intellij.demo;\n" +
                  "\n";
    for(int i = 0; i < 10; ++i) text += text;
    text += "public class Foo {}";

    FindModel findModel = FindManagerTestUtils.configureFindModel("package((?:\\n|.)+)class (\\w+)");
    findModel.setRegularExpressions(true);

    FindResult findResult = myFindManager.findString(text, 0, findModel, null);
    assertTrue(findResult.isStringFound());

    findModel = FindManagerTestUtils.configureFindModel("package((.|\\n)+)class (\\w+)");
    findModel.setRegularExpressions(true);

    findResult = myFindManager.findString(text, 0, findModel, null);
    assertTrue(findResult.isStringFound());

    findModel = FindManagerTestUtils.configureFindModel("package(([^\\n]|\\n)+)class (\\w+)");
    findModel.setRegularExpressions(true);

    findResult = myFindManager.findString(text, 0, findModel, null);
    assertTrue(!findResult.isStringFound()); // SOE, no match
  }

  public void testFindRegexpThatMatchesWholeFile() throws Exception {
    FindModel findModel = FindManagerTestUtils.configureFindModel("[^~]+\\Z");
    findModel.setRegularExpressions(true);

    createFile("a.java", "some text");

    findModel.setRegularExpressions(true);
    List<UsageInfo> usages = findUsages(findModel);
    assertSize(1, usages);

    assertTrue(usages.get(0).isValid());
  }

  public void testRegExpMatchReplacement() throws FindManager.MalformedReplacementStringException {
    String text = "final override val\n" +
                  "      d1PrimitiveType by lazyThreadSafeIdempotentGenerator { D1PrimitiveType( typeManager = this ) }";
    String pattern = "final override val\n" +
                     "d(\\w+)PrimitiveType by lazyThreadSafeIdempotentGenerator \\{ D(\\w+)PrimitiveType\\( typeManager = this \\) \\}";
    String replacement = "";

    FindModel findModel = FindManagerTestUtils.configureFindModel(pattern);

    findModel.setRegularExpressions(true);
    findModel.setMultiline(true);

    FindResult findResult = myFindManager.findString(text, 0, findModel, null);
    assertTrue(findResult.isStringFound());
    assertEquals(replacement, myFindManager.getStringToReplace(findResult.substring(text), findModel, 0, text));
  }

  public void testRegExpSearchDoesCheckCancelled() throws InterruptedException {
    String text = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";
    FindModel findModel = FindManagerTestUtils.configureFindModel("(x+x+)+y");
    findModel.setRegularExpressions(true);

    runAsyncTest(text, findModel);
    findModel.setSearchContext(FindModel.SearchContext.IN_COMMENTS);
    runAsyncTest("/*" + text + "*/", findModel);
  }

  public void testProperInsensitiveSearchForRegExp() throws Exception {
    createFile("a.java", "Цитрус цитрус");
    FindModel findModel = FindManagerTestUtils.configureFindModel("цитрус");
    findModel.setRegularExpressions(true);
    assertSize(2, findUsages(findModel));
  }

  public void testProperHandlingOfEmptyLinesWhenReplacingWithRegExp() {
    doTestRegexpReplace(
      "foo\n\n\n",
      "^",
      "// ",
      "// foo\n// \n// \n");
  }

  private void runAsyncTest(String text, FindModel findModel) throws InterruptedException {
    final Ref<FindResult> result = new Ref<>();
    final CountDownLatch progressStarted = new CountDownLatch(1);
    final ProgressIndicatorBase progressIndicatorBase = new ProgressIndicatorBase();
    final Thread thread = new Thread(() -> ProgressManager.getInstance().runProcess(() -> {
      try {
        progressStarted.countDown();
        result.set(myFindManager.findString(text, 0, findModel, new LightVirtualFile("foo.java")));
      }
      catch (ProcessCanceledException ex) {
        result.set(new FindResultImpl());
      }
    }, progressIndicatorBase), "runAsyncTest");
    thread.start();

    progressStarted.await();
    thread.join(100);
    progressIndicatorBase.cancel();
    thread.join(500);
    assertNotNull(result.get());
    assertTrue(!result.get().isStringFound());
    thread.join();
  }

  private void doTestRegexpReplace(String initialText, String searchString, String replaceString, String expectedResult) {
    configureByText(FileTypes.PLAIN_TEXT, initialText);
    FindModel model = new FindModel();
    model.setRegularExpressions(true);
    model.setStringToFind(searchString);
    model.setStringToReplace(replaceString);
    model.setPromptOnReplace(false);
    assertTrue(FindUtil.replace(myProject, myEditor, 0, model));
    assertEquals(expectedResult, myEditor.getDocument().getText());
  }

  public void testDataContextProcessing() {
    initProject("findInPath", "src");
    VirtualFile directory = mySourceDirs[0].findFileByRelativePath("x");
    VirtualFile file = directory.findChild("dd.properties");
    assertNotNull(file);
    SearchScope scope = GlobalSearchScope.filesScope(myProject, ContainerUtil.list(directory));
    Module module = ModuleManager.getInstance(myProject).getModules()[0];
    String moduleName = module.getName();
    String dirName = directory.getPresentableUrl();

    FindModel model = new FindModel();
    model.setDirectoryName("initialDirectoryName");
    model.setModuleName("initialModuleName");
    model.setProjectScope(false);
    model.setCustomScopeName("initialScopeName");

    checkContext(model, myProject, directory, module, false, null, moduleName, false);
    checkContext(model, myProject, directory, null, false, dirName, moduleName, false);//prev module state
    checkContext(model, myProject, null, null, true, dirName, moduleName, false);//prev module and dir state
    model.setCustomScope(scope);
    model.setCustomScope(true);
    checkContext(model, myProject, null, null, false, dirName, moduleName, true);//prev module and dir state
  }

  private static void checkContext(FindModel model, Project project, VirtualFile directory, Module module,
                                   boolean shouldBeProjectScope, String expectedDirectoryName, String expectedModuleName, boolean shouldBeCustomScope) {
    MapDataContext dataContext = new MapDataContext();
    dataContext.put(CommonDataKeys.PROJECT, project);
    dataContext.put(CommonDataKeys.VIRTUAL_FILE, directory);
    dataContext.put(LangDataKeys.MODULE_CONTEXT, module);
    FindInProjectUtil.setDirectoryName(model, dataContext);
    assertEquals(shouldBeProjectScope, model.isProjectScope());
    assertEquals(expectedDirectoryName, model.getDirectoryName());
    assertEquals(expectedModuleName, model.getModuleName());
    assertEquals(shouldBeCustomScope, model.isCustomScope());
  }
}
