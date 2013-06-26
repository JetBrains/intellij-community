/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.find;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.find.impl.FindInProjectUtil;
import com.intellij.find.replaceInProject.ReplaceInProjectManager;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.FindUsagesProcessPresentation;
import com.intellij.usages.Usage;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.WaitFor;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/*
 * @author: MYakovlev
 * Date: Oct 17, 2002
 * Time: 7:53:31 PM
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public class FindManagerTest extends DaemonAnalyzerTestCase {
  protected VirtualFile[] mySourceDirs;

  public void testFindString() throws Exception{
    FindManager findManager = FindManager.getInstance(myProject);

    FindModel findModel = new FindModel();
    findModel.setStringToFind("done");
    findModel.setWholeWordsOnly(false);
    findModel.setFromCursor(false);
    findModel.setGlobal(true);
    findModel.setMultipleFiles(false);
    findModel.setProjectScope(true);

    String text = "public static class MyClass{\n/*done*/\npublic static void main(){}}";
    FindResult findResult = findManager.findString(text, 0, findModel);
    assertTrue(findResult.isStringFound());

    findModel = new FindModel();
    findModel.setStringToFind("done");
    findModel.setWholeWordsOnly(false);
    findModel.setFromCursor(true);
    findModel.setGlobal(true);
    findModel.setMultipleFiles(false);
    findModel.setProjectScope(true);

    findResult = findManager.findString(text, 40, findModel);
    assertFalse(findResult.isStringFound());

    findModel = new FindModel();
    findModel.setStringToFind("done");
    findModel.setWholeWordsOnly(false);
    findModel.setFromCursor(true);
    findModel.setGlobal(true);
    findModel.setMultipleFiles(false);
    findModel.setProjectScope(true);
    findModel.setForward(false);

    findResult = findManager.findString(text, 40, findModel);
    assertTrue(findResult.isStringFound());

    findModel = new FindModel();
    findModel.setStringToFind("done");
    findModel.setWholeWordsOnly(true);
    findModel.setFromCursor(false);
    findModel.setGlobal(true);
    findModel.setMultipleFiles(false);
    findModel.setProjectScope(true);

    findResult = findManager.findString(text, 0, findModel);
    assertTrue(findResult.isStringFound());

    findModel = new FindModel();
    findModel.setStringToFind("don");
    findModel.setWholeWordsOnly(true);
    findModel.setFromCursor(false);
    findModel.setGlobal(true);
    findModel.setMultipleFiles(false);
    findModel.setProjectScope(true);

    final FindResult[] findResultArr = new FindResult[1];
    findInNewThread(findModel, findManager, text, 0, findResultArr);
    new WaitFor(30 *1000){
      @Override
      protected boolean condition() {
        return findResultArr[0] != null;
      }
    }.assertCompleted();

    assertFalse(findResultArr[0].isStringFound());
  }

  private static Thread findInNewThread(final FindModel model,
                                  final FindManager findManager,
                                  final CharSequence text,
                                  final int offset,
                                  final FindResult[] op_result){
    op_result[0] = null;
    Thread findThread = new Thread(){
      @Override
      public void run(){
        op_result[0] = findManager.findString(text, offset, model);
      }
    };
    findThread.start();
    return findThread;
  }

  public void testFindUsages() throws Exception{
    initProject("findManager", "src", "src1");
    final String projectDir = (PathManagerEx.getTestDataPath() + "/find/findManager").replace('/', File.separatorChar);

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

    //findModel = new FindModel();
    findModel.setFromCursor(false);
    findModel.setGlobal(true);
    findModel.setMultipleFiles(true);
    findModel.setProjectScope(false);
    findModel.setDirectoryName(projectDir + File.separatorChar + "src1");
    findModel.setWithSubdirectories(true);
    checkFindUsages(6, findModel);

    findModel.setWholeWordsOnly(true);
    checkFindUsages(5, findModel);
//    findModel.setForward(false);
//    findModel.setCaseSensitive();


  }

  private void checkFindUsages(int expectedResults, FindModel findModel) throws Exception{
    Collection<UsageInfo> usages = findUsages(findModel);
    assertEquals(expectedResults, usages.size());
  }

  private List<UsageInfo> findUsages(final FindModel findModel) {
    PsiDirectory psiDirectory = FindInProjectUtil.getPsiDirectory(findModel, myProject);
    List<UsageInfo> result = new ArrayList<UsageInfo>();
    final CommonProcessors.CollectProcessor<UsageInfo> collector = new CommonProcessors.CollectProcessor<UsageInfo>(result);
    FindInProjectUtil.findUsages(findModel, psiDirectory, myProject, true, collector, new FindUsagesProcessPresentation());
    return result;
  }

  public void testFindWholeWordsInProperties() throws Exception {
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
    PsiElement element = usages.get(0).getElement();
    //assertTrue(element instanceof PsiJavaFile);
    PsiElement refElement = element.findElementAt(usages.get(0).getRangeInElement().getStartOffset()).getParent();
    assertTrue(refElement instanceof PsiLiteralExpression);
    assertEquals("xx.yy", ((PsiLiteralExpression)refElement).getValue());

    VirtualFile file = mySourceDirs[0].findFileByRelativePath("x/dd.properties");
    PropertiesFile propertiesFile = (PropertiesFile)PsiManager.getInstance(myProject).findFile(file);
    element = usages.get(1).getElement();
    //assertTrue(element instanceof PropertiesFile);
    refElement = element.findElementAt(usages.get(1).getRangeInElement().getStartOffset()).getParent();
    assertTrue(refElement instanceof IProperty);
    assertSame(propertiesFile.findPropertyByKey("xx.yy"), refElement);
  }

  public void testFindInClassHierarchy() throws Exception {
    initProject("findInClassHierarchy", "src");

    FindModel findModel = new FindModel();
    findModel.setStringToFind("instanceof");
    findModel.setWholeWordsOnly(true);
    findModel.setFromCursor(false);
    findModel.setGlobal(true);
    findModel.setMultipleFiles(true);
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
    final PsiClass baseClass = facade.findClass("A", GlobalSearchScope.allScope(getProject()));
    final PsiClass implClass = facade.findClass("AImpl", GlobalSearchScope.allScope(getProject()));
    findModel.setCustomScope(new LocalSearchScope(new PsiElement[]{baseClass, implClass}));

    List<UsageInfo> usages = findUsages(findModel);
    assertEquals(2, usages.size());
  }

  public void testReplaceRegexp() throws Throwable {
    FindManager findManager = FindManager.getInstance(myProject);

    FindModel findModel = new FindModel();
    findModel.setStringToFind("bug(?=here)");
    findModel.setStringToReplace("x$0y");
    findModel.setWholeWordsOnly(false);
    findModel.setFromCursor(false);
    findModel.setGlobal(true);
    findModel.setMultipleFiles(false);
    findModel.setProjectScope(true);
    findModel.setRegularExpressions(true);
    findModel.setPromptOnReplace(false);

    findManager.setFindNextModel(null);
    findManager.getFindInFileModel().copyFrom(findModel);

    String text = "bughere\n" + "bughere";
    configureByText(FileTypes.PLAIN_TEXT, text);
    boolean succ = FindUtil.replace(getProject(), getEditor(), 0, findModel);
    assertTrue(succ);

    assertEquals("xbugyhere\n" + "xbugyhere", getEditor().getDocument().getText());
  }
  public void testReplaceRegexp1() throws Throwable {
    FindManager findManager = FindManager.getInstance(myProject);

    FindModel findModel = new FindModel();
    findModel.setStringToFind("bug(?=here)");
    findModel.setStringToReplace("$0");
    findModel.setWholeWordsOnly(false);
    findModel.setFromCursor(false);
    findModel.setGlobal(true);
    findModel.setMultipleFiles(false);
    findModel.setProjectScope(true);
    findModel.setRegularExpressions(true);
    findModel.setPromptOnReplace(false);

    findManager.setFindNextModel(null);
    findManager.getFindInFileModel().copyFrom(findModel);

    String text = "bughere\n" + "bughere";
    configureByText(FileTypes.PLAIN_TEXT, text);
    boolean succ = FindUtil.replace(getProject(), getEditor(), 0, findModel);
    assertTrue(succ);

    assertEquals(text, getEditor().getDocument().getText());
  }

  public void testReplaceRegexpWithNewLine() throws Throwable {
    FindManager findManager = FindManager.getInstance(myProject);

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

    findManager.setFindNextModel(null);
    findManager.getFindInFileModel().copyFrom(findModel);

    String text = "xxx";
    configureByText(FileTypes.PLAIN_TEXT, text);
    boolean succ = FindUtil.replace(getProject(), getEditor(), 0, findModel);
    assertTrue(succ);

    assertEquals(text+"\n", getEditor().getDocument().getText());
  }

  private  void initProject(String folderName, final String... sourceDirs) throws Exception{
    final String testDir = JavaTestUtil.getJavaTestDataPath() + "/find/" + folderName;
    ApplicationManager.getApplication().runWriteAction(new Runnable(){
      @Override
      public void run(){
        try{
          mySourceDirs = new VirtualFile[sourceDirs.length];
          for (int i = 0; i < sourceDirs.length; i++){
            String sourceDir = sourceDirs[i];
            mySourceDirs[i] = LocalFileSystem.getInstance().refreshAndFindFileByPath(new File(testDir + File.separatorChar + sourceDir).getCanonicalPath().replace(File.separatorChar, '/'));
          }
          VirtualFile projectDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(new File(testDir).getCanonicalPath().replace(File.separatorChar, '/'));

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
      }
    });
  }

  public void testReplaceAll() throws Throwable {
    FindManager findManager = FindManager.getInstance(myProject);

    FindModel findModel = new FindModel();
    String toFind = "xxx";
    String toReplace = "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX";
    findModel.setStringToFind(toFind);
    findModel.setStringToReplace(toReplace);
    findModel.setWholeWordsOnly(true);
    findModel.setFromCursor(false);
    findModel.setGlobal(true);
    findModel.setMultipleFiles(false);
    findModel.setProjectScope(true);
    findModel.setRegularExpressions(false);
    findModel.setPromptOnReplace(false);

    findManager.setFindNextModel(null);
    findManager.getFindInFileModel().copyFrom(findModel);

    String text = StringUtil.repeat(toFind + "\n",6);
    configureByText(FileTypes.PLAIN_TEXT, text);

    List<Usage> usages = FindUtil.findAll(getProject(), myEditor, findModel);
    for (Usage usage : usages) {
      ReplaceInProjectManager.getInstance(getProject()).replaceUsage(usage, findModel, Collections.<Usage>emptySet(), false);
    }
    String newText = StringUtil.repeat(toReplace + "\n",6);
    assertEquals(newText, getEditor().getDocument().getText());
  }

  public void testFindInFileUnderLibraryUnderProject() throws Exception {
    initProject("libUnderProject", "src");
    PsiTestUtil.addLibrary(myModule, "lib", JavaTestUtil.getJavaTestDataPath() + "/find/libUnderProject/lib", new String[]{""}, ArrayUtil.EMPTY_STRING_ARRAY);

    FindModel findModel = new FindModel();
    findModel.setStringToFind("TargetWord");
    findModel.setFromCursor(false);
    findModel.setGlobal(true);
    findModel.setMultipleFiles(true);

    findModel.setWholeWordsOnly(false);
    assertSize(2, findUsages(findModel));

    findModel.setWholeWordsOnly(true);
    assertSize(2, findUsages(findModel));
  }

  public void testLocalScopeSearchPerformance() throws Throwable {
    final int fileCount = 3000;
    final int lineCount = 500;
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
      final FindModel findModel = new FindModel();
      findModel.setStringToFind("TargetWord");
      findModel.setWholeWordsOnly(true);
      findModel.setFromCursor(false);
      findModel.setGlobal(true);
      findModel.setMultipleFiles(true);

      ThrowableRunnable test = new ThrowableRunnable() {
        @Override
        public void run() throws Throwable {
          assertSize(lineCount, findUsages(findModel));
        }
      };

      findModel.setCustomScope(GlobalSearchScope.fileScope(psiFile));
      PlatformTestUtil.startPerformanceTest("slow", 400, test).attempts(2).cpuBound().usesAllCPUCores().assertTiming();

      findModel.setCustomScope(new LocalSearchScope(psiFile));
      PlatformTestUtil.startPerformanceTest("slow", 400, test).attempts(2).cpuBound().usesAllCPUCores().assertTiming();
    }
    finally {
      fixture.tearDown();
    }
  }


}
