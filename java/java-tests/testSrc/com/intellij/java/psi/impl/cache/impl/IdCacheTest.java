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
package com.intellij.java.psi.impl.cache.impl;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.ide.todo.TodoConfiguration;
import com.intellij.ide.todo.TodoIndexPatternProvider;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.cache.CacheManager;
import com.intellij.psi.impl.cache.TodoCacheManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.TodoAttributesUtil;
import com.intellij.psi.search.TodoPattern;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.ArrayUtil;

import java.io.File;
import java.util.Arrays;

public class IdCacheTest extends CodeInsightTestCase{

  private VirtualFile myRootDir;
  private File myCacheFile;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    String root = JavaTestUtil.getJavaTestDataPath()+ "/psi/impl/cache/";

    PsiTestUtil.removeAllRoots(myModule, IdeaTestUtil.getMockJdk17());
    myRootDir = PsiTestUtil.createTestProjectStructure(myProject, myModule, root, myFilesToDelete);

    myCacheFile = FileUtil.createTempFile("cache", "");
    myCacheFile.delete();
    myFilesToDelete.add(myCacheFile);
  }

  public void testBuildCache() {
    checkCache(CacheManager.SERVICE.getInstance(myProject), TodoCacheManager.SERVICE.getInstance(myProject));
  }

  public void testLoadCacheNoTodo() {

    final CacheManager cache = CacheManager.SERVICE.getInstance(myProject);

    checkResult(new String[]{"1.java", "2.java"}, convert(cache.getFilesWithWord("b", UsageSearchContext.ANY, GlobalSearchScope.projectScope(myProject), false)));
  }

  public void testUpdateCache1() throws Exception {
    createChildData(myRootDir, "4.java");
    Thread.sleep(1000);
    checkCache(CacheManager.SERVICE.getInstance(myProject), TodoCacheManager.SERVICE.getInstance(myProject));
  }

  public void testUpdateCache2() {
    VirtualFile child = myRootDir.findChild("1.java");
    setFileText(child, "xxx");

    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    FileDocumentManager.getInstance().saveAllDocuments();

    final CacheManager cache = CacheManager.SERVICE.getInstance(myProject);
    final TodoCacheManager todocache = TodoCacheManager.SERVICE.getInstance(myProject);
    final GlobalSearchScope scope = GlobalSearchScope.projectScope(myProject);
    checkResult(new String[] {"1.java"}, convert(cache.getFilesWithWord("xxx", UsageSearchContext.ANY, scope, false)));
    checkResult(new String[]{}, convert(cache.getFilesWithWord("a", UsageSearchContext.ANY, scope, false)));
    checkResult(new String[]{"2.java"}, convert(cache.getFilesWithWord("b", UsageSearchContext.ANY,scope, false)));
    checkResult(new String[]{"2.java", "3.java"}, convert(cache.getFilesWithWord("c", UsageSearchContext.ANY,scope, false)));
    checkResult(new String[]{"2.java", "3.java"}, convert(cache.getFilesWithWord("d", UsageSearchContext.ANY,scope, false)));
    checkResult(new String[]{"3.java"}, convert(cache.getFilesWithWord("e", UsageSearchContext.ANY,scope, false)));

    checkResult(new String[]{"3.java"}, convert(todocache.getFilesWithTodoItems()));
    assertEquals(0, todocache.getTodoCount(myRootDir.findChild("1.java"), TodoIndexPatternProvider.getInstance()));
    assertEquals(0, todocache.getTodoCount(myRootDir.findChild("2.java"), TodoIndexPatternProvider.getInstance()));
    assertEquals(2, todocache.getTodoCount(myRootDir.findChild("3.java"), TodoIndexPatternProvider.getInstance()));
  }

  public void testUpdateCache3() {
    VirtualFile child = myRootDir.findChild("1.java");
    delete(child);

    final CacheManager cache2 = CacheManager.SERVICE.getInstance(myProject);
    final TodoCacheManager todocache2 = TodoCacheManager.SERVICE.getInstance(myProject);
    final GlobalSearchScope scope = GlobalSearchScope.projectScope(myProject);
    checkResult(ArrayUtil.EMPTY_STRING_ARRAY, convert(cache2.getFilesWithWord("xxx", UsageSearchContext.ANY, scope, false)));
    checkResult(ArrayUtil.EMPTY_STRING_ARRAY, convert(cache2.getFilesWithWord("a", UsageSearchContext.ANY, scope, false)));
    checkResult(new String[]{"2.java"}, convert(cache2.getFilesWithWord("b", UsageSearchContext.ANY, scope, false)));
    checkResult(new String[]{"2.java", "3.java"}, convert(cache2.getFilesWithWord("c", UsageSearchContext.ANY, scope, false)));
    checkResult(new String[]{"2.java", "3.java"}, convert(cache2.getFilesWithWord("d", UsageSearchContext.ANY, scope, false)));
    checkResult(new String[]{"3.java"}, convert(cache2.getFilesWithWord("e", UsageSearchContext.ANY, scope, false)));

    checkResult(new String[]{"3.java"}, convert(todocache2.getFilesWithTodoItems()));
    assertEquals(0, todocache2.getTodoCount(myRootDir.findChild("2.java"), TodoIndexPatternProvider.getInstance()));
    assertEquals(2, todocache2.getTodoCount(myRootDir.findChild("3.java"), TodoIndexPatternProvider.getInstance()));
  }

  public void testUpdateCacheNoTodo() {
    createChildData(myRootDir, "4.java");
    final GlobalSearchScope scope = GlobalSearchScope.projectScope(myProject);
    final CacheManager cache = CacheManager.SERVICE.getInstance(myProject);
    checkResult(new String[]{"1.java", "2.java"}, convert(cache.getFilesWithWord("b", UsageSearchContext.ANY, scope, false)));
  }

  public void testUpdateOnTodoChange() {
    TodoPattern pattern = new TodoPattern("newtodo", TodoAttributesUtil.createDefault(), true);
    TodoPattern[] oldPatterns = TodoConfiguration.getInstance().getTodoPatterns();
    TodoConfiguration.getInstance().setTodoPatterns(new TodoPattern[]{pattern});

    try{
      final TodoCacheManager todocache = TodoCacheManager.SERVICE.getInstance(myProject);
      checkResult(new String[]{"2.java"}, convert(todocache.getFilesWithTodoItems()));
      assertEquals(0, todocache.getTodoCount(myRootDir.findChild("1.java"), TodoIndexPatternProvider.getInstance()));
      assertEquals(1, todocache.getTodoCount(myRootDir.findChild("2.java"), TodoIndexPatternProvider.getInstance()));
      assertEquals(0, todocache.getTodoCount(myRootDir.findChild("3.java"), TodoIndexPatternProvider.getInstance()));
    }
    finally{
      TodoConfiguration.getInstance().setTodoPatterns(oldPatterns);
    }
  }

  public void testFileModification() {
    final CacheManager cache = CacheManager.SERVICE.getInstance(myProject);
    final TodoCacheManager todocache = TodoCacheManager.SERVICE.getInstance(myProject);
    checkCache(cache, todocache);

    VirtualFile child = myRootDir.findChild("1.java");

    checkCache(cache, todocache);

    setFileText(child, "xxx");
    setFileText(child, "xxx");
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    final GlobalSearchScope scope = GlobalSearchScope.projectScope(myProject);
    checkResult(new String[] {"1.java"}, convert(cache.getFilesWithWord("xxx", UsageSearchContext.ANY, scope, false)));
    checkResult(new String[]{}, convert(cache.getFilesWithWord("a", UsageSearchContext.ANY, scope, false)));
    checkResult(new String[]{"2.java"}, convert(cache.getFilesWithWord("b", UsageSearchContext.ANY, scope, false)));
    checkResult(new String[]{"2.java", "3.java"}, convert(cache.getFilesWithWord("c", UsageSearchContext.ANY, scope, false)));
    checkResult(new String[]{"2.java", "3.java"}, convert(cache.getFilesWithWord("d", UsageSearchContext.ANY, scope, false)));
    checkResult(new String[]{"3.java"}, convert(cache.getFilesWithWord("e", UsageSearchContext.ANY, scope, false)));

    checkResult(new String[]{"3.java"}, convert(todocache.getFilesWithTodoItems()));
    assertEquals(0, todocache.getTodoCount(myRootDir.findChild("1.java"), TodoIndexPatternProvider.getInstance()));
    assertEquals(0, todocache.getTodoCount(myRootDir.findChild("2.java"), TodoIndexPatternProvider.getInstance()));
    assertEquals(2, todocache.getTodoCount(myRootDir.findChild("3.java"), TodoIndexPatternProvider.getInstance()));
  }

  public void testFileDeletion() {
    final CacheManager cache = CacheManager.SERVICE.getInstance(myProject);
    final TodoCacheManager todocache = TodoCacheManager.SERVICE.getInstance(myProject);
    checkCache(cache, todocache);

    VirtualFile child = myRootDir.findChild("1.java");
    delete(child);

    final GlobalSearchScope scope = GlobalSearchScope.projectScope(myProject);
    checkResult(new String[]{}, convert(cache.getFilesWithWord("xxx", UsageSearchContext.ANY, scope, false)));
    checkResult(new String[]{}, convert(cache.getFilesWithWord("a", UsageSearchContext.ANY, scope, false)));
    checkResult(new String[]{"2.java"}, convert(cache.getFilesWithWord("b", UsageSearchContext.ANY, scope, false)));
    checkResult(new String[]{"2.java", "3.java"}, convert(cache.getFilesWithWord("c", UsageSearchContext.ANY, scope, false)));
    checkResult(new String[]{"2.java", "3.java"}, convert(cache.getFilesWithWord("d", UsageSearchContext.ANY, scope, false)));
    checkResult(new String[]{"3.java"}, convert(cache.getFilesWithWord("e", UsageSearchContext.ANY, scope, false)));

    checkResult(new String[]{"3.java"}, convert(todocache.getFilesWithTodoItems()));
    assertEquals(0, todocache.getTodoCount(myRootDir.findChild("2.java"), TodoIndexPatternProvider.getInstance()));
    assertEquals(2, todocache.getTodoCount(myRootDir.findChild("3.java"), TodoIndexPatternProvider.getInstance()));
  }

  public void testFileCreation() {
    final CacheManager cache = CacheManager.SERVICE.getInstance(myProject);
    final TodoCacheManager todocache = TodoCacheManager.SERVICE.getInstance(myProject);
    checkCache(cache, todocache);

    VirtualFile child = createChildData(myRootDir, "4.java");
    setFileText(child, "xxx //todo");
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    final GlobalSearchScope scope = GlobalSearchScope.projectScope(myProject);
    checkResult(new String[]{"4.java"}, convert(cache.getFilesWithWord("xxx", UsageSearchContext.ANY, scope, false)));
    checkResult(new String[]{"1.java"}, convert(cache.getFilesWithWord("a", UsageSearchContext.ANY, scope, false)));
    checkResult(new String[]{"1.java", "2.java"}, convert(cache.getFilesWithWord("b", UsageSearchContext.ANY, scope, false)));
    checkResult(new String[]{"1.java", "2.java", "3.java"}, convert(cache.getFilesWithWord("c", UsageSearchContext.ANY, scope, false)));
    checkResult(new String[]{"2.java", "3.java"}, convert(cache.getFilesWithWord("d", UsageSearchContext.ANY, scope, false)));
    checkResult(new String[]{"3.java"}, convert(cache.getFilesWithWord("e", UsageSearchContext.ANY, scope, false)));

    checkResult(new String[]{"1.java", "3.java", "4.java"}, convert(todocache.getFilesWithTodoItems()));
    assertEquals(1, todocache.getTodoCount(myRootDir.findChild("1.java"), TodoIndexPatternProvider.getInstance()));
    assertEquals(0, todocache.getTodoCount(myRootDir.findChild("2.java"), TodoIndexPatternProvider.getInstance()));
    assertEquals(2, todocache.getTodoCount(myRootDir.findChild("3.java"), TodoIndexPatternProvider.getInstance()));
    assertEquals(1, todocache.getTodoCount(myRootDir.findChild("4.java"), TodoIndexPatternProvider.getInstance()));
  }

  public void testCrash() {
    final CacheManager cache = CacheManager.SERVICE.getInstance(myProject);
    cache.getFilesWithWord("xxx", UsageSearchContext.ANY, GlobalSearchScope.projectScope(myProject), false);
    System.gc();
  }

  private void checkCache(CacheManager cache, TodoCacheManager todocache) {
    final GlobalSearchScope scope = GlobalSearchScope.projectScope(myProject);
    checkResult(ArrayUtil.EMPTY_STRING_ARRAY, convert(cache.getFilesWithWord("xxx", UsageSearchContext.ANY, scope, false)));
    checkResult(new String[]{"1.java"}, convert(cache.getFilesWithWord("a", UsageSearchContext.ANY, scope, false)));
    checkResult(new String[]{"1.java", "2.java"}, convert(cache.getFilesWithWord("b", UsageSearchContext.ANY, scope, false)));
    checkResult(new String[]{"1.java", "2.java", "3.java"}, convert(cache.getFilesWithWord("c", UsageSearchContext.ANY, scope, false)));
    checkResult(new String[]{"2.java", "3.java"}, convert(cache.getFilesWithWord("d", UsageSearchContext.ANY, scope, false)));
    checkResult(new String[]{"3.java"}, convert(cache.getFilesWithWord("e", UsageSearchContext.ANY, scope, false)));

    checkResult(new String[]{"1.java", "3.java"}, convert(todocache.getFilesWithTodoItems()));
    assertEquals(1, todocache.getTodoCount(myRootDir.findChild("1.java"), TodoIndexPatternProvider.getInstance()));
    assertEquals(0, todocache.getTodoCount(myRootDir.findChild("2.java"), TodoIndexPatternProvider.getInstance()));
    assertEquals(2, todocache.getTodoCount(myRootDir.findChild("3.java"), TodoIndexPatternProvider.getInstance()));
  }

  private static VirtualFile[] convert(PsiFile[] psiFiles) {
    final VirtualFile[] files = new VirtualFile[psiFiles.length];
    for (int idx = 0; idx < psiFiles.length; idx++) {
      files[idx] = psiFiles[idx].getVirtualFile();
    }
    return files;
  }
  
  private static void checkResult(String[] expected, VirtualFile[] result){
    assertEquals(expected.length, result.length);
    
    Arrays.sort(expected);
    Arrays.sort(result, (o1, o2) -> {
      VirtualFile file1 = (VirtualFile)o1;
      VirtualFile file2 = (VirtualFile)o2;
      return file1.getName().compareTo(file2.getName());
    });

    for(int i = 0; i < expected.length; i++){
      String name = expected[i];
      assertEquals(name, result[i].getName());
    }
  }
}
