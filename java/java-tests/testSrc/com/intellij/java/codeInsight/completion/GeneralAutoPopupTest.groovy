// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.completion

import com.intellij.codeInsight.completion.CompletionAutoPopupTestCase
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.InjectedLanguagePlaces
import com.intellij.psi.LanguageInjector
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.testFramework.PlatformTestUtil
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull

/**
 * For tests checking platform behavior not related to Java language (but they may still use Java for code samples)
 */
@CompileStatic
class GeneralAutoPopupTest extends CompletionAutoPopupTestCase {
  void "test no autopopup in the middle of word when the only variant is already in the editor"() {
    myFixture.configureByText 'a.java', 'class Foo { private boolean ignoredProperty; public boolean isIgnoredP<caret>operty() {}}'
    type 'r'
    assert !lookup
  }

  void "test no lookup after typing a letter and then quickly overtyping a quote"() {
    myFixture.configureByText 'a.html', '<a href="<caret>">'
    myFixture.type('a')
    type '"'
    assert !lookup
  }

  void "test no lookup after typing and quickly moving caret to another place"() {
    myFixture.configureByText 'a.java', 'class Foo { <caret> }'
    edt {
      myFixture.type('F')
      myFixture.editor.caretModel.moveToOffset(myFixture.caretOffset + 1)
    }

    myTester.joinAutopopup()
    myTester.joinCompletion()

    assert !lookup
  }

  void "test injectors are not run in EDT"() {
    boolean injectorCalled = false
    LanguageInjector injector = new LanguageInjector() {
      @Override
      void getLanguagesToInject(@NotNull PsiLanguageInjectionHost host, @NotNull InjectedLanguagePlaces injectionPlacesRegistrar) {
        injectorCalled = true
        assert !ApplicationManager.application.dispatchThread
      }
    }
    PlatformTestUtil.maskExtensions(LanguageInjector.EXTENSION_POINT_NAME, [injector] as List<LanguageInjector>, myFixture.testRootDisposable)

    myFixture.configureByText 'a.java', 'class Foo { String s = <caret>; }'
    assert !injectorCalled
    type '"'

    injectorCalled = false
    type 'abc'
    
    assert injectorCalled
    assert !lookup
  }
}
