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

import java.util.LinkedList;
import java.util.Queue;


public class CoreJavaFileManagerTest extends PsiTestCase {

  private VirtualFile prepareClasses(String clazzName, String clazzData) throws Exception {
    VirtualFile root = PsiTestUtil.createTestProjectStructure(myProject, myModule, myFilesToDelete);
    VirtualFile pkg = root.createChildDirectory(this, "foo");
    PsiDirectory dir = myPsiManager.findDirectory(pkg);
    assertNotNull(dir);
    dir.add(PsiFileFactory.getInstance(getProject()).createFileFromText(clazzName + ".java", JavaFileType.INSTANCE, clazzData));
    return root;
  }

  public void testNotNullInnerClass() throws Exception {
    String text = "package foo;\n\n" +
                  "public class Nested {\n" +
                  "public class InnerGeneral {}\n" +
                  "public class Inner$ {" +
                  "}\n" +
                  "\n" +
                  "public Inner$ inner() {\n" +
                  "   return  new Inner$();\n" +
                  "}\n" +
                  "\n" +
                  "}";

    VirtualFile root = prepareClasses("Nested", text);
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


  public void testNotNullInnerClass2() throws Exception {
    String text = "package foo;\n\n" +
                  "public class Nested {\n" +

                  "public class Inner {" +
                  "   public class XInner{}" +
                  "   public class XInner${}" +
                  "}\n" +
                  "public class Inner$ {" +
                  "   public class XInner{}" +
                  "   public class XInner${}" +
                  "}\n" +
                  "\n" +
                  "}";

    VirtualFile root = prepareClasses("Nested", text);
    GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());
    CoreJavaFileManager manager = new CoreJavaFileManager(myPsiManager);
    manager.addToClasspath(root);

    PsiClass clazzInner = manager.findClass("foo.Nested.Inner", scope);
    assertNotNull(clazzInner);

    PsiClass clazzXInner = manager.findClass("foo.Nested.Inner.XInner", scope);
    assertNotNull(clazzXInner);

    PsiClass clazzXInner$ = manager.findClass("foo.Nested.Inner.XInner$", scope);
    assertNotNull(clazzXInner$);

    PsiClass clazz$XInner = manager.findClass("foo.Nested.Inner$.XInner", scope);
    assertNotNull(clazz$XInner);

    PsiClass clazz$XInner$ = manager.findClass("foo.Nested.Inner$.XInner$", scope);
    assertNotNull(clazz$XInner$);
  }


  public void testNotNullInnerClass3() throws Exception {
    String text = "package foo;\n\n" +
                  "public class NestedX {\n" +

                  "public class XX {" +
                  "   public class XXX{" +
                  "     public class XXXX{ }" +
                  "     public class XXXX${ }" +
                  "   }" +
                  "   public class XXX${" +
                  "     public class XXXX{ }" +
                  "     public class XXXX${ }" +
                  "   }" +
                  "}\n" +
                  "public class XX$ {" +
                  "   public class XXX{" +
                  "     public class XXXX{ }" +
                  "     public class XXXX${ }" +
                  "   }" +
                  "   public class XXX${" +
                  "     public class XXXX{ }" +
                  "     public class XXXX${ }" +
                  "   }" +
                  "}\n" +
                  "\n" +
                  "}";

    VirtualFile root = prepareClasses("NestedX", text);
    GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());
    CoreJavaFileManager manager = new CoreJavaFileManager(myPsiManager);
    manager.addToClasspath(root);

    Queue<String> queue = new LinkedList<String>();
    queue.add("foo.NestedX");

    while(!queue.isEmpty()) {
      String head = queue.remove();
      PsiClass clazzInner = manager.findClass(head, scope);
      assertNotNull(head, clazzInner);      
      String lastSegment = head.substring(head.lastIndexOf('.'));
      String xs = lastSegment.substring(lastSegment.indexOf("X")).replace("$", "");
      if (xs.length() < 4) {
        queue.add(head + "." + xs + "X");
        queue.add(head + "." + xs + "X$");
      }
    }
  }

}
