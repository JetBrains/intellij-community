// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.generate.inspection;

import com.intellij.codeInsight.generation.PsiElementClassMember;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.java.generate.GenerateToStringActionHandlerImpl;
import org.jetbrains.java.generate.GenerateToStringContext;
import org.jetbrains.java.generate.GenerateToStringWorker;
import org.jetbrains.java.generate.config.Config;
import org.jetbrains.java.generate.config.ConflictResolutionPolicy;
import org.jetbrains.java.generate.config.ReplacePolicy;
import org.jetbrains.java.generate.template.TemplateResource;
import org.jetbrains.java.generate.template.toString.ToStringTemplatesManager;

import java.util.Arrays;
import java.util.Collection;

public class ToStringGeneratingTest extends LightJavaCodeInsightFixtureTestCase {
  public void testDuplicateToStringAnInnerClass() {
    doTest("""
             public class Foobar  {
                 private int foo;
                 private int bar;

                 @Override <caret>
                 public String toString() {
                     return "Foobar{" +
                             "foo=" + foo +
                             ", bar=" + bar +
                             '}';
                 }

                 public static class Nested {
                 }
             }
             """, """
             public class Foobar  {
                 private int foo;
                 private int bar;

                 @Override
                 public String <caret>toString() {
                     return "Foobar{" +
                             "foo=" + foo +
                             ", bar=" + bar +
                             '}';
                 }

                 public static class Nested {
                 }
             }
             """, ReplacePolicy.getInstance());
  }

  public void testProtectedFieldInSuper() {
    doTest("""
             class Foobar extends Foo {
                 private int bar;
                 <caret>\s
             }
             class Foo  {
                 protected int foo;
             }
             """, """
             class Foobar extends Foo {
                 private int bar;

                 @Override
                 public String toString() {
                     return "Foobar{" +
                             "foo=" + foo +
                             ", bar=" + bar +
                             '}';
                 }
             }
             class Foo  {
                 protected int foo;
             }
             """, ReplacePolicy.getInstance());
  }

  public void testPrivateFieldWithGetterInSuper() {
    Config config = GenerateToStringContext.getConfig();
    config.setEnableMethods(true);
    try {
      doTest("""
               class Foobar extends Foo {
                   private int bar;
                   <caret>\s
               }
               class Foo  {
                   private int foo;
                   public int getFoo() {
                      return foo;
                   }
               }
               """, """
               class Foobar extends Foo {
                   private int bar;

                   @Override
                   public String toString() {
                       return "Foobar{" +
                               "foo=" + getFoo() +
                               ", bar=" + bar +
                               '}';
                   }
               }
               class Foo  {
                   private int foo;
                   public int getFoo() {
                      return foo;
                   }
               }
               """, ReplacePolicy.getInstance());
    }
    finally {
      config.setEnableMethods(false);
    }
  }

  public void testPrivateFieldWithGetterInSuperSortSuperFirst() {
    Config config = GenerateToStringContext.getConfig();
    config.setEnableMethods(true);
    config.setSortElements(3);
    try {
      doTest("""
               class Foobar extends Foo {
                   private int bar;
                   <caret>\s
               }
               class Foo  {
                   private int foo;
                   public int getFoo() {
                      return foo;
                   }
               }
               """, """
               class Foobar extends Foo {
                   private int bar;

                   @Override
                   public String toString() {
                       return "Foobar{" +
                               "foo=" + getFoo() +
                               ", bar=" + bar +
                               '}';
                   }
               }
               class Foo  {
                   private int foo;
                   public int getFoo() {
                      return foo;
                   }
               }
               """, ReplacePolicy.getInstance());
    }
    finally {
      config.setEnableMethods(false);
      config.setSortElements(0);
    }
  }

  public void testAbstractSuperToString() {
    doTest("""

             class FooImpl extends Foo {
             <caret>
             }

             abstract class Foo {
               public abstract toString();
             }
             """, """

             class FooImpl extends Foo {
                 @Override
                 public String toString() {
                     return "FooImpl{}";
                 }
             }

             abstract class Foo {
               public abstract toString();
             }
             """, ReplacePolicy.getInstance(), findTemplate("String concat (+) and super.toString()"));
  }

