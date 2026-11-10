// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupEx;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class CreateMethodFromUsageTemplateTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testTemplateAssertions() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.java", """
                                class SomeOuterClassWithLongName {
                                    void foo(PropertyDescriptorWithVeryLongName.Group group, PropertyDescriptorWithVeryLongName.Group child) {
                                        group.add<caret>SubGroup(child);
                                    }
                                    static class PropertyDescriptorWithVeryLongName {
                                        static class Group {

                                        }
                                    }
                                }
                                """);
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
    EdtTestUtil.runInEdtAndWait(() -> {
      doAction("Create method 'addSubGroup' in 'Group in PropertyDescriptorWithVeryLongName in SomeOuterClassWithLongName'");
      TemplateState state = TemplateManagerImpl.getTemplateState(myFixture.getEditor());
      //skip void return type
      state.nextTab();

      // parameter type
      final LookupEx lookup = LookupManager.getActiveLookup(myFixture.getEditor());
      final LookupElement item = (lookup == null ? null : lookup.getCurrentItem());
      assertTrue((item == null ? null : item.getLookupString()).endsWith("Group"));

      myFixture.type("\n");

      // parameter name, skip it
      final LookupEx lookup1 = LookupManager.getActiveLookup(myFixture.getEditor());
      final LookupElement item1 = (lookup1 == null ? null : lookup1.getCurrentItem());
      assertEquals("child", (item1 == null ? null : item1.getLookupString()));
      state.nextTab();

      assertTrue(state.isFinished());

      myFixture.checkResult("""
          class SomeOuterClassWithLongName {
              void foo(PropertyDescriptorWithVeryLongName.Group group, PropertyDescriptorWithVeryLongName.Group child) {
                  group.addSubGroup(child);
              }
              static class PropertyDescriptorWithVeryLongName {
                  static class Group {

                      public void addSubGroup(Group child) {
                          <selection></selection>
                      }
                  }
              }
          }
          """);
    });
  }

  public void doAction(String hint) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.launchAction(myFixture.findSingleIntention(hint));
  }

  @Test
  public void test_prefer_nearby_return_types() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.java", """
                                class Singleton {
                                    boolean add(Object o) {}

                                }
                                class Usage {
                                    void foo() {
                                        Singleton.get<caret>Instance().add("a");
                                    }

                                }
                                """);
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
    doAction("Create method 'getInstance' in 'Singleton'");
    final LookupEx lookup = LookupManager.getActiveLookup(myFixture.getEditor());
    final LookupElement item = (lookup == null ? null : lookup.getCurrentItem());
    assertEquals("Singleton", (item == null ? null : item.getLookupString()));
  }

  @Test
  public void test_delete_created_modifiers() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.java", """
                                interface Singleton {
                                    default boolean add(Object o) {}

                                }
                                class Usage {
                                    void foo() {
                                        Singleton.get<caret>Instance().add("a");
                                    }

                                }
                                """);
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
    EdtTestUtil.runInEdtAndWait(() -> {
      doAction("Create method 'getInstance' in 'Singleton'");
      TemplateState state = TemplateManagerImpl.getTemplateState(myFixture.getEditor());

      final Document document = myFixture.getEditor().getDocument();
      final int offset = myFixture.getEditor().getCaretModel().getOffset();

      WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
          PsiMethod method = PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(offset), PsiMethod.class);
          method.getModifierList().setModifierProperty(PsiModifier.STATIC, false);
          PsiDocumentManager.getInstance(myFixture.getFile().getProject()).commitDocument(document);
        });
      state.gotoEnd(false);
    });
  }

  @Test
  public void test_prefer_outer_class_when_static_is_not_applicable_for_inner() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.java", """
                                class A {
                                    int x;
                                    A(int x) { this.x = x; }
                                    class B extends A{
                                        B(int x) { super(f<caret>oo(x)); }
                                    }
                                }
                                """);
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
    EdtTestUtil.runInEdtAndWait(() -> {
      doAction("Create method 'foo' in 'A'");
      TemplateManagerImpl.getTemplateState(myFixture.getEditor()).gotoEnd(false);

      myFixture.checkResult("""
          class A {
              int x;
              A(int x) { this.x = x; }
              class B extends A{
                  B(int x) { super(foo(x)); }
              }

              private static int foo(int x) {
                  return 0;
              }
          }
          """);
    });
  }

  @Test
  public void test_use_fully_qualified_names_with_conflicting_imports() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(myFixture.getProject());
    settings.setUseFqClassNames(true);
    myFixture.configureByText("a.java",
                              """

                                import java.awt.List;
                                class A {
                                    void m(java.util.List<String> list){
                                      fo<caret>o(list);
                                    }
                                }
                                """);
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
    EdtTestUtil.runInEdtAndWait(() -> {
      doAction("Create method 'foo' in 'A'");
      TemplateState state = TemplateManagerImpl.getTemplateState(myFixture.getEditor());

      final Document document = myFixture.getEditor().getDocument();
      final int offset = myFixture.getEditor().getCaretModel().getOffset();

      ApplicationManager.getApplication().runWriteAction(() -> {
          PsiMethod method = PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(offset), PsiMethod.class);
          method.getModifierList().setModifierProperty(PsiModifier.STATIC, false);
          PsiDocumentManager.getInstance(myFixture.getFile().getProject()).commitDocument(document);
        });

      state.gotoEnd(false);

      myFixture.checkResult("""

          import java.awt.List;
          class A {
              void m(java.util.List<String> list){
                foo(list);
              }

              private void foo(java.util.List<String> list) {
                 \s
              }
          }
          """);
    });
  }

  @Test
  public void test_format_adjusted_imports() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.java", """
                                  /**javadoc*/
                                  class A {
                                      void m(java.util.List<String> list){
                                        fo<caret>o(list);
                                      }
                                  }""");
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
    EdtTestUtil.runInEdtAndWait(() -> {
      doAction("Create method 'foo' in 'A'");
      TemplateState state = TemplateManagerImpl.getTemplateState(myFixture.getEditor());

      state.gotoEnd(false);

      myFixture.checkResult("""
            import java.util.List;

            /**javadoc*/
            class A {
                void m(java.util.List<String> list){
                  foo(list);
                }

                private void foo(List<String> list) {
                   \s
                }
            }""");
    });
  }

  @Test
  public void test_guess_type_parameters() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.java", """
                                public class A {
                                    void m(java.util.List<String> list, B<String> b) {
                                        b.<caret>foo(list);
                                    }
                                }

                                class B<T>
                                {
                                }
                                """);
    doAction("Create method 'foo' in 'B'");
    myFixture.checkResult("""
        import java.util.List;

        public class A {
            void m(java.util.List<String> list, B<String> b) {
                b.foo(list);
            }
        }

        class B<T>
        {
            public void foo(List<T> list) {
                <caret>
            }
        }
        """);
  }

  @Test
  public void test_create_property_in_invalid_class() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("InvalidClass.java", """
      public class InvalidClass {
          void usage() {
              <caret>getFoo();
          }
      """);

    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
    EdtTestUtil.runInEdtAndWait(() -> {
      doAction("Create read-only property 'foo' in 'InvalidClass'");
      TemplateManagerImpl.getTemplateState(myFixture.getEditor()).gotoEnd(false);

      myFixture.checkResult("""
          public class InvalidClass {
              private Object foo;

              void usage() {
                  getFoo();
              }

              public Object getFoo() {<caret>
                  return foo;
              }
          """);
    });
  }

  @Test
  public void test_deepest_super_methods_are_included_in_expected_info_when_available() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.java", """
      class A {
        {
          new A().get<caret>Bar().toString();
        }
      }
      """);
    ReadAction.runBlocking(() -> {
      PsiExpression expr = PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset()), PsiExpression.class);

      ExpectedTypeInfo[] types = ExpectedTypesProvider.getExpectedTypes(expr, false);
      assertNotNull(
        ContainerUtil.find(types, info -> info.getDefaultType().equalsToText(CommonClassNames.JAVA_LANG_OBJECT)));
    });
  }

  @Test
  public void test_typing_generics() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.java", "class A {\n    {\n        pass<caret>Class(String.class)\n    }\n}\n");
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
    CodeInsightSettings.getInstance().setSelectAutopopupSuggestionsByChars(true);
    try {
      doAction("Create method 'passClass' in 'A'");
      myFixture.type("\t");
      myFixture.type("Class<?>\n");
      myFixture.checkResult("""
          class A {
              {
                  passClass(String.class)
              }

              private void passClass(Class<?> <selection>stringClass</selection>) {
                 \s
              }
          }
          """);
    }
    finally {
      CodeInsightSettings.getInstance().setSelectAutopopupSuggestionsByChars(false);
    }
  }
  
  @Test
  public void testObjectArrayArgs() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.java", """
      import java.util.List;
      
      public final class CreateMethodArray {
          public static void main(String[] args) {
              List<?>[] settings = {List.of(), List.of(1)};
              <caret>useThem(settings);
          }
      }""");
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
    doAction("Create method 'useThem' in 'CreateMethodArray'");
    myFixture.type('\n');
    myFixture.type('\n');
    myFixture.checkResult("""
                            import java.util.List;
                            
                            public final class CreateMethodArray {
                                public static void main(String[] args) {
                                    List<?>[] settings = {List.of(), List.of(1)};
                                    useThem(settings);
                                }
                            
                                private static void useThem(List<?>[] <selection>settings<caret></selection>) {
                                   \s
                                }
                            }""");
  }

  @Test
  public void testPrimitiveArrayArgs() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.java", """
      public final class X {
          public static void main(String[] args) {
              int[] is = {1, 2, 4};
              <caret>f(is);
          }
      }""");
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
    doAction("Create method 'f' in 'X'");
    myFixture.type('\n');
    myFixture.type('\n');
    myFixture.checkResult("""
                            public final class X {
                                public static void main(String[] args) {
                                    int[] is = {1, 2, 4};
                                    f(is);
                                }
                            
                                private static void f(int[] <selection>is<caret></selection>) {
                                   \s
                                }
                            }""");
  }
}
