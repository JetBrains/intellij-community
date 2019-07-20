// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring.rename

import com.intellij.psi.PsiParameter
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.junit.Test

class JavaUniqueNameSuggesterTest : LightJavaCodeInsightFixtureTestCase() {
  @Test fun test() {
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

    val element = PsiTreeUtil.getParentOfType(file.findElementAt(editor.caretModel.offset), PsiParameter::class.java)

    assertEquals("entry", JavaCodeStyleManager.getInstance(project).suggestUniqueVariableName("entry", element, true))
  }
}