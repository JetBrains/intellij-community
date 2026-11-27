// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture;
import com.intellij.core.CoreJavaFileManager;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.heavyJavaCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;


@TestApplication
public class CoreJavaFileManagerTest {
  private final TestFixture<HeavyJavaCodeInsightFixture> heavyFixture = heavyJavaCodeInsightFixture(JavaTestUtil.getJavaTestDataPath());

  @Test
  public void testCommon() {
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

  @Test
  public void testInnerClassesWithDollars() {
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

  @Test
  public void testTopLevelClassesWithDollars() {
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

  @Test
  public void testTopLevelClassWithDollarsAndInners() {
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

  @Test
  public void testDoNotThrowOnMalformedInput() {
    CoreJavaFileManager fileWithEmptyName = configureManager("package foo;\n\n public class Top$Level {}", "");
    assertCannotFind(fileWithEmptyName, "foo.");
    assertCannotFind(fileWithEmptyName, ".");
    assertCannotFind(fileWithEmptyName, "..");
    assertCannotFind(fileWithEmptyName, "");
    assertCannotFind(fileWithEmptyName, ".foo");
  }

  @Test
  public void testSeveralClassesInOneFile() {
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

  @Test
  public void testScopeCheck() {
    CoreJavaFileManager manager = configureManager("""
                                                     package foo;

                                                     public class Test {}
                                                     """, "Test");

    Project project = heavyFixture.get().getProject();
    ReadAction.runBlocking(() -> {
      assertNotNull(manager.findClass("foo.Test", GlobalSearchScope.allScope(project)), "Should find class in all scope");
      assertNull(manager.findClass("foo.Test", GlobalSearchScope.EMPTY_SCOPE), "Should not find class in empty scope");
    });
  }

  @NotNull
  private CoreJavaFileManager configureManager(@Language("JAVA") @NotNull String text, @NotNull String className) {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    VirtualFile root = fixture.createTestProjectStructure(null);
    VirtualFile pkg = VfsTestUtil.createDir(root, "foo");
    PsiDirectory dir = ReadAction.computeBlocking(() -> fixture.getPsiManager().findDirectory(pkg));
    assertNotNull(dir);
    WriteCommandAction.writeCommandAction(fixture.getProject()).run(() -> {
      dir.add(PsiFileFactory.getInstance(fixture.getProject()).createFileFromText(className + ".java", JavaFileType.INSTANCE, text));
    });

    CoreJavaFileManager manager = new CoreJavaFileManager(fixture.getPsiManager());
    manager.addToClasspath(root);
    return manager;
  }

  private void assertCanFind(@NotNull CoreJavaFileManager manager, @NotNull String qName) {
    Project project = heavyFixture.get().getProject();
    ReadAction.runBlocking(() -> {
      PsiClass foundClass = manager.findClass(qName, GlobalSearchScope.allScope(project));
      assertNotNull(foundClass, "Could not find:" + qName);
      assertEquals(qName, foundClass.getQualifiedName(), "Found " + foundClass.getQualifiedName() + " instead of " + qName);
    });
  }

  private void assertCannotFind(@NotNull CoreJavaFileManager manager, @NotNull String qName) {
    Project project = heavyFixture.get().getProject();
    PsiClass foundClass = ReadAction.computeBlocking(() -> manager.findClass(qName, GlobalSearchScope.allScope(project)));
    assertNull(foundClass, "Found, but shouldn't have:" + qName);
  }
}
