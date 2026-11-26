// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupEx;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.inplace.MemberInplaceRenameHandler;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class RenameSuggestionsTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, "/java/java-tests/testData");

  @Test
  public void testByParameterName() {
    String text = """
         class Test {
             void foo(int foo) {}
             {
                 int bar = 0;
                 foo(b<caret>ar);
             }
         }""";

    doTestSuggestionAvailable(text, "foo");
  }

  @Test
  public void testLambdaParameterName() {
    String text = """
      import java.util.Map;

      class LambdaRename {
          void q(Map<String, LambdaRename> map) {
              map.forEach((k, <caret>v) -> {
                  System.out.println(k + v);
              });
          }
      }""";

    doTestSuggestionAvailable(text, "lambdaRename", "rename");
  }

  @Test
  public void testForeachScope() {
    String text = """
         class Foo {
            {
               for (Foo <caret>f : new Foo[] {});
               for (Foo foo : new Foo[] {});
            }
         }""";

    doTestSuggestionAvailable(text, "foo");
  }

  @Test
  public void testBySuperParameterName() {
    String text = """
         class Test {
             void foo(int foo) {}
         }

         class TestImpl extends Test {
             void foo(int foo<caret>1) {}
         }""";
    List<String> names = getNameSuggestions(text);
    assertEquals(List.of("foo", "i"), names);
  }

  @Test
  public void testByOptionalOfInitializer() {
    List<String> suggestions = getNameSuggestions("""
                                                    import java.util.*;
                                                    
                                                    class Foo {{
                                                      Foo typeValue = null;
                                                      Optional<Foo> <caret>o = Optional.of(typeValue);
                                                    }}
                                                    """);
    assertEquals(List.of("typeValue1", "value", "foo", "optionalFoo", "fooOptional", "optional"), suggestions);
  }

  @Test
  public void testByOptionalOfNullableInitializer() {
    List<String> suggestions = getNameSuggestions("""
        import java.util.*;
        
        class Foo {{
          Foo typeValue = this;
          Optional<Foo> <caret>o = Optional.ofNullable(typeValue);
        }}""");
    assertEquals(List.of("typeValue1", "value", "foo", "optionalFoo", "fooOptional", "optional"), suggestions);
  }

  @Test
  public void testByOptionalOfInitializerWithConstructor() {
    List<String> suggestions = getNameSuggestions("""
        import java.util.*;
        class Foo {{
          Optional<Foo> <caret>o = Optional.ofNullable(new Foo());
        }}""");
    assertEquals(List.of("foo", "optionalFoo", "fooOptional", "optional"), suggestions);
  }

  @Test
  public void testByOptionalFlatMap() {
    List<String> suggestions = getNameSuggestions("""
        import java.util.*;
        
        class Foo {{
          Optional<Car> <caret>o = Optional.of(new Person()).flatMap(Person::getCar);
        }}
        class Person {
            Optional<Car> getCar() {}
        }
        class Car {}
        """);
    assertEquals(List.of("car", "optionalCar", "carOptional", "optional"), suggestions);
  }

  @Test
  public void testLongQualifiedName() {
    List<String> suggestions = getNameSuggestions("""
                                                    class Foo {
                                                      Inner inner;
                                                      class Inner {
                                                        String getCat() {}
                                                      }

                                                      void m(Foo f){
                                                        String <caret>s = f.inner.getCat();\s
                                                      }
                                                    }
                                                    """);
    assertEquals(List.of("cat", "innerCat", "string"), suggestions);
  }

  @Test
  public void testConstant() {
    List<String> names = getNameSuggestions("""
                                              class X {
                                                public static final String fooBa<caret>rBaz = "";
                                              }
                                              """);
    assertEquals(List.of("FOO_BAR_BAZ", "BAR_BAZ", "BAZ", "STRING"), names);
  }

  @Test
  public void testNonConstant() {
    List<String> names = getNameSuggestions("""
                                              class X {
                                                public static String FOO_BAR_<caret>BAZ = "";
                                              }
                                              """);
    assertEquals(List.of("fooBarBaz", "barBaz", "baz", "string"), names);
  }
  
  @Test
  public void testSimpleField() {
    List<String> names = getNameSuggestions("""
                                              import java.util.ArrayList;
                                              class X {
                                                private List<String> typ<caret>eValue = new ArrayList<>();
                                              }
                                              """);
    assertEquals(List.of("value", "objects", "objectArrayList", "arrayList", "list", "stringList"), names);
  }

  @Test
  public void testSimpleClass() {
    List<String> names = getNameSuggestions("""
                                              class RecentlyChangedFilesState<caret> {
                                              }
                                              """);
    assertEquals(List.of("ChangedFilesState", "FilesState", "State"), names);
  }
  
  @Test
  public void testAcronyms() {
    List<String> names = getNameSuggestions("""
                                              class APIClient {
                                                String <caret>fileAPI;
                                              }""");
    assertEquals(List.of("fileApi", "api", "string"), names);

    List<String> names2 = getNameSuggestions("""
                                              class <caret>APIClient {
                                                String fileAPI;
                                              }""");
    assertEquals(List.of("ApiClient", "Client"), names2);
  }

  private void doTestSuggestionAvailable(String text, String... expectedSuggestions) {
    List<String> suggestions = getNameSuggestions(text);
    for (String suggestion : expectedSuggestions) {
      assertTrue(suggestions.contains(suggestion));
    }
  }

  private List<String> getNameSuggestions(String text) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.java", text);
    Ref<List<String>> suggestions = new Ref<>();
    myFixture.runInCommand(() -> {
      boolean oldPreselectSetting = myFixture.getEditor().getSettings().isPreselectRename();
      try {
        TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
        final PsiElement element = TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.getInstance().getAllAccepted());
        assertNotNull(element);

        MemberInplaceRenameHandler handler = new MemberInplaceRenameHandler();
        handler.doRename(element, myFixture.getEditor(), null);

        LookupEx lookup = LookupManager.getActiveLookup(myFixture.getEditor());
        assertNotNull(lookup);
        suggestions.set(ContainerUtil.map(lookup.getItems(), LookupElement::getLookupString));
      }
      finally {
        myFixture.getEditor().getSettings().setPreselectRename(oldPreselectSetting);
        TemplateState state = TemplateManagerImpl.getTemplateState(myFixture.getEditor());
        assertNotNull(state);
        state.gotoEnd(false);
      }
    });
    return suggestions.get();
  }
}