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
package com.intellij.java.index;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

public class FilenameIndexTest extends JavaCodeInsightFixtureTestCase {
  public void testCaseInsensitiveFilesByName() {
    final VirtualFile vFile1 = myFixture.addFileToProject("dir1/foo.test", "Foo").getVirtualFile();
    final VirtualFile vFile2 = myFixture.addFileToProject("dir2/FOO.TEST", "Foo").getVirtualFile();

    GlobalSearchScope scope = GlobalSearchScope.projectScope(getProject());
    assertSameElements(FilenameIndex.getVirtualFilesByName(getProject(), "foo.test", true, scope), vFile1);
    assertSameElements(FilenameIndex.getVirtualFilesByName(getProject(), "FOO.TEST", true, scope), vFile2);

    assertSameElements(FilenameIndex.getVirtualFilesByName(getProject(), "foo.test", false, scope), vFile1, vFile2);
    assertSameElements(FilenameIndex.getVirtualFilesByName(getProject(), "FOO.TEST", false, scope), vFile1, vFile2);
  }
}
