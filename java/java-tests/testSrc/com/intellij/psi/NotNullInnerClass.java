package com.intellij.psi;

import com.intellij.core.CoreJavaFileManager;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.testFramework.PsiTestUtil;


public class NotNullInnerClass extends PsiTestCase {

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
  }

}
