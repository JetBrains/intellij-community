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
package com.intellij.psi;

import com.intellij.core.CoreJavaFileManager;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.testFramework.PsiTestUtil;


public class CoreJavaFileManagerTest extends PsiTestCase {

  public void testNotNullInnerClass() throws Exception {
    VirtualFile root = PsiTestUtil.createTestProjectStructure(myProject, myModule, myFilesToDelete);
    VirtualFile pkg = root.createChildDirectory(this, "foo");
    PsiDirectory dir = myPsiManager.findDirectory(pkg);
    assertNotNull(dir);
    String text = "package foo;\n\n" +
                  "public class Nested {\n" +
                  "public class InnerGeneral {}\n" +
                  "public class Inner$ {}\n" +
                  "\n" +
                  "public Inner$ inner() {\n" +
                  "   return  new Inner$();\n" +
                  "}\n" +
                  "\n" +
                  "}";
    PsiElement created = dir.add(PsiFileFactory.getInstance(getProject()).createFileFromText("Nested.java", JavaFileType.INSTANCE, text));

    GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());
    CoreJavaFileManager manager = new CoreJavaFileManager(myPsiManager);
    manager.addToClasspath(root);

    PsiClass clazz = manager.findClass("foo.Nested", scope);
    assertNotNull(clazz);

    PsiClass clazzInnerGeneral = manager.findClass("foo.Nested.InnerGeneral", scope);
    assertNotNull(clazzInnerGeneral);

    PsiClass clazzInner$ = manager.findClass("foo.Nested.Inner$", scope);
    assertNotNull(clazzInner$);

    PsiClass clazzInner$Wrong1 = manager.findClass("foo.Nested.Inner$X", scope);
    assertNull(clazzInner$Wrong1);

    PsiClass clazzInner$Wrong2 = manager.findClass("foo.Nested.Inner$$X", scope);
    assertNull(clazzInner$Wrong2);

    PsiClass clazzInner$Wrong3 = manager.findClass("foo.Nested.Inner$$", scope);
    assertNull(clazzInner$Wrong3);
  }

}
