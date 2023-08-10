// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi;

import com.intellij.core.CoreJavaFileManager;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.JavaPsiTestCase;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;


@HeavyPlatformTestCase.WrapInCommand
public class CoreJavaFileManagerTest extends JavaPsiTestCase {

  public void testCommon() throws Exception {
    CoreJavaFileManager manager = configureManager("""
                                                     package foo;

                                                     public class TopLevel {
                                                     public class Inner {
                                                        public class Inner {}
                                                     }

                                                     }""", "TopLevel");

    assertCanFind(manager, "foo.TopLevel");
    assertCanFind(manager, "foo.TopLevel.Inner");
    assertCanFind(manager, "foo.TopLevel.Inner.Inner");

    assertCannotFind(manager, "foo.TopLevel$Inner.Inner");
    assertCannotFind(manager, "foo.TopLevel.Inner$Inner");
    assertCannotFind(manager, "foo.TopLevel.Inner.Inner.Inner");
  }

  public void testInnerClassesWithDollars() throws Exception {
    CoreJavaFileManager manager = configureManager("""
                                                     package foo;

                                                     public class TopLevel {
                                                     public class I$nner {   public class I$nner{}   public class $Inner{}   public class In$ne$r${}   public class Inner$${}   public class $$$$${}}
                                                     public class Inner$ {   public class I$nner{}   public class $Inner{}   public class In$ne$r${}   public class Inner$${}   public class $$$$${}}
                                                     public class In$ner$$ {   public class I$nner{}   public class $Inner{}   public class In$ne$r${}   public class Inner$${}   public class $$$$${}}

                                                     }""", "TopLevel");

    assertCanFind(manager, "foo.TopLevel");

    assertCanFind(manager, "foo.TopLevel.I$nner");
    assertCanFind(manager, "foo.TopLevel.I$nner.I$nner");
    assertCanFind(manager, "foo.TopLevel.I$nner.$Inner");
    assertCanFind(manager, "foo.TopLevel.I$nner.In$ne$r$");
    assertCanFind(manager, "foo.TopLevel.I$nner.Inner$$");
    assertCanFind(manager, "foo.TopLevel.I$nner.$$$$$");

    assertCannotFind(manager, "foo.TopLevel.I.nner.$$$$$");

    assertCanFind(manager, "foo.TopLevel.Inner$");
    assertCanFind(manager, "foo.TopLevel.Inner$.I$nner");
    assertCanFind(manager, "foo.TopLevel.Inner$.$Inner");
    assertCanFind(manager, "foo.TopLevel.Inner$.In$ne$r$");
    assertCanFind(manager, "foo.TopLevel.Inner$.Inner$$");
    assertCanFind(manager, "foo.TopLevel.Inner$.$$$$$");

    assertCannotFind(manager, "foo.TopLevel.Inner..$$$$$");

    assertCanFind(manager, "foo.TopLevel.In$ner$$");
    assertCanFind(manager, "foo.TopLevel.In$ner$$.I$nner");
    assertCanFind(manager, "foo.TopLevel.In$ner$$.$Inner");
    assertCanFind(manager, "foo.TopLevel.In$ner$$.In$ne$r$");
    assertCanFind(manager, "foo.TopLevel.In$ner$$.Inner$$");
    assertCanFind(manager, "foo.TopLevel.In$ner$$.$$$$$");

    assertCannotFind(manager, "foo.TopLevel.In.ner$$.$$$$$");
  }

  public void testTopLevelClassesWithDollars() throws Exception {
    CoreJavaFileManager inTheMiddle = configureManager("package foo;\n\n public class Top$Level {}", "Top$Level");
    assertCanFind(inTheMiddle, "foo.Top$Level");

    CoreJavaFileManager doubleAtTheEnd = configureManager("package foo;\n\n public class TopLevel$$ {}", "TopLevel$$");
    assertCanFind(doubleAtTheEnd, "foo.TopLevel$$");

    CoreJavaFileManager multiple = configureManager("package foo;\n\n public class Top$Lev$el$ {}", "Top$Lev$el$");
    assertCanFind(multiple, "foo.Top$Lev$el$");
    assertCannotFind(multiple, "foo.Top.Lev$el$");

    CoreJavaFileManager twoBucks = configureManager("package foo;\n\n public class $$ {}", "$$");
    assertCanFind(twoBucks, "foo.$$");
  }

