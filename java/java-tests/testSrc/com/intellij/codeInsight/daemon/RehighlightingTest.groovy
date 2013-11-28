/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
/**
 * @author peter
 */
class RehighlightingTest extends JavaCodeInsightFixtureTestCase {

  public void testDeleteClassCaptionUndo() {
    myFixture.addClass('package java.lang.reflect; public class Modifier {}')

    myFixture.configureByText 'a.java', '''
import java.lang.reflect.Modifier;

<caret><selection>class MemberModifiers extends Modifier {</selection>
    public static final MemberModifiers DEFAULT_MODIFIERS = new MemberModifiers(false, false, false);

    private final boolean isVirtual;
    private final boolean isOverride;

    public MemberModifiers(boolean isAbstract, boolean isVirtual, boolean isOverride) {
        this.isVirtual = isVirtual;
        this.isOverride = isOverride;
    }


    public boolean isVirtual() {
        return isVirtual;
    }

    public boolean isOverride() {
        return isOverride;
    }
}
'''
    myFixture.checkHighlighting(false, false, false)
    def caption = myFixture.editor.selectionModel.selectedText
    myFixture.type(' ')
    assert myFixture.doHighlighting().size() > 0

    WriteCommandAction.runWriteCommandAction project, {
      myFixture.editor.document.insertString(myFixture.editor.caretModel.offset, caption)
    }

    myFixture.checkHighlighting(false, false, false)
  }

}
