// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.lookup.LookupEx
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInsight.template.impl.TemplateState
import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.inplace.VariableInplaceRenameHandler
import com.intellij.testFramework.LightCodeInsightTestCase
import groovy.transform.CompileStatic

@CompileStatic
class RenameSuggestionsTest extends LightCodeInsightTestCase {
  void "test by parameter name"() {
    def text = """\
     class Test {
         void foo(int foo) {}
         {
             int bar = 0;
             foo(b<caret>ar);
         }
     }
   }
   """

    doTestSuggestionAvailable(text, "foo")
  }
  
  void "test lambda parameter name"() {
    def text = """\
     import java.util.Map;

class LambdaRename {
    void q(Map<String, LambdaRename> map) {
        map.forEach((k, <caret>v) -> {
            System.out.println(k + v);
        });
    }
}
   }
   """

    doTestSuggestionAvailable(text, "lambdaRename", "rename", "v")
  }

  void "test foreach scope"() {
    def text = """\
     class Foo {
        {
           for (Foo <caret>f : new Foo[] {});
           for (Foo foo : new Foo[] {});
        }
     }
   """

    doTestSuggestionAvailable(text, "foo")
  }

  void "test by super parameter name"() {
    def text = """\
     class Test {
         void foo(int foo) {}
     }
     
     class TestImpl extends Test {
         void foo(int foo<caret>1) {}
     }
   }
   """

    doTestSuggestionAvailable(text, "foo")
  }


  void "test by Optional_of initializer"() {
    def suggestions = getNameSuggestions("""
import java.util.*;
class Foo {{
  Foo typeValue = null;
  Optional<Foo> <caret>o = Optional.of(typeValue);
}}
""")
    assert suggestions == ["typeValue1", "value", "foo", "optionalFoo", "fooOptional", "optional", "o"]
  }

  void "test by Optional_ofNullable initializer"() {
    def suggestions = getNameSuggestions("""
import java.util.*;
class Foo {{
  Foo typeValue = this;
  Optional<Foo> <caret>o = Optional.ofNullable(typeValue);
}}
""")
    assert suggestions == ["typeValue1", "value", "foo", "optionalFoo", "fooOptional", "optional", "o"]
  }

  void "test by Optional_of initializer with constructor"() {
    def suggestions = getNameSuggestions("""
import java.util.*;
class Foo {{
  Optional<Foo> <caret>o = Optional.ofNullable(new Foo());
}}
""")
    assert suggestions == ["foo", "optionalFoo", "fooOptional", "optional", "o"]
  }

  void "test by Optional_flatMap"() {
    def suggestions = getNameSuggestions("""
import java.util.*;
class Foo {{
  Optional<Car> <caret>o = Optional.of(new Person()).flatMap(Person::getCar);
}}
class Person {
    Optional<Car> getCar() {}
}
class Car {}
""")
    assert suggestions == ["car", "optionalCar", "carOptional", "optional", "o"]
  }

  void "test long qualified name"() {
    def suggestions = getNameSuggestions("""
class Foo {
  Inner inner;
  class Inner {
    String getCat() {}
  }
  
  void m(Foo f){
    String <caret>s = f.inner.getCat(); 
  }
}
""")
    assert suggestions == ["cat", "innerCat", "s"]
  }

  private doTestSuggestionAvailable(String text, String... expectedSuggestions) {
    def suggestions = getNameSuggestions(text)
    for (String suggestion : expectedSuggestions) {
      assert suggestion in suggestions
    }
    
  }
  
  private List<String> getNameSuggestions(String text) {
    configure text
    def oldPreselectSetting = myEditor.settings.preselectRename
    try {
      TemplateManagerImpl.setTemplateTesting(getTestRootDisposable())
      final PsiElement element = TargetElementUtil.findTargetElement(myEditor, TargetElementUtil.getInstance().getAllAccepted())

      assertNotNull(element)

      VariableInplaceRenameHandler handler = new VariableInplaceRenameHandler()


      handler.doRename(element, editor, null)
      
      LookupEx lookup = LookupManager.getActiveLookup(editor)
      assertNotNull(lookup)
      return lookup.items.collect { it.lookupString }
    }
    finally {
      myEditor.settings.preselectRename = oldPreselectSetting

      TemplateState state = TemplateManagerImpl.getTemplateState(editor)

      assertNotNull(state)

      state.gotoEnd(false)
    }
  }

  private def configure(String text) {
    configureFromFileText("a.java", text)
  }
}