/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInspection;

import com.intellij.analysis.AnalysisScope;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.ModuleTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.VfsTestUtil;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;

/**
 * @author nik
 */
public class AnalysisScopeTest extends ModuleTestCase {
  public void testGeneratedSourceRoot() throws Exception {
    VirtualFile genRoot = getVirtualFile(createTempDir("genSrcRoot"));
    VirtualFile srcRoot = getVirtualFile(createTempDir("srcRoot"));
    JavaSourceRootProperties properties = JpsJavaExtensionService.getInstance().createSourceRootProperties("", true);
    PsiTestUtil.addSourceRoot(myModule, genRoot, JavaSourceRootType.SOURCE, properties);
    PsiTestUtil.addSourceRoot(myModule, srcRoot);
    VirtualFile genClass = VfsTestUtil.createFile(genRoot, "Gen.java", "class Gen{}");
    VirtualFile srcClass = VfsTestUtil.createFile(srcRoot, "Src.java", "class Src{}");
    AnalysisScope scope = new AnalysisScope(myModule);
    assertTrue(scope.contains(srcClass));
    assertFalse(scope.contains(genClass));
  }
}
