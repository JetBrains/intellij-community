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
package com.intellij.find.impl;

import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestCase;

import java.io.IOException;

public class FindResultTest extends PlatformTestCase {
  public void testFindResultHasCorrectCompare() throws IOException {
    VirtualFile file = createTempFile("txt", null, "xxxx", CharsetToolkit.UTF8_CHARSET);
    PsiFile psiFile = getPsiManager().findFile(file);
    FindResultUsageInfo info1 = new FindResultUsageInfo(FindManager.getInstance(myProject), psiFile, 1, new FindModel(), new FindResultImpl(1, 2));
    FindResultUsageInfo info2 = new FindResultUsageInfo(FindManager.getInstance(myProject), psiFile, 2, new FindModel(), new FindResultImpl(2, 3));
    assertTrue("result: "+info1.compareToByStartOffset(info2),info1.compareToByStartOffset(info2) < 0);
  }
}
