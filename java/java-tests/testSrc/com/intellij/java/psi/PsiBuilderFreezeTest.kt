// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DEPRECATION")

package com.intellij.java.psi

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.lang.PsiBuilder
import com.intellij.lang.impl.PsiBuilderImpl
import com.intellij.lang.java.JavaParserDefinition
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.impl.source.tree.SharedImplUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

@TestApplication
internal class PsiBuilderFreezeTest {
  companion object {
    private val projectFixture = projectFixture()
    private val module = projectFixture.moduleFixture("src")
    private val sourceRoot = module.sourceRootFixture()
  }

  private val psiFile by sourceRoot.psiFileFixture("Test.java", "")
  private val project get() = projectFixture.get()

  private suspend fun createBuilder(text: String): PsiBuilder {
    val lang = JavaFileType.INSTANCE.language
    val parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(lang)
    assertNotNull(parserDefinition)
    writeAction {
      psiFile.fileDocument.setText(text)
    }
    return readAction {
      PsiBuilderImpl(/* project = */ project,
                     /* containingFile = */ psiFile,
                     /* parserDefinition = */ parserDefinition,
                     /* lexer = */ JavaParserDefinition.createLexer(LanguageLevel.JDK_1_5),
                     /* charTable = */ SharedImplUtil.findCharTableByTree(psiFile.node),
                     /* text = */ text,
                     /* originalTree = */ null,
                     /* parentLightTree = */ null)
    }
  }

  @Test
  fun `test psi builder does not freeze`() = runBlocking(/*timeout = 150.milliseconds,*/ context = Dispatchers.Default) {
    val builder = createBuilder("class Test {}")

    val writeAccessDelay = 50.milliseconds
    val maxReadAccessExpectedDuration = 200.milliseconds

    val restarted = AtomicInteger(0)
    launch {
      readAction {
        val started = System.currentTimeMillis()

        val count = restarted.incrementAndGet()
        if (count > 1) {
          return@readAction
        }

        builder.advanceLexer()

        while (true) {
          val marker = builder.mark()
          builder.advanceLexer()
          marker.rollbackTo()

          val lng = System.currentTimeMillis() - started
          if (lng > maxReadAccessExpectedDuration.inWholeMilliseconds) {
            fail { "read action was not canceled for more than ${maxReadAccessExpectedDuration - writeAccessDelay}: $lng" }
          }
        }
      }
    }

    delay(writeAccessDelay)

    writeAction {} // restart all read actions

    delay(50.milliseconds)

    assert(restarted.get() > 1) { "Builder was not restarted: ${restarted.get()}" }
  }
}