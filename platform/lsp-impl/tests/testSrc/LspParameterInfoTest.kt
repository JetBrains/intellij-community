package com.intellij.platform.lsp

import com.intellij.lang.parameterInfo.CreateParameterInfoContext
import com.intellij.openapi.application.readAction
import com.intellij.platform.lsp.common.configureServerSession
import com.intellij.platform.lsp.common.fakeLspServerProviderFixture
import com.intellij.platform.lsp.impl.features.parameterInfo.LspParameterInfoContext
import com.intellij.platform.lsp.impl.features.parameterInfo.LspParameterInfoHandler
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.utils.parameterInfo.MockCreateParameterInfoContext
import com.intellij.testFramework.utils.parameterInfo.MockUpdateParameterInfoContext
import kotlinx.coroutines.CoroutineScope
import org.eclipse.lsp4j.SignatureHelp
import org.eclipse.lsp4j.SignatureHelpOptions
import org.eclipse.lsp4j.SignatureInformation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test


@TestApplication
internal class LspParameterInfoTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
    private val project by projectFixture

    @Suppress("unused")
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)
  }

  private val codeInsightFixture by codeInsightFixture(projectFixture, tempDirFixture)

  @Suppress("unused")
  private val fakeLspServerProvider by projectFixture.fakeLspServerProviderFixture(
    configureServerCapabilities = {
      signatureHelpProvider = SignatureHelpOptions()
    },
  )

  @Test
  fun `basic signature with outer active parameter`(): Unit = timeoutRunBlocking {
    codeInsightFixture.configureByText("a.txt", "function('a', <caret>1)")

    val signatureHelp = SignatureHelp()
    signatureHelp.signatures = listOf(SignatureInformation("function(param1: String, param2: Int)"))
    signatureHelp.activeParameter = 1

    val updateContext = querySignatureHelp(signatureHelp)

    assertEquals(1, updateContext.currentParameter)
    assertEquals("function(param1: String, param2: Int)", (updateContext.objectsToView[0] as LspParameterInfoContext).signatureHelp.signatures[0].label)
  }

  @Test
  fun `basic signature with inner active parameter`(): Unit = timeoutRunBlocking {
    codeInsightFixture.configureByText("a.txt", "function('a', 1, true)")

    val signatureHelp = SignatureHelp()
    signatureHelp.signatures = listOf(
      SignatureInformation("function(param1: String, param2: Int, param3: Boolean)").apply { activeParameter = 2 }
    )
    signatureHelp.activeSignature = 0

    val updateContext = querySignatureHelp(signatureHelp)

    assertEquals(2, updateContext.currentParameter)
    assertEquals(
      "function(param1: String, param2: Int, param3: Boolean)",
      (updateContext.objectsToView[0] as LspParameterInfoContext).signatureHelp.signatures[0].label,
    )
  }

  private suspend fun CoroutineScope.querySignatureHelp(mockSignatureHelp: SignatureHelp): MockUpdateParameterInfoContext {
    val serverSession = configureServerSession(project, codeInsightFixture.file.virtualFile)

    fun expectResult() {
      serverSession.expectRequest(serverSession.SIGNATURE_HELP, { true }) {
        mockSignatureHelp
      }
    }

    val handler = LspParameterInfoHandler()
    val createContext: CreateParameterInfoContext = MockCreateParameterInfoContext(codeInsightFixture.editor, codeInsightFixture.file)
    expectResult()
    val element = readAction { handler.findElementForParameterInfo(createContext) }

    val updateContext = MockUpdateParameterInfoContext(codeInsightFixture.editor, codeInsightFixture.file, createContext.getItemsToShow())
    updateContext.parameterOwner = element
    expectResult()
    readAction { handler.updateParameterInfo(element!!, updateContext) }

    return updateContext
  }
}