  public void testTopLevelClassWithDollarsAndInners() throws Exception {
    CoreJavaFileManager manager = configureManager("""
                                                     package foo;

                                                     public class Top$Level$$ {
                                                     public class I$nner {   public class I$nner{}   public class In$ne$r${}   public class Inner$$$$${}   public class $Inner{}   public class ${}   public class $$$$${}}
                                                     public class Inner {   public class Inner{}}

                                                     }""", "Top$Level$$");

    assertCanFind(manager, "foo.Top$Level$$");

    assertCanFind(manager, "foo.Top$Level$$.Inner");
    assertCanFind(manager, "foo.Top$Level$$.Inner.Inner");

    assertCanFind(manager, "foo.Top$Level$$.I$nner");
    assertCanFind(manager, "foo.Top$Level$$.I$nner.I$nner");
    assertCanFind(manager, "foo.Top$Level$$.I$nner.In$ne$r$");
    assertCanFind(manager, "foo.Top$Level$$.I$nner.Inner$$$$$");
    assertCanFind(manager, "foo.Top$Level$$.I$nner.$Inner");
    assertCanFind(manager, "foo.Top$Level$$.I$nner.$");
    assertCanFind(manager, "foo.Top$Level$$.I$nner.$$$$$");

    assertCannotFind(manager, "foo.Top.Level$$.I$nner.$$$$$");
  }

  public void testDoNotThrowOnMalformedInput() throws Exception {
    CoreJavaFileManager fileWithEmptyName = configureManager("package foo;\n\n public class Top$Level {}", "");
    assertCannotFind(fileWithEmptyName, "foo.");
    assertCannotFind(fileWithEmptyName, ".");
    assertCannotFind(fileWithEmptyName, "..");
    assertCannotFind(fileWithEmptyName, "");
    assertCannotFind(fileWithEmptyName, ".foo");
  }

  public void testSeveralClassesInOneFile() throws Exception {
    CoreJavaFileManager manager = configureManager("""
                                                     package foo;

                                                     public class One {}
                                                     class Two {}
                                                     class Three {}""", "One");

    assertCanFind(manager, "foo.One");

    //NOTE: this is unsupported
    assertCannotFind(manager, "foo.Two");
    assertCannotFind(manager, "foo.Three");
  }

  public void testScopeCheck() throws Exception {
    CoreJavaFileManager manager = configureManager("""
                                                     package foo;

                                                     public class Test {}
                                                     """, "Test");

    assertNotNull("Should find class in all scope", manager.findClass("foo.Test", GlobalSearchScope.allScope(getProject())));
    assertNull("Should not find class in empty scope", manager.findClass("foo.Test", GlobalSearchScope.EMPTY_SCOPE));
  }

  @NotNull
  private CoreJavaFileManager configureManager(@Language("JAVA") @NotNull String text, @NotNull String className) throws Exception {
    VirtualFile root = createTestProjectStructure();
    VirtualFile pkg = createChildDirectory(root, "foo");
    PsiDirectory dir = myPsiManager.findDirectory(pkg);
    assertNotNull(dir);
    ApplicationManager.getApplication().runWriteAction(() -> {
      dir.add(PsiFileFactory.getInstance(getProject()).createFileFromText(className + ".java", JavaFileType.INSTANCE, text));
    });

    CoreJavaFileManager manager = new CoreJavaFileManager(myPsiManager);
    manager.addToClasspath(root);
    return manager;
  }

  private void assertCanFind(@NotNull CoreJavaFileManager manager, @NotNull String qName) {
    PsiClass foundClass = manager.findClass(qName, GlobalSearchScope.allScope(getProject()));
    assertNotNull("Could not find:" + qName, foundClass);
    assertEquals("Found " + foundClass.getQualifiedName() + " instead of " + qName, qName, foundClass.getQualifiedName());
  }

  private void assertCannotFind(@NotNull CoreJavaFileManager manager, @NotNull String qName) {
    PsiClass foundClass = manager.findClass(qName, GlobalSearchScope.allScope(getProject()));
    assertNull("Found, but shouldn't have:" + qName, foundClass);
  }
}
