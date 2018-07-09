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
package com.intellij.diff

import com.intellij.diff.fragments.LineFragment
import com.intellij.diff.tools.util.base.HighlightPolicy
import com.intellij.diff.tools.util.base.IgnorePolicy
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder
import com.intellij.diff.tools.util.text.SimpleTextDiffProvider
import com.intellij.diff.util.Range
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.runInEdtAndWait

abstract class HeavyDiffTestCase : DiffTestCase() {
  var projectFixture: IdeaProjectTestFixture? = null
  var project: Project? = null

  override fun setUp() {
    super.setUp()

    projectFixture = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getTestName()).fixture
    projectFixture!!.setUp()
    project = projectFixture!!.project
  }

  override fun tearDown() {
    try {
      project = null
      projectFixture?.tearDown()
    }
    finally {
      super.tearDown()
    }
  }

  override fun runBare() {
    runInEdtAndWait {
      super.runBare()
    }
  }


  protected fun compareExplicitBlocks(text1: CharSequence, text2: CharSequence, ranges: List<Range>,
                                      highlightPolicy: HighlightPolicy, ignorePolicy: IgnorePolicy): List<LineFragment> {
    val settings = TextDiffSettingsHolder.TextDiffSettings()
    settings.highlightPolicy = highlightPolicy
    settings.ignorePolicy = ignorePolicy

    val disposable = Disposer.newDisposable()
    try {
      val diffProvider = SimpleTextDiffProvider(settings, Runnable {}, disposable)
      return diffProvider.compare(text1, text2, ranges, INDICATOR)!!.flatMap { it }
    }
    finally {
      Disposer.dispose(disposable)
    }
  }
}