// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring.rename

import com.intellij.openapi.application.readAction
import com.intellij.psi.PsiParameter
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

@TestApplication
class JavaUniqueNameSuggesterTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @Test fun test(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    myFixture.configureByText("a.java", """import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

class Boo {
    public static <K, V> Map<K, V> filterOptionalMapValues(Map<K, Optional<V>> map) {
        return map.entrySet().stream()
                .filter(entry -> entry.getValue().isPresent())
                .collect(Collectors.toMap(Map.Entry::getKey, <caret>e -> e.getValue().get()));
    }
}
""")

    readAction {
      val element = PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(myFixture.editor.caretModel.offset), PsiParameter::class.java)

      assertEquals("entry", JavaCodeStyleManager.getInstance(myFixture.project).suggestUniqueVariableName("entry", element, true))
    }
  }
}