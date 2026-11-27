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
package com.intellij.java.openapi.vfs.changes;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.changes.PsiChangeTracker;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiFilter;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.heavyJavaCodeInsightFixture;

/**
 * @author Konstantin Bulenkov
 */
@TestApplication
public class PsiChangeTrackerTest {
  private final TestFixture<HeavyJavaCodeInsightFixture> heavyFixture = heavyJavaCodeInsightFixture(JavaTestUtil.getJavaTestDataPath());

  private PsiFile myOriginalFile;
  private PsiFile myChangedFile;

  @BeforeEach
  void setUp() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    final VirtualFile testRoot = getTestRoot();
    final VirtualFile child = testRoot.findChild("PsiChangesTest.java");
    assert child != null : "Can't find PsiChangesTest.java";
    final PsiFile original = ReadAction.computeBlocking(() -> fixture.getPsiManager().findFile(child));
    assert original != null : "Can't create PsiFile from VirtualFile " + child.getName();
    myOriginalFile = original;

    final VirtualFile changedFile = testRoot.findChild("PsiChangesTest_changed.java");
    assert changedFile != null : "Can't find PsiChangesTest_changed.java";

    myChangedFile = ReadAction.computeBlocking(() -> PsiFileFactory.getInstance(fixture.getProject())
      .createFileFromText(LoadTextUtil.loadText(changedFile), myOriginalFile));
    assert myChangedFile != null : "Can't create PsiFile from " + changedFile.getPath();
  }

  @Test
  public void testMethods() {
    doTest(new PsiFilter<>(PsiMethod.class));
  }

  @Test
  public void testFields() {
    doTest(new PsiFilter<>(PsiField.class));
  }

  private <T extends PsiElement> void doTest(PsiFilter<T> filter) {
    final Map<String, String> changes = ReadAction.computeBlocking(() -> {
      final Map<T,FileStatus> map = PsiChangeTracker.getElementsChanged(myChangedFile, myOriginalFile, filter);
      return convert(map);
    });
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

  private Map<String, String> getExpectedResults() {
    final String resultFileName = heavyFixture.get().getTestName(true) + ".txt";
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
    final File root = new File(JavaTestUtil.getJavaTestDataPath());
    final File testRoot = new File(new File(root, "vfs"), "changes");
    final VirtualFile ioFile = StandardFileSystems.local().findFileByPath(testRoot.getAbsolutePath());
    assert ioFile != null : "Can't find directory vfs/changes";
    return ioFile;
  }
}
