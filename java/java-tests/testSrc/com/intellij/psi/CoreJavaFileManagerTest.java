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
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;


public class CoreJavaFileManagerTest extends PsiTestCase {

  public void testCommon() throws Exception {
    CoreJavaFileManager manager = configureManager("package foo;\n\n" +
                                                   "public class TopLevel {\n" +
                                                   "public class Inner {\n" +
                                                   "   public class Inner {}\n" +
                                                   "}\n" +
                                                   "\n" +
                                                   "}");

    assertCanFind(manager, "foo.TopLevel");
    assertCanFind(manager, "foo.TopLevel.Inner");
    assertCanFind(manager, "foo.TopLevel.Inner.Inner");
  }

  public void testInnerClassesWithDollars() throws Exception {

    CoreJavaFileManager manager = configureManager("package foo;\n\n" +
                                                   "public class TopLevel {\n" +

                                                   "public class I$nner {" +
                                                   "   public class I$nner{}" +
                                                   "   public class $Inner{}" +
                                                   "   public class In$ne$r${}" +
                                                   "   public class Inner$${}" +
                                                   "   public class $$$$${}" +
                                                   "}\n" +
                                                   "public class Inner$ {" +
                                                   "   public class I$nner{}" +
                                                   "   public class $Inner{}" +
                                                   "   public class In$ne$r${}" +
                                                   "   public class Inner$${}" +
                                                   "   public class $$$$${}" +
                                                   "}\n" +
                                                   "public class In$ner$$ {" +
                                                   "   public class I$nner{}" +
                                                   "   public class $Inner{}" +
                                                   "   public class In$ne$r${}" +
                                                   "   public class Inner$${}" +
                                                   "   public class $$$$${}" +
                                                   "}\n" +
                                                   "\n" +
                                                   "}");

    assertCanFind(manager, "foo.TopLevel");

    assertCanFind(manager, "foo.TopLevel.I$nner");
    assertCanFind(manager, "foo.TopLevel.I$nner.I$nner");
    assertCanFind(manager, "foo.TopLevel.I$nner.$Inner");
    assertCanFind(manager, "foo.TopLevel.I$nner.In$ne$r$");
    assertCanFind(manager, "foo.TopLevel.I$nner.Inner$$");
    assertCanFind(manager, "foo.TopLevel.I$nner.$$$$$");

    assertCanFind(manager, "foo.TopLevel.Inner$");
    assertCanFind(manager, "foo.TopLevel.Inner$.I$nner");
    assertCanFind(manager, "foo.TopLevel.Inner$.$Inner");
    assertCanFind(manager, "foo.TopLevel.Inner$.In$ne$r$");
    assertCanFind(manager, "foo.TopLevel.Inner$.Inner$$");
    assertCanFind(manager, "foo.TopLevel.Inner$.$$$$$");

    assertCanFind(manager, "foo.TopLevel.In$ner$$");
    assertCanFind(manager, "foo.TopLevel.In$ner$$.I$nner");
    assertCanFind(manager, "foo.TopLevel.In$ner$$.$Inner");
    assertCanFind(manager, "foo.TopLevel.In$ner$$.In$ne$r$");
    assertCanFind(manager, "foo.TopLevel.In$ner$$.Inner$$");
    assertCanFind(manager, "foo.TopLevel.In$ner$$.$$$$$");
  }

  @NotNull
  private CoreJavaFileManager configureManager(@Language("JAVA") @NotNull String text) throws Exception {
    VirtualFile root = PsiTestUtil.createTestProjectStructure(myProject, myModule, myFilesToDelete);
    VirtualFile pkg = root.createChildDirectory(this, "foo");
    PsiDirectory dir = myPsiManager.findDirectory(pkg);
    assertNotNull(dir);
    dir.add(PsiFileFactory.getInstance(getProject()).createFileFromText("TopLevel.java", JavaFileType.INSTANCE, text));
    CoreJavaFileManager manager = new CoreJavaFileManager(myPsiManager);
    manager.addToClasspath(root);
    return manager;
  }

  private void assertCanFind(@NotNull CoreJavaFileManager manager, @NotNull String qName) {
    PsiClass foundClass = manager.findClass(qName, GlobalSearchScope.allScope(getProject()));
    assertNotNull("Could not find:" + qName, foundClass);
    assertEquals("Found " + foundClass.getQualifiedName() + " instead of " + qName, qName, foundClass.getQualifiedName());
  }
}