  public void testImplicitClass() {
    doTest(
            """
            private Integer i = 2;
            
            public void main()
            {
            }
            
            <caret>
            
            public class T{
            }""",
           """
            private Integer i = 2;
            
            public void main()
            {
            }
            
            
            
            public class T{
            }
            
            @Override
            public String toString() {
                return "a{" +
                        "i=" + i +
                        '}';
            }""", ReplacePolicy.getInstance(), findTemplate("String concat (+) and super.toString()"));
  }

  public void testImplicitClassWithoutMain() {
    doTest("""
            public void main()
            {
            }
            
            <caret>
            """,
           """
             public void main()
             {
             }
             
             @Override
             public String toString() {
                 return "a{}";
             }
             
             
             """, ReplacePolicy.getInstance(), findTemplate("String concat (+) and super.toString()"));
  }

  public void testAnonymousClass() {
    doTest(
      """
            public class Test {
                public static void main(String[] args) {
                    new Runnable() {
                        <caret>
                        @Override
                        public void run() {
                        }
                    };
                }
            }
            """,
           """
              public class Test {
                  public static void main(String[] args) {
                      new Runnable() {
                          @Override
                          public String toString() {
                              return "anonymous Runnable{}";
                          }
              
                          @Override
                          public void run() {
                          }
                      };
                  }
              }
              """, ReplacePolicy.getInstance(), findTemplate("String concat (+) and super.toString()"), true);
  }

  private void doTest(@NotNull String before,
                      @NotNull String after,
                      @NotNull ConflictResolutionPolicy policy,
                      @NotNull TemplateResource template){
    doTest(before, after, policy, template, false);
  }

  private void doTest(@NotNull String before,
                      @NotNull String after,
                      @NotNull ConflictResolutionPolicy policy,
                      @NotNull TemplateResource template,
                      boolean fromCaret) {
    myFixture.configureByText("a.java", before);

    PsiClass clazz = findClass(fromCaret);
    Collection<PsiMember> members = collectMembers(clazz);
    GenerateToStringWorker worker = buildWorker(clazz, policy);

    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), "", "", () -> {
      worker.execute(members, template, policy);
    }, myFixture.getFile());

    myFixture.checkResult(after);
  }

  private void doTest(@NotNull String before, @NotNull String after, @NotNull ConflictResolutionPolicy policy) {
    doTest(before, after, policy, ToStringGeneratingTest.findDefaultTemplate());
  }

  @NotNull
  private GenerateToStringWorker buildWorker(@NotNull final PsiClass clazz, @NotNull final ConflictResolutionPolicy policy) {
    return new GenerateToStringWorker(clazz, myFixture.getEditor(), true) {
      @Override
      protected ConflictResolutionPolicy exitsMethodDialog(TemplateResource template) {
        return policy;
      }
    };
  }

  @NotNull
  private static TemplateResource findDefaultTemplate() {
    return findTemplate("String concat (+)");
  }

  @NotNull
  private static TemplateResource findTemplate(final String templateName) {
    Collection<TemplateResource> templates = ToStringTemplatesManager.getInstance().getAllTemplates();
    TemplateResource template = ContainerUtil.find(templates, x -> x.getFileName().equals(templateName));
    assertNotNull(template);
    return template;
  }

  @NotNull
  private static Collection<PsiMember> collectMembers(@NotNull PsiClass clazz) {
    PsiElementClassMember<?>[] memberElements = GenerateToStringActionHandlerImpl.buildMembersToShow(clazz);
    return Arrays.stream(memberElements)
      .<PsiMember>map(x -> x.getElement())
      .sorted((o1, o2) -> compareMembers(o1, o2))
      .toList();
  }

  private static int compareMembers(PsiMember o1, PsiMember o2) {
    PsiClass c1 = o1.getContainingClass();
    PsiClass c2 = o2.getContainingClass();
    return c1.equals(c2) ? o2.getName().compareTo(o1.getName()) : c1.isInheritor(c2, true) ? 1 : -1;
  }

  @NotNull
  private PsiClass findClass(boolean fromCaret) {
    PsiFile file = myFixture.getFile();
    if (fromCaret) {
      Editor editor = getEditor();
      int offset = editor.getCaretModel().getOffset();
      PsiElement element = file.findElementAt(offset);
      PsiClass psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
      assertNotNull(psiClass);
      return psiClass;
    }
    assertInstanceOf(file, PsiJavaFile.class);
    PsiClass[] classes = ((PsiJavaFile)file).getClasses();
    assertTrue(classes.length > 0);
    return classes[0];
  }
}
