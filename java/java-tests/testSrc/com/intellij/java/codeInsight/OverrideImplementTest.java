// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.generation.OverrideImplementExploreUtil;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.generation.OverrideImplementsAnnotationsHandler;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class OverrideImplementTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/codeInsight/overrideImplement");
  }

  private void addRecordClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    //noinspection override
    myFixture.addClass("package java.lang;public abstract class Record {" +
                       "public abstract boolean equals(Object obj);" +
                       "public abstract int hashCode();" +
                       "public abstract String toString();}");
  }

  @Test
  public void testImplementRecordMethods() {
    addRecordClass();
    doTest(true);
  }

  @Test
  public void testImplementInterfaceMethodsInRecord() {
    addRecordClass();
    doTest(true);
  }

  @Test
  public void testOverrideRecordMethods() {
    addRecordClass();
    doTest(false);
  }

  @Test
  public void testProtectedConstructorInFinalClass() {
    doTest(false);
  }

  @Test
  public void testOverrideForImplicitClass() {
    doTest(false);
  }
  @Test
  public void testOverrideForClassNestedInImplicitClass() {
    doTest(false);
  }
  @Test
  public void testImplementExtensionMethods() {
    doTest(true);
  }
  @Test
  public void testOverrideExtensionMethods() {
    doTest(false);
  }
  @Test
  public void testMultipleSuperMethodsThroughGenerics() {
    doTest(true);
  }
  @Test
  public void testDoNotImplementExtensionMethods() {
    doTest(true);
  }
  @Test
  public void testExtensionMethods1() {
    doTest(true);
  }
  @Test
  public void testExtensionMethods2() {
    doTest(true);
  }
  @Test
  public void testSkipUnknownAnnotations() {
    doTest(true);
  }
  @Test
  public void testMultipleInheritedThrows() {
    doTest(false);
  }
  @Test
  public void testOverrideInInterface() {
    doTest(false);
  }
  @Test
  public void testOverrideInEmptyEnum() {
    doTest(false);
  }
  @Test
  public void testMultipleInheritanceWithThrowables() {
    doTest(true);
  }

  @Test
  public void testBrokenMethodDeclaration() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("interface A { m();}");
    PsiClass psiClass = myFixture.addClass("class B implements A {<caret>}");
    ReadAction.runBlocking(() -> assertThat(OverrideImplementExploreUtil.getMethodSignaturesToImplement(psiClass)).isNullOrEmpty());
  }

  @Test
  public void testImplementInInterface() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass(
      """
        interface A {
            void foo();
        }""");
    VirtualFile file = myFixture.addClass(
      """
        interface B extends A {
            <caret>
        }
        """).getContainingFile().getVirtualFile();
    myFixture.configureFromExistingVirtualFile(file);

    Presentation presentation = new Presentation();
    presentation.setText(ActionsBundle.message("action.ImplementMethods.text"));
    EdtTestUtil.runInEdtAndWait(() -> {
      CommandProcessor.getInstance().executeCommand(myFixture.getProject(), () -> invokeAction(true), presentation.getText(), null);
    });

    myFixture.checkResult(
      """
        interface B extends A {
            @Override
            default void foo() {
                <caret>
            }
        }
        """);
  }

  @Test
  public void testImplementInAnnotation() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    VirtualFile file = myFixture.addClass(
      """
        @interface A {
            <caret>
        }"""
    ).getContainingFile().getVirtualFile();
    myFixture.configureFromExistingVirtualFile(file);

    Presentation presentation = new Presentation();
    presentation.setText(ActionsBundle.message("action.ImplementMethods.text"));
    EdtTestUtil.runInEdtAndWait(() -> {
      CommandProcessor.getInstance().executeCommand(myFixture.getProject(), () -> invokeAction(true), presentation.getText(), null);
    });

    myFixture.checkResult(
      """
        @interface A {
            <caret>
        }""");
  }

  @Test
  public void testImplementInterfaceWhenClassProvidesProtectedImplementation() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass(
      """
        interface A {
          void f();
        }
        """);
    myFixture.addClass(
      """
        class B {
          protected void f() {}
        }
        """);

    VirtualFile file = myFixture.addClass(
      """
        class C extends B implements A {
           <caret>
        }
        """
    ).getContainingFile().getVirtualFile();
    myFixture.configureFromExistingVirtualFile(file);

    Presentation presentation = new Presentation();
    presentation.setText(ActionsBundle.message("action.ImplementMethods.text"));
    EdtTestUtil.runInEdtAndWait(() -> {
      CommandProcessor.getInstance().executeCommand(myFixture.getProject(), () -> invokeAction(true), presentation.getText(), null);
    });

    myFixture.checkResult(
      """
        class C extends B implements A {
            @Override
            public void f() {
                <caret>
            }
        }
        """
    );
  }

  @Test
  public void testImplementSameNamedInterfaces() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass(
      """
        class Main1 {
           interface I {
              void foo();
           }
        }
        """);
    myFixture.addClass(
      """
        class Main2 {
           interface I {
              void bar();
           }
        }
        """);

    VirtualFile file = myFixture.addClass(
      """
        class B implements Main1.I, Main2.I {
            <caret>
        }
        """).getContainingFile().getVirtualFile();
    myFixture.configureFromExistingVirtualFile(file);

    Presentation presentation = new Presentation();
    presentation.setText(ActionsBundle.message("action.ImplementMethods.text"));
    EdtTestUtil.runInEdtAndWait(() -> {
      CommandProcessor.getInstance().executeCommand(myFixture.getProject(), () -> invokeAction(true), presentation.getText(), null);
    });

    myFixture.checkResult(
      """
        class B implements Main1.I, Main2.I {
            @Override
            public void foo() {
                <caret>
            }

            @Override
            public void bar() {

            }
        }
        """);
  }

  @Test
  public void test_overriding_overloaded_method() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass(
      """
        package bar;
        interface A {
            void foo(Foo2 f);
            void foo(Foo1 f);
        }
        """);
    myFixture.addClass("package bar; class Foo1 {}");
    myFixture.addClass("package bar; class Foo2 {}");
    VirtualFile file = myFixture.addClass(
      """                                            
        package bar;
        class Test implements A {
            public void foo(Foo1 f) {}
            <caret>
        }
        """
    ).getContainingFile().getVirtualFile();
    myFixture.configureFromExistingVirtualFile(file);

    invokeAction(true);

    myFixture.checkResult(
      """
        package bar;
        class Test implements A {
            public void foo(Foo1 f) {}

            @Override
            public void foo(Foo2 f) {
                <caret>
            }
        }
        """);
  }

  @Test
  public void testOverrideLong() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass(
      """
        package bar;
        interface A {
            Long foo();
        }
        """);
    VirtualFile file = myFixture.addClass(
      """                                            
        package bar;
        class Test implements A {
            <caret>
        }
        """
    ).getContainingFile().getVirtualFile();
    myFixture.configureFromExistingVirtualFile(file);

    invokeAction(true);

    myFixture.checkResult(
      """
        package bar;
        class Test implements A {
            @Override
            public Long foo() {
                return 0L;
            }
        }
        """);
  }

  @Test
  public void testTypeAnnotationsInImplementedMethod() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    OverrideImplementsAnnotationsHandler handler = new OverrideImplementsAnnotationsHandler() {
      @Override
      public String[] getAnnotations(@NotNull PsiFile file) { return new String[]{"TA"}; }
    };
    OverrideImplementsAnnotationsHandler.EP_NAME.getPoint().registerExtension(handler, myFixture.getTestRootDisposable());

    myFixture.addClass(
      """
        import java.lang.annotation.*;
        @Target(ElementType.TYPE_USE)
        public @interface TA {
        }
        """.stripIndent());

    myFixture.configureByText("test.java", """
      import java.util.*;

      interface I {
          @TA List<@TA String> i(@TA String p1, @TA(1) int @TA(2) [] p2 @TA(3) []) throws @TA IllegalArgumentException;
      }

      class C implements I {
          <caret>
      }""".stripIndent());

    invokeAction(true);

    myFixture.checkResult(
      """
        import java.util.*;

        interface I {
            @TA List<@TA String> i(@TA String p1, @TA(1) int @TA(2) [] p2 @TA(3) []) throws @TA IllegalArgumentException;
        }

        class C implements I {
            @Override
            public @TA List<@TA String> i(@TA String p1, @TA(1) int @TA(3) [] @TA(2) [] p2) throws @TA IllegalArgumentException {
                return Collections.emptyList();
            }
        }""".stripIndent());
  }

  @Test
  public void testNoCustomOverrideImplementsHandler() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass(
      """
        package a;
        public @interface A {
        }
        """);

    myFixture.configureByText("test.java", """
      import java.util.*;
      import a.*;

      interface I {
          @A List<String> i(@A String p);
      }

      class C implements I {
          <caret>
      }""".stripIndent());

    invokeAction(true);

    myFixture.checkResult(
      """
        import java.util.*;
        import a.*;

        interface I {
            @A List<String> i(@A String p);
        }

        class C implements I {
            @Override
            public List<String> i(String p) {
                return Collections.emptyList();
            }
        }""".stripIndent());
  }

  @Test
  public void testCustomOverrideImplementsHandler() throws Exception {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass(
      """
        package a;
        public @interface A {
          String value();
        }
        """);

    ServiceContainerUtil.registerExtension(ApplicationManager.getApplication(), OverrideImplementsAnnotationsHandler.EP_NAME,
                                           new OverrideImplementsAnnotationsHandler() {
                                             @Override
                                             public String[] getAnnotations(@NotNull PsiFile file) {
                                               return new String[]{"a.A"};
                                             }
                                           }, myFixture.getTestRootDisposable());
    myFixture.configureByText("test.java", """
      import java.util.*;
      import a.*;

      interface I {
          @A("") List<String> i(@A("a") String p);
      }

      class C implements I {
          <caret>
      }""".stripIndent());

    invokeAction(true);

    myFixture.checkResult(
      """
        import java.util.*;
        import a.*;

        interface I {
            @A("") List<String> i(@A("a") String p);
        }

        class C implements I {
            @A("")
            @Override
            public List<String> i(@A("a") String p) {
                return Collections.emptyList();
            }
        }""".stripIndent());
  }

  @Test
  public void test_invocation_before_orphan_type_parameters_does_not_lead_to_stub_AST_mismatches() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.java", """
      public class Test implements Runnable{
          int i = ; <caret><X>
      }""");

    invokeAction(true);
    ReadAction.runBlocking(() -> {
      PsiTestUtil.checkStubsMatchText(myFixture.getFile());
      assertTrue(myFixture.getFile().getText().contains("run()"));
    });
  }

  @Test
  public void testTypeAnnotationsAfterKeyword() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    OverrideImplementsAnnotationsHandler handler = new OverrideImplementsAnnotationsHandler() {
      @Override
      public String[] getAnnotations(@NotNull PsiFile file) { return new String[]{"TA"}; }
    };
    OverrideImplementsAnnotationsHandler.EP_NAME.getPoint().registerExtension(handler, myFixture.getTestRootDisposable());

    myFixture.addClass(
      """
        import java.lang.annotation.*;
        @Target({ElementType.TYPE_USE, ElementType.METHOD})
        public @interface TA {
        }
        """.stripIndent());

    myFixture.configureByText("test.java", """
      import java.util.*;

      interface I {
          @TA
          public List<String> i(String p1, int[] p2) throws IllegalArgumentException;
      }

      class C implements I {
          <caret>
      }""".stripIndent());

    invokeAction(true);

    myFixture.checkResult(
      """
        import java.util.*;
        
        interface I {
            @TA
            public List<String> i(String p1, int[] p2) throws IllegalArgumentException;
        }
        
        class C implements I {
            @Override
            public @TA List<String> i(String p1, int[] p2) throws IllegalArgumentException {
                return Collections.emptyList();
            }
        }""".stripIndent());
  }

  @Test
  public void testTypeAnnotationsAfterKeywordWithGenerationBefore() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    JavaCodeStyleSettings instance = JavaCodeStyleSettings.getInstance(myFixture.getProject());
    boolean oldValue = instance.GENERATE_USE_TYPE_ANNOTATION_BEFORE_TYPE;
    instance.GENERATE_USE_TYPE_ANNOTATION_BEFORE_TYPE = false;

    try {
      OverrideImplementsAnnotationsHandler handler = new OverrideImplementsAnnotationsHandler() {
        @Override
        public String[] getAnnotations(@NotNull PsiFile file) { return new String[]{"TA"}; }
      };
      OverrideImplementsAnnotationsHandler.EP_NAME.getPoint().registerExtension(handler, myFixture.getTestRootDisposable());

      myFixture.addClass(
        """
          import java.lang.annotation.*;
          @Target({ElementType.TYPE_USE, ElementType.METHOD})
          public @interface TA {
          }
          """.stripIndent());

      myFixture.configureByText("test.java", """
      import java.util.*;
      
      interface I {
          @TA
          public List<String> i(String p1, int[] p2) throws IllegalArgumentException;
      }
      
      class C implements I {
          <caret>
      }""".stripIndent());

      invokeAction(true);

      myFixture.checkResult(
        """
          import java.util.*;
          
          interface I {
              @TA
              public List<String> i(String p1, int[] p2) throws IllegalArgumentException;
          }
          
          class C implements I {
              @TA
              @Override
              public List<String> i(String p1, int[] p2) throws IllegalArgumentException {
                  return Collections.emptyList();
              }
          }""".stripIndent());
    }finally {
      instance.GENERATE_USE_TYPE_ANNOTATION_BEFORE_TYPE = oldValue;
    }
  }

  @Test
  public void testSeveralAnnotations() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    OverrideImplementsAnnotationsHandler handler = new OverrideImplementsAnnotationsHandler() {
      @Override
      public String[] getAnnotations(@NotNull PsiFile file) { return new String[]{"R1", "R2", "R3", "R4"}; }
    };
    OverrideImplementsAnnotationsHandler.EP_NAME.getPoint().registerExtension(handler, myFixture.getTestRootDisposable());

    myFixture.configureByText("test.java", """
      import java.lang.annotation.*;
      @Retention(RetentionPolicy.RUNTIME)
      @Target({ElementType.METHOD, ElementType.TYPE_USE})
      @interface R2 {
          boolean test();
      }
      @Retention(RetentionPolicy.RUNTIME)
      @Target({ElementType.METHOD, ElementType.TYPE_USE})
      @interface R1 {
          boolean test();
      }
      @Retention(RetentionPolicy.RUNTIME)
      @Target({ElementType.METHOD, ElementType.TYPE_USE})
      @interface R3 {
          boolean test();
      }
      @Retention(RetentionPolicy.RUNTIME)
      @Target({ElementType.METHOD, ElementType.TYPE_USE})
      @interface R4 {
          boolean test();
      }
      abstract public class AAA {
          @R1(test = true)
          @R3(test = true)
          abstract public @R2(test = false) @R4(test = false) Object test();
      }
      class BBB extends AAA {
          <caret>
      }""".stripIndent());

    invokeAction(true);

    myFixture.checkResult(
      """
        import java.lang.annotation.*;
        @Retention(RetentionPolicy.RUNTIME)
        @Target({ElementType.METHOD, ElementType.TYPE_USE})
        @interface R2 {
            boolean test();
        }
        @Retention(RetentionPolicy.RUNTIME)
        @Target({ElementType.METHOD, ElementType.TYPE_USE})
        @interface R1 {
            boolean test();
        }
        @Retention(RetentionPolicy.RUNTIME)
        @Target({ElementType.METHOD, ElementType.TYPE_USE})
        @interface R3 {
            boolean test();
        }
        @Retention(RetentionPolicy.RUNTIME)
        @Target({ElementType.METHOD, ElementType.TYPE_USE})
        @interface R4 {
            boolean test();
        }
        abstract public class AAA {
            @R1(test = true)
            @R3(test = true)
            abstract public @R2(test = false) @R4(test = false) Object test();
        }
        class BBB extends AAA {
            @Override
            public @R1(test = true) @R3(test = true) @R2(test = false) @R4(test = false) Object test() {
                return null;
            }
        }""".stripIndent());
  }

  @Test
  public void testSeveralAnnotationsWithGenerationBefore() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    JavaCodeStyleSettings instance = JavaCodeStyleSettings.getInstance(myFixture.getProject());
    boolean oldValue = instance.GENERATE_USE_TYPE_ANNOTATION_BEFORE_TYPE;
    instance.GENERATE_USE_TYPE_ANNOTATION_BEFORE_TYPE = false;
    try {
      OverrideImplementsAnnotationsHandler handler = new OverrideImplementsAnnotationsHandler() {
        @Override
        public String[] getAnnotations(@NotNull PsiFile file) { return new String[]{"R1", "R2", "R3", "R4"}; }
      };
      OverrideImplementsAnnotationsHandler.EP_NAME.getPoint().registerExtension(handler, myFixture.getTestRootDisposable());

      myFixture.configureByText("test.java", """
      import java.lang.annotation.*;
      @Retention(RetentionPolicy.RUNTIME)
      @Target({ElementType.METHOD, ElementType.TYPE_USE})
      @interface R2 {
          boolean test();
      }
      @Retention(RetentionPolicy.RUNTIME)
      @Target({ElementType.METHOD, ElementType.TYPE_USE})
      @interface R1 {
          boolean test();
      }
      @Retention(RetentionPolicy.RUNTIME)
      @Target({ElementType.METHOD, ElementType.TYPE_USE})
      @interface R3 {
          boolean test();
      }
      @Retention(RetentionPolicy.RUNTIME)
      @Target({ElementType.METHOD, ElementType.TYPE_USE})
      @interface R4 {
          boolean test();
      }
      abstract public class AAA {
          @R1(test = true)
          @R3(test = true)
          abstract public @R2(test = false) @R4(test = false) Object test();
      }
      class BBB extends AAA {
          <caret>
      }""".stripIndent());

      invokeAction(true);

      myFixture.checkResult(
        """
          import java.lang.annotation.*;
          @Retention(RetentionPolicy.RUNTIME)
          @Target({ElementType.METHOD, ElementType.TYPE_USE})
          @interface R2 {
              boolean test();
          }
          @Retention(RetentionPolicy.RUNTIME)
          @Target({ElementType.METHOD, ElementType.TYPE_USE})
          @interface R1 {
              boolean test();
          }
          @Retention(RetentionPolicy.RUNTIME)
          @Target({ElementType.METHOD, ElementType.TYPE_USE})
          @interface R3 {
              boolean test();
          }
          @Retention(RetentionPolicy.RUNTIME)
          @Target({ElementType.METHOD, ElementType.TYPE_USE})
          @interface R4 {
              boolean test();
          }
          abstract public class AAA {
              @R1(test = true)
              @R3(test = true)
              abstract public @R2(test = false) @R4(test = false) Object test();
          }
          class BBB extends AAA {
              @R1(test = true)
              @R3(test = true)
              @R2(test = false)
              @R4(test = false)
              @Override
              public Object test() {
                  return null;
              }
          }""".stripIndent());
    }finally {
      instance.GENERATE_USE_TYPE_ANNOTATION_BEFORE_TYPE = oldValue;
    }
  }

  @Test
  public void testDiamondInheritance() {
    doTest(true);
  }

  @Test
  public void testDiamondInheritanceWithGenerics() {
    doTest(true);
  }

  @Test
  public void testMarkdownComment() {
    doTest(true);
  }

  private void doTest(boolean toImplement) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final String name = myFixture.getTestName(false);
    myFixture.configureByFile("before" + name + ".java");
    invokeAction(toImplement);
    myFixture.checkResultByFile("after" + name + ".java");
  }

  private void invokeAction(boolean toImplement) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      Editor editor = myFixture.getEditor();
      PsiFile file = myFixture.getFile();
      PsiClass aClass = OverrideImplementUtil.getContextClass(myFixture.getProject(), editor, file, true);
      assertNotNull(aClass);
      OverrideImplementUtil.chooseAndOverrideOrImplementMethods(myFixture.getProject(), editor, aClass, toImplement);
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    });
  }
}
