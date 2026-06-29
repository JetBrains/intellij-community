// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupEx;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.inplace.MemberInplaceRenameHandler;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import com.intellij.util.containers.ContainerUtil;

import java.util.List;

public class RenameSuggestionsTest extends LightJavaCodeInsightTestCase {
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

  public void testByOptionalOfNullableInitializer() {
    List<String> suggestions = getNameSuggestions("""
        import java.util.*;
        
        class Foo {{
          Foo typeValue = this;
          Optional<Foo> <caret>o = Optional.ofNullable(typeValue);
        }}""");
    assertEquals(List.of("typeValue1", "value", "foo", "optionalFoo", "fooOptional", "optional"), suggestions);
  }

  public void testByOptionalOfInitializerWithConstructor() {
    List<String> suggestions = getNameSuggestions("""
        import java.util.*;
        class Foo {{
          Optional<Foo> <caret>o = Optional.ofNullable(new Foo());
        }}""");
    assertEquals(List.of("foo", "optionalFoo", "fooOptional", "optional"), suggestions);
  }

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

  public void testConstant() {
    List<String> names = getNameSuggestions("""
                                              class X {
                                                public static final String fooBa<caret>rBaz = "";
                                              }
                                              """);
    assertEquals(List.of("FOO_BAR_BAZ", "BAR_BAZ", "BAZ", "STRING"), names);
  }

  public void testNonConstant() {
    List<String> names = getNameSuggestions("""
                                              class X {
                                                public static String FOO_BAR_<caret>BAZ = "";
                                              }
                                              """);
    assertEquals(List.of("fooBarBaz", "barBaz", "baz", "string"), names);
  }
  
  public void testSimpleField() {
    List<String> names = getNameSuggestions("""
                                              import java.util.ArrayList;
                                              class X {
                                                private List<String> typ<caret>eValue = new ArrayList<>();
                                              }
                                              """);
    assertEquals(List.of("value", "objects", "objectArrayList", "arrayList", "list", "stringList"), names);
  }

  public void testSimpleClass() {
    List<String> names = getNameSuggestions("""
                                              class RecentlyChangedFilesState<caret> {
                                              }
                                              """);
    assertEquals(List.of("ChangedFilesState", "FilesState", "State"), names);
  }

  private void doTestSuggestionAvailable(String text, String... expectedSuggestions) {
    List<String> suggestions = getNameSuggestions(text);
    for (String suggestion : expectedSuggestions) {
      assertTrue(suggestions.contains(suggestion));
    }
  }

  private List<String> getNameSuggestions(String text) {
    configureFromFileText("a.java", text);
    boolean oldPreselectSetting = getEditor().getSettings().isPreselectRename();
    try {
      TemplateManagerImpl.setTemplateTesting(getTestRootDisposable());
      final PsiElement element = TargetElementUtil.findTargetElement(getEditor(), TargetElementUtil.getInstance().getAllAccepted());
      assertNotNull(element);

      MemberInplaceRenameHandler handler = new MemberInplaceRenameHandler();
      handler.doRename(element, getEditor(), null);

      LookupEx lookup = LookupManager.getActiveLookup(getEditor());
      assertNotNull(lookup);
      return ContainerUtil.map(lookup.getItems(), LookupElement::getLookupString);
    }
    finally {
      getEditor().getSettings().setPreselectRename(oldPreselectSetting);
      TemplateState state = TemplateManagerImpl.getTemplateState(getEditor());
      assertNotNull(state);
      state.gotoEnd(false);
    }
  }
}