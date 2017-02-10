/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.vfs.changes;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.changes.PsiChangeTracker;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFilter;
import com.intellij.testFramework.IdeaTestCase;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
public class PsiChangeTrackerTest extends IdeaTestCase {
  private PsiFile myOriginalFile;
  private PsiFile myChangedFile;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final VirtualFile testRoot = getTestRoot();
    final VirtualFile child = testRoot.findChild("PsiChangesTest.java");
    assert child != null : "Can't find PsiChangesTest.java";
    final PsiFile original = getPsiManager().findFile(child);
    assert original != null : "Can't create PsiFile from VirtualFile " + child.getName();
    myOriginalFile = original;

    final VirtualFile changedFile = testRoot.findChild("PsiChangesTest_changed.java");
    assert changedFile != null : "Can't find PsiChangesTest_changed.java";

    myChangedFile = PsiFileFactory.getInstance(myProject)
      .createFileFromText(LoadTextUtil.loadText(changedFile), myOriginalFile);
    assert myChangedFile != null : "Can't create PsiFile from " + changedFile.getPath();
  }

  public void testMethods() throws Exception {
    doTest(new PsiFilter<>(PsiMethod.class));
  }

  public void testFields() throws Exception {
    doTest(new PsiFilter<>(PsiField.class));
  }

  private <T extends PsiElement> void doTest(PsiFilter<T> filter) throws IOException {
    final Map<T,FileStatus> map = PsiChangeTracker.getElementsChanged(myChangedFile, myOriginalFile, filter);
    final Map<String, String> changes = convert(map);
    final Map<String, String> expected = getExpectedResults();
    assert expected.equals(changes) : "Changes are not equal \n Expected:\n" + expected + "\nFound:\n" + changes;
  }

  private static <T extends PsiElement> Map<String, String> convert(Map<T, FileStatus> map) {
    final Map<String, String> result = new HashMap<>();
    for (T key : map.keySet()) {
      final String newKey;
      if (key instanceof PsiMember) {
        newKey = ((PsiMember)key).getName();
      } else {
        newKey = key.getText();
      }
      result.put(newKey, map.get(key).getText());
    }
    return result;
  }

  private Map<String, String> getExpectedResults() throws IOException {
    final String resultFileName = getTestName(true) + ".txt";
    final VirtualFile resultsDir = getTestRoot().findChild("results");
    assert resultsDir != null : "Can't find results dir";
    final VirtualFile result = resultsDir.findChild(resultFileName);
    assert result != null : "File not found " + resultsDir.getPath() +  "/" + resultFileName;
    String res = LoadTextUtil.loadText(result).toString();
    final Map<String, String> map = new HashMap<>();
    for (String s : res.replace("\r\n", "\n").split("\n")) {
      if (!StringUtil.isEmptyOrSpaces(s)) {
        final String[] strings = s.split(":");
        map.put(strings[0].trim(), strings[1].trim());
      }
    }
    return map;
  }

  protected static VirtualFile getTestRoot() {
    final File root = new File(PathManagerEx.getTestDataPath());
    final File testRoot = new File(new File(root, "vfs"), "changes");
    final VirtualFile ioFile = LocalFileSystem.getInstance().findFileByIoFile(testRoot);
    assert ioFile != null : "Can't find directory vfs/changes";
    return ioFile;
  }
}
