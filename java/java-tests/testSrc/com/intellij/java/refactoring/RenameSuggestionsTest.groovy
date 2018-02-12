/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.refactoring

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.lookup.LookupEx
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInsight.template.impl.TemplateState
import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.inplace.VariableInplaceRenameHandler
import com.intellij.testFramework.LightCodeInsightTestCase

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
      TemplateManagerImpl.setTemplateTesting(getProject(), getTestRootDisposable())
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