/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.folding

import com.intellij.codeInsight.folding.impl.CodeFoldingManagerImpl
import com.intellij.codeInsight.folding.impl.JavaCodeFoldingSettingsImpl
import com.intellij.codeInsight.folding.impl.JavaFoldingBuilder
import com.intellij.openapi.editor.ex.FoldingModelEx
import com.intellij.openapi.editor.impl.FoldingModelImpl
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.annotations.NotNull

/**
 * @author peter
 */
class JavaFolding8Test extends LightCodeInsightFixtureTestCase {

  JavaCodeFoldingSettingsImpl myFoldingSettings
  JavaCodeFoldingSettingsImpl myFoldingStateToRestore

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8
  }

  @Override
  void setUp() {
    super.setUp()
    myFoldingSettings = JavaCodeFoldingSettings.instance as JavaCodeFoldingSettingsImpl
    myFoldingStateToRestore = new JavaCodeFoldingSettingsImpl()
    myFoldingStateToRestore.loadState(myFoldingSettings)
  }

  @Override
  protected void tearDown() {
    myFoldingSettings.loadState(myFoldingStateToRestore)
    super.tearDown()
  }

  void "test no plain lambda folding where anonymous class can be real lambda but fold otherwise"() {
    myFixture.addClass('interface Runnable2 { void run(); }')
    myFixture.addClass('abstract class MyAction { public void run(); public void update() {} }')
    def text = """\
class Test {
  void test() {
    Runnable r = new Runnable2() {
      public void run() {
        System.out.println();
      }
    };
    MyAction action = new MyAction() {
      public void run() {
        System.out.println();
      }
    }
  }
}
"""
    configure text
    def foldingModel = myFixture.editor.foldingModel as FoldingModelImpl

    assert foldingModel.getCollapsedRegionAtOffset(text.indexOf("MyAction(")).placeholderText == 'run() ' + JavaFoldingBuilder.rightArrow + ' { '
    assert !foldingModel.getCollapsedRegionAtOffset(text.indexOf("Runnable2("))
  }

  void "test closure folding when implementing a single abstract method in a class"() {
    myFixture.addClass('abstract class MyAction { public abstract void run(); }')
    def text = """\
class Test {
  void test() {
    MyAction action = new MyAction() {
      public void run() {
        System.out.println();
      }
    }
  }
}
"""
    configure text
    def foldingModel = myFixture.editor.foldingModel as FoldingModelImpl

    assert foldingModel.getCollapsedRegionAtOffset(text.indexOf("MyAction("))?.placeholderText == '() ' + JavaFoldingBuilder.rightArrow + ' { '
  }

  private def configure(String text) {
    myFixture.configureByText("a.java", text)
    CodeFoldingManagerImpl.getInstance(getProject()).buildInitialFoldings(myFixture.editor)
    def foldingModel = myFixture.editor.foldingModel as FoldingModelEx
    foldingModel.rebuild()
    myFixture.doHighlighting()
  }

}
