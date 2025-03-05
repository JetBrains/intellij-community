// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.find.impl.FindInProjectUtil;
import com.intellij.find.impl.FindResultImpl;
import com.intellij.find.replaceInProject.ReplaceInProjectManager;
import com.intellij.idea.ExcludeFromTestDiscovery;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
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
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.WaitFor;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ExcludeFromTestDiscovery
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

  public void testFindInDirectoryCorrectlyFindVirtualFileForJars() {
    FindModel findModel = FindManagerTestUtils.configureFindModel("done");
    VirtualFile rtJar = Stream.of(getTestProjectJdk().getRootProvider().getFiles(OrderRootType.CLASSES))
      .filter(file -> file.getPath().contains("rt.jar"))
      .map(file -> JarFileSystem.getInstance().getLocalByEntry(file))
      .findFirst().orElse(null);
    assertNotNull(rtJar);

    findModel.setProjectScope(false);
    findModel.setDirectoryName(rtJar.getPath());
    assertNotNull(FindInProjectUtil.getDirectory(findModel));

    VirtualFile jarRootForLocalFile = JarFileSystem.getInstance().getJarRootForLocalFile(rtJar);
    VirtualFile jarPackageInRtJar = jarRootForLocalFile.findChild("java");
    assertNotNull(jarPackageInRtJar);
    findModel.setDirectoryName(jarPackageInRtJar.getPath());
    assertNotNull(FindInProjectUtil.getDirectory(findModel));
  }

  public void testFindString() throws InterruptedException, ExecutionException {
    FindModel findModel = FindManagerTestUtils.configureFindModel("done");
    @Language("JAVA") @SuppressWarnings("ALL")
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
    Future<?> thread = findInNewThread(findModel, myFindManager, text, findResultArr);
    new WaitFor(30 *1000){
      @Override
      protected boolean condition() {
        return findResultArr[0] != null;
      }
    }.assertCompleted();

    assertFalse(findResultArr[0].isStringFound());
    thread.get();
  }

  private static Future<?> findInNewThread(FindModel model, FindManager findManager, CharSequence text, FindResult[] op_result){
    op_result[0] = null;
    return ApplicationManager.getApplication().executeOnPooledThread(() -> {
        op_result[0] = findManager.findString(text, 0, model);
      }
    );
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
    Collection<UsageInfo> usages = findInProject(findModel);
    assertEquals(expectedResults, usages.size());
  }

  private List<UsageInfo> findInProject(@NotNull FindModel findModel) {
    List<UsageInfo> result = Collections.synchronizedList(new ArrayList<>());
    FindUsagesProcessPresentation presentation = new FindUsagesProcessPresentation(FindInProjectUtil.setupViewPresentation(true, findModel));
    FindInProjectUtil.findUsages(findModel, myProject, new CommonProcessors.CollectProcessor<>(result), presentation);
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

    List<UsageInfo> usages = findInProject(findModel);
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
    PsiClass baseClass = Objects.requireNonNull(facade.findClass("A", scope));
    PsiClass implClass = Objects.requireNonNull(facade.findClass("AImpl", scope));
    findModel.setCustomScope(new LocalSearchScope(new PsiElement[]{baseClass, implClass}));

    List<UsageInfo> usages = findInProject(findModel);
    assertEquals(2, usages.size());

    PsiClass aClass = Objects.requireNonNull(facade.findClass("B", scope));
    findModel.setCustomScope(new LocalSearchScope(aClass));

    assertSize(1, findInProject(findModel));
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
    assertSize(2, findInProject(findModel));

    findModel.setStringToFind("foo$");
    assertSize(2, findInProject(findModel));

    findModel.setStringToFind("$foo");
    assertSize(2, findInProject(findModel));
  }

  public void testFindInOpenedFilesIncludesNoneProjectButOpenedFile() throws IOException {
    File dir = createTempDirectory();
    File file = new File(dir.getPath(), "A.test1234");
    assertTrue(file.createNewFile());
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
    findModel.setCustomScope(GlobalSearchScope.filesScope(myProject, Arrays.asList(nonProjectFile)));

    assertSize(1, findInProject(findModel));
  }

  public void testWholeWordsInNonIndexedFiles() throws Exception {
    createFile(myModule, "A.test123", "foo fo foo");

    // don't use createFile here because it creates PsiFile and runs file type autodetection
    // in real life some files might not be autodetected as plain text until the search starts
    VirtualFile custom = WriteCommandAction.writeCommandAction(myProject).compute(() -> {
      File dir = createTempDirectory();
      File file = new File(dir.getPath(), "A.test1234");
      assertTrue(file.createNewFile());
      FileUtil.writeToFile(file, "foo fo foo");
      addSourceContentToRoots(myModule, VfsUtil.findFileByIoFile(dir, true));
      return VfsUtil.findFileByIoFile(file, true);
    });

    assertNull(FileDocumentManager.getInstance().getCachedDocument(custom));
    assertEquals(PlainTextFileType.INSTANCE, custom.getFileType());

    FindModel findModel = new FindModel();
    findModel.setWholeWordsOnly(true);
    findModel.setFromCursor(false);
    findModel.setGlobal(true);
    findModel.setMultipleFiles(true);
    findModel.setProjectScope(true);

    findModel.setStringToFind("fo");
    assertSize(2, findInProject(findModel));

    // and we should get the same with text loaded
    assertNotNull(FileDocumentManager.getInstance().getDocument(custom));
    assertEquals(FileTypes.PLAIN_TEXT, custom.getFileType());

    assertSize(2, findInProject(findModel));
  }

  public void testNonRecursiveDirectory() throws Exception {
    VirtualFile root = getTempDir().createVirtualDir();
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
    assertSize(2, findInProject(findModel));

    findModel.setWithSubdirectories(false);
    assertSize(1, findInProject(findModel));
  }

  public void testNonSourceContent() throws Exception {
    VirtualFile root = getTempDir().createVirtualDir();
    PsiTestUtil.addContentRoot(myModule, root);

    createFile(myModule, root, "A.txt", "goo doo");

    FindModel findModel = FindManagerTestUtils.configureFindModel("goo");
    findModel.setProjectScope(false);
    findModel.setModuleName(myModule.getName());

    assertSize(1, findInProject(findModel));
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
    findModel.setStringToFind(toFind);
    String toReplace = "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX";
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

    final List<Usage> usages = FindUtil.findAll(getProject(), myFile, findModel);
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
    PsiTestUtil.addLibrary(myModule, "lib", libDir, new String[]{""}, ArrayUtilRt.EMPTY_STRING_ARRAY);

    FindModel findModel = new FindModel();
    findModel.setStringToFind("TargetWord");
    findModel.setFromCursor(false);
    findModel.setGlobal(true);
    findModel.setMultipleFiles(true);

    findModel.setWholeWordsOnly(false);
    assertSize(2, findInProject(findModel));

    findModel.setWholeWordsOnly(true);
    assertSize(2, findInProject(findModel));

    findModel.setWholeWordsOnly(false);
    findModel.setRegularExpressions(true);
    findModel.setStringToFind("Ta(rgetWord)");
    assertSize(2, findInProject(findModel));
  }

  public void testLocalScopeSearchDoesNotLoadUnrelatedFiles() throws Exception {
    int lineCount = 500;
    TempDirTestFixture fixture = new LightTempDirTestFixtureImpl();
    fixture.setUp();

    try {
      String sampleText = StringUtil.repeat("zoo TargetWord foo bar goo\n", lineCount);
      VirtualFile unrelated = fixture.createFile("another.txt", sampleText);
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

      assertNull(FileDocumentManager.getInstance().getCachedDocument(unrelated));

      findModel.setCustomScope(GlobalSearchScope.fileScope(psiFile));
      assertSize(lineCount, findInProject(findModel));
      assertNull(FileDocumentManager.getInstance().getCachedDocument(unrelated));

      findModel.setCustomScope(new LocalSearchScope(psiFile));
      assertSize(lineCount, findInProject(findModel));
      assertNull(FileDocumentManager.getInstance().getCachedDocument(unrelated));
    }
    finally {
      fixture.tearDown();
    }
  }

  public void testFindInCommentsAndLiterals() {
    FindModel findModel = FindManagerTestUtils.configureFindModel("done");

    String text = "\"done done done\" /* done done done */";
    FindManagerTestUtils.runFindInCommentsAndLiterals(myFindManager, findModel, text);

    findModel.setRegularExpressions(true);
    FindManagerTestUtils.runFindInCommentsAndLiterals(myFindManager, findModel, text);
  }

  public void testFindWholeWords() {
    configureByText(FileTypes.PLAIN_TEXT, "-- -- ---");
    FindModel model = new FindModel();
    model.setStringToFind("--");
    model.setWholeWordsOnly(true);

    List<Usage> usages = FindUtil.findAll(myProject, myFile, model);
    assertNotNull(usages);
    assertEquals(2, usages.size());

    configureByText(FileTypes.PLAIN_TEXT, "myproperty=@AspectJ");
    model = new FindModel();
    model.setStringToFind("@AspectJ");
    model.setWholeWordsOnly(true);
    usages = FindUtil.findAll(myProject, myFile, model);
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
      assertSize(2, findInProject(findModel));
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
      assertSize(2, findInProject(findModel));
    }
    finally {
      tempDirFixture.tearDown();
    }
  }

  public void testFindInExcludedDirectory() throws Exception {
    VirtualFile root = getTempDir().createVirtualDir();
    addSourceContentToRoots(myModule, root);
    VirtualFile excluded = createChildDirectory(root, "excluded");
    VirtualFile aTxt = createFile(myModule, excluded, "a.txt", "foo bar foo").getVirtualFile();
    PsiTestUtil.addExcludedRoot(myModule, excluded);

    FindModel findModel = FindManagerTestUtils.configureFindModel("foo");
    findModel.setWholeWordsOnly(true);
    findModel.setProjectScope(false);
    findModel.setDirectoryName(excluded.getPath());
    assertSize(2, findInProject(findModel));

    findModel.setDirectoryName(root.getPath());

    var fileIndex = ProjectRootManager.getInstance(getProject()).getFileIndex();
    assertTrue(fileIndex.isExcluded(aTxt));
    assertTrue(fileIndex.isExcluded(excluded));
    assertFalse(fileIndex.isExcluded(root));
    assertFalse(Registry.is("find.search.in.excluded.dirs"));
    assertEmpty(
      FindInProjectSearchEngine.EP_NAME.getExtensionList().stream()
        .map(it -> it.createSearcher(findModel, getProject()))
        .filter(it -> it != null)
        .flatMap(it -> it.searchForOccurrences().stream())
        .collect(Collectors.toList())
    );

    assertSize(0, findInProject(findModel));
    Registry.get("find.search.in.excluded.dirs").setValue(true, getTestRootDisposable());
    assertSize(2, findInProject(findModel));
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
    String text = """
      "done done"; 'done'; // done
      /* done
      done */""";
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

    assertSize(1, findInProject(findModel));

    findModel.setCaseSensitive(true);
    assertSize(1, findInProject(findModel));

    findModel.setWholeWordsOnly(true);
    assertSize(1, findInProject(findModel));
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

  public void testRegExpWithWords() throws Exception {
    createFile(myModule, "A.txt", "abc(edf)");
    FindModel findModel = FindManagerTestUtils.configureFindModel("abc\\(edf");
    findModel.setRegularExpressions(true);
    List<UsageInfo> usages = findInProject(findModel);
    assertEquals(1, usages.size());
  }

  public void testRegExpInString2() throws Exception {
    FindModel findModel = FindManagerTestUtils.configureFindModel("\\b");
    String text = "\"abc def\"";

    findModel.setSearchContext(FindModel.SearchContext.IN_STRING_LITERALS);
    findModel.setRegularExpressions(true);
    LightVirtualFile file = new LightVirtualFile("A.java", text);

    FindResult findResult = myFindManager.findString(text, 0, findModel, file);
    assertTrue(findResult.isStringFound());
    assertEquals(1, findResult.getStartOffset());

    findResult = myFindManager.findString(text, findResult.getStartOffset() + 1, findModel, file);
    assertTrue(findResult.isStringFound());
    assertEquals(4, findResult.getStartOffset());

    findResult = myFindManager.findString(text, findResult.getStartOffset() + 1, findModel, file);
    assertTrue(findResult.isStringFound());
    assertEquals(5, findResult.getStartOffset());

    findResult = myFindManager.findString(text, findResult.getStartOffset() + 1, findModel, file);
    assertTrue(findResult.isStringFound());
    assertEquals(8, findResult.getStartOffset());

    findResult = myFindManager.findString(text, findResult.getStartOffset() + 1, findModel, file);
    assertFalse(findResult.isStringFound());

    createFile(myModule, "A.java", text);
    List<UsageInfo> usagesInProject = findInProject(findModel);
    assertEquals(4, usagesInProject.size());
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
    DumbModeTestUtils.runInDumbModeSynchronously(getProject(), () -> {
      assertSize(1, findInProject(findModel));
    });
  }

  public void testNoFilesFromAdditionalIndexedRootsWithCustomExclusionScope() throws ParsingException {
    FindModel findModel = FindManagerTestUtils.configureFindModel("Object.prototype.__defineGetter__");
    PackageSet compile = PackageSetFactory.getInstance().compile("!src[subdir]:*..*");
    findModel.setCustomScope(GlobalSearchScopesCore.filterScope(myProject, new NamedScope.UnnamedScope(compile)));
    findModel.setCustomScope(true);
    assertSize(0, findInProject(findModel));
  }

  public void testRegexReplacementStringForIndices() {
    assertEquals("public static   MyType my   = 1;", FindInProjectUtil.buildStringToFindForIndicesFromRegExp("public static (@A)? MyType my\\w+?  = 1;", myProject));
    assertEquals("Foo", FindInProjectUtil.buildStringToFindForIndicesFromRegExp("\\bFoo\\b", myProject));
    assertEquals("", FindInProjectUtil.buildStringToFindForIndicesFromRegExp("foo|bar", myProject));
    assertEquals("Exit Foo Bar Baz", FindInProjectUtil.buildStringToFindForIndicesFromRegExp("\\nExit\\tFoo\\rBar\\fBaz", myProject));
    assertEquals("Foo Bar Baz Exit", FindInProjectUtil.buildStringToFindForIndicesFromRegExp("\\012Foo\\u000ABar\\x0ABaz\\aExit", myProject));
    assertEquals("Foo Bar BazCooBoo", FindInProjectUtil.buildStringToFindForIndicesFromRegExp("\\1Foo\\sBar\\DBaz\\QCoo\\E\\QBoo", myProject));
  }

  public void testCreateFileMaskCondition() {
    Condition<CharSequence> condition = FindInProjectUtil.createFileMaskCondition("*.java, *.js, !Foo.java, !*.min.js");
    assertTrue(condition.value("Bar.java"));
    assertFalse(condition.value("Bar.javac"));
    assertFalse(condition.value("Foo.java"));
    assertFalse(condition.value("Foo.jav"));
    assertFalse(condition.value("Foo.min.js"));
    assertTrue(condition.value("Foo.js"));

    condition = FindInProjectUtil.createFileMaskCondition("!Foo.java");
    assertTrue(condition.value("Bar.java"));
    assertFalse(condition.value("Foo.java"));
    assertTrue(condition.value("Foo.js"));
    assertTrue(condition.value("makefile"));
  }

  public void testRegExpSOEWhenMatch() {
    String text = """
      package com.intellij.demo;

      """;
    for(int i = 0; i < 10; ++i) text += text;

    FindModel findModel = FindManagerTestUtils.configureFindModel(";((?:\\n|.)*export default )(?:DS.Model)(.extend((?:\\n|.)*assets: DS.attr(?:\\n|.)*});)");
    findModel.setRegularExpressions(true);

    FindResult findResult = myFindManager.findString(text, 0, findModel, null);
    assertFalse(findResult.isStringFound());
  }

  public void testRegExpSOEWhenMatch2() {
    String text = """
      package com.intellij.demo;

      """;
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
    assertFalse(findResult.isStringFound()); // SOE, no match
  }

  public void testFindRegexpThatMatchesWholeFile() throws Exception {
    FindModel findModel = FindManagerTestUtils.configureFindModel("[^~]+\\Z");
    findModel.setRegularExpressions(true);

    createFile("a.java", "some text");

    findModel.setRegularExpressions(true);
    List<UsageInfo> usages = findInProject(findModel);
    assertSize(1, usages);

    assertTrue(usages.get(0).isValid());
  }

  public void testFindMultilineWithLeadingSpaces() {
    FindModel findModel = FindManagerTestUtils.configureFindModel("System.currentTimeMillis();\n   System.currentTimeMillis();");
    findModel.setMultiline(true);
    String fileContent = """
      System.currentTimeMillis();
         System.currentTimeMillis();

              System.currentTimeMillis();
         System.currentTimeMillis();""";
    FindResult findResult = myFindManager.findString(fileContent, 0, findModel, null);
    assertTrue(findResult.isStringFound());
    findResult = myFindManager.findString(fileContent, findResult.getEndOffset(), findModel, null);
    assertTrue(findResult.isStringFound());
  }

  public void testRegExpMatchReplacement() throws FindManager.MalformedReplacementStringException {
    String text = "final override val\n" +
                  "      d1PrimitiveType by lazyThreadSafeIdempotentGenerator { D1PrimitiveType( typeManager = this ) }";
    String pattern = "final override val\n" +
                     "\\s+d(\\w+)PrimitiveType by lazyThreadSafeIdempotentGenerator \\{ D(\\w+)PrimitiveType\\( typeManager = this \\) \\}";
    String replacement = "";

    FindModel findModel = FindManagerTestUtils.configureFindModel(pattern);

    findModel.setRegularExpressions(true);
    findModel.setMultiline(true);

    FindResult findResult = myFindManager.findString(text, 0, findModel, null);
    assertTrue(findResult.isStringFound());
    assertEquals(replacement, myFindManager.getStringToReplace(findResult.substring(text), findModel, 0, text));
  }

  public void testRegExpSearchDoesCheckCancelled() throws InterruptedException, ExecutionException {
    FindModel findModel = FindManagerTestUtils.configureFindModel("(x+x+)+y");
    findModel.setRegularExpressions(true);

    String text = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";
    runAsyncTest(text, findModel);
    findModel.setSearchContext(FindModel.SearchContext.IN_COMMENTS);
    runAsyncTest("/*" + text + "*/", findModel);
  }

  public void testProperInsensitiveSearchForRegExp() throws Exception {
    createFile("a.java", "Цитрус цитрус");
    FindModel findModel = FindManagerTestUtils.configureFindModel("цитрус");
    findModel.setRegularExpressions(true);
    assertSize(2, findInProject(findModel));
  }

  public void testProperHandlingOfEmptyLinesWhenReplacingWithRegExp() {
    doTestRegexpReplace(
      "foo\n\n\n",
      "^",
      "// ",
      "// foo\n// \n// \n");
  }

  private void runAsyncTest(String text, FindModel findModel) throws InterruptedException, ExecutionException {
    final Ref<FindResult> result = new Ref<>();
    final CountDownLatch progressStarted = new CountDownLatch(1);
    final ProgressIndicatorBase progressIndicatorBase = new ProgressIndicatorBase();
    Future<?> thread = ApplicationManager.getApplication().executeOnPooledThread(() -> ProgressManager.getInstance().runProcess(() -> {
      try {
        progressStarted.countDown();
        result.set(myFindManager.findString(text, 0, findModel, new LightVirtualFile("foo.java")));
      }
      catch (ProcessCanceledException ex) {
        result.set(new FindResultImpl());
      }
    }, progressIndicatorBase));

    progressStarted.await();
    try {
      thread.get(100, TimeUnit.MILLISECONDS);
    }
    catch (TimeoutException ignored) {
    }
    progressIndicatorBase.cancel();
    try {
      thread.get(500, TimeUnit.MILLISECONDS);
    }
    catch (TimeoutException ignored) {

    }
    assertNotNull(result.get());
    assertFalse(result.get().isStringFound());
    thread.get();
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
    SearchScope scope = GlobalSearchScope.filesScope(myProject, Arrays.asList(directory));
    Module module = ModuleManager.getInstance(myProject).getModules()[0];
    String moduleName = module.getName();
    String dirName = directory.getPresentableUrl();

    FindModel model = new FindModel();
    checkContext(model, myProject, null, null, true, null, null, false);
    model.setDirectoryName("initialDirectoryName");
    model.setModuleName("initialModuleName");
    model.setProjectScope(false);
    model.setCustomScopeName("initialScopeName");

    checkContext(model, myProject, directory, module, false, dirName, moduleName, true);
    checkContext(model, myProject, directory, null, false, dirName, moduleName, false);//prev directory state
    checkContext(model, myProject, null, null, false, dirName, moduleName, false);//prev module and dir state
    model.setCustomScope(scope);
    model.setCustomScope(true);
    checkContext(model, myProject, null, null, false, dirName, moduleName, true);//prev module and dir state
  }

  private static void checkContext(FindModel model,
                                   Project project,
                                   VirtualFile directory,
                                   Module module,
                                   boolean shouldBeProjectScope,
                                   String expectedDirectoryName,
                                   String expectedModuleName,
                                   boolean shouldBeCustomScope) {
    DataContext dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, project)
      .add(CommonDataKeys.VIRTUAL_FILE, directory)
      .add(LangDataKeys.MODULE_CONTEXT, module)
      .build();
    FindInProjectUtil.setDirectoryName(model, dataContext);
    assertEquals(shouldBeProjectScope, model.isProjectScope());
    assertEquals(expectedDirectoryName, model.getDirectoryName());
    assertEquals(expectedModuleName, model.getModuleName());
    assertEquals(shouldBeCustomScope, model.isCustomScope());
  }

  public void testSearchInImlsIfRequestedExplicitly() throws Exception {
    createFile("a.iml", "foo");
    FindModel findModel = FindManagerTestUtils.configureFindModel("foo");
    assertEmpty(findInProject(findModel)); // skipped by default

    findModel.setFileFilter("*.iml");
    assertSize(1, findInProject(findModel));
  }

  public void testSearchInDotIdeaIfRequestedExplicitly() throws Exception {
    VirtualFile dotIdea = createChildDirectory(getTempDir().createVirtualDir(), Project.DIRECTORY_STORE_FOLDER);
    createFile(myModule, dotIdea, "a.iml", "foo");
    FindModel findModel = FindManagerTestUtils.configureFindModel("foo");
    assertEmpty(findInProject(findModel)); // skipped by default

    findModel.setDirectoryName(dotIdea.getPath());
    assertSize(1, findInProject(findModel));
  }

  public void testFindInPathDoesStopAtOneHundredUsagesWhenAskedTo() throws Exception {
    createFile(myModule, "A.txt", StringUtil.repeat("foo ", 200));

    FindModel findModel = new FindModel();
    findModel.setWholeWordsOnly(false);
    findModel.setFromCursor(false);
    findModel.setGlobal(true);
    findModel.setMultipleFiles(true);
    findModel.setProjectScope(true);

    findModel.setStringToFind("foo");

    List<UsageInfo> result = Collections.synchronizedList(new ArrayList<>());
    Processor<UsageInfo> collector = info -> {
      if (info.equals(ContainerUtil.getLastItem(result))) {
        throw new RuntimeException("duplicate elements found");
      }
      result.add(info);
      return result.size() < 100;
    };
    FindUsagesProcessPresentation presentation = new FindUsagesProcessPresentation(FindInProjectUtil.setupViewPresentation(true, findModel));
    FindInProjectUtil.findUsages(findModel, myProject, collector, presentation);

    assertEquals(100, result.size());
  }

  public void testFindInEditedAndNotSavedFile() throws Exception {
    PsiFile file = createFile(myModule, "A.txt", "foo");
    Document document = FileDocumentManager.getInstance().getDocument(file.getVirtualFile());
    WriteCommandAction.runWriteCommandAction(myProject, () -> {
      document.insertString(document.getTextLength(), "bar");
    });
    PsiDocumentManager.getInstance(myProject).commitDocument(document);

    FindModel findModel = new FindModel();
    findModel.setWholeWordsOnly(false);
    findModel.setFromCursor(false);
    findModel.setGlobal(true);
    findModel.setMultipleFiles(true);
    findModel.setProjectScope(true);

    findModel.setStringToFind("bar");
    List<UsageInfo> usages = findInProject(findModel);

    assertEquals(1, usages.size());
  }
}
