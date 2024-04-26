// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.externalProcessAuthHelper

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.askPassword
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ssh.SSHUtil
import externalApp.nativessh.NativeSshAskPassAppHandler
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

class NativeSshGuiAuthenticator(private val project: Project,
                                private val authenticationGate: AuthenticationGate,
                                private val authenticationMode: AuthenticationMode,
                                private val doNotRememberPasswords: Boolean) : NativeSshAskPassAppHandler {
  companion object {
    private val LOG = logger<NativeSshGuiAuthenticator>()
  }

  private val passwordHandlers = listOf(
    KeyPassphrasePromptHandler(),
    SshPasswordPromptHandler(),
    SshPinPromptHandler(),
    ConfirmationPromptHandler()
  )

  override fun handleInput(description: @NlsSafe String): String? {
    LOG.debug("handleInput: ${description}, mode: ${authenticationMode}")

    if (authenticationMode == AuthenticationMode.NONE) {
      LOG.debug("authenticationMode: NONE")
      return null
    }
    return authenticationGate.waitAndCompute { doHandleInput(description) }
  }

  private fun doHandleInput(description: @NlsSafe String): String? {
    for (passwordHandler in passwordHandlers) {
      val answer = passwordHandler.handleInput(description)
      if (answer is PromptAnswer.Answer) {
        LOG.debug("handling using: $passwordHandler")
        return answer.value
      }
    }

    LOG.debug("handling using generic prompt")
    return askGenericInput(description)
  }

  private inner class KeyPassphrasePromptHandler : PasswordPromptHandler() {
    override val title: String = ExternalProcessAuthHelperBundle.message("ssh.ask.passphrase.title")
    override val serviceName: String = ExternalProcessAuthHelperBundle.message("label.credential.store.key.ssh.passphrase")
    override fun parseDescription(description: String): Prompt? {
      val matcher = SSHUtil.PASSPHRASE_PROMPT.matcher(description)
      if (!matcher.matches()) return null

      val keyPath = SSHUtil.extractKeyPath(matcher)
      val promptMessage = ExternalProcessAuthHelperBundle.message("ssh.ask.passphrase.message", keyPath)
      return Prompt(keyPath, promptMessage)
    }
  }

  private inner class SshPasswordPromptHandler : PasswordPromptHandler() {
    override val title: String = ExternalProcessAuthHelperBundle.message("ssh.password.title")
    override val serviceName: String = ExternalProcessAuthHelperBundle.message("label.credential.store.key.ssh.password")
    override fun parseDescription(description: String): Prompt? {
      val matcher = SSHUtil.PASSWORD_PROMPT.matcher(description)
      if (!matcher.matches()) return null

      val username = SSHUtil.extractUsername(matcher)
      val promptMessage = ExternalProcessAuthHelperBundle.message("ssh.password.message", username)
      return Prompt(username, promptMessage)
    }
  }

  private inner class SshPinPromptHandler : PasswordPromptHandler() {
    override val title: String = ExternalProcessAuthHelperBundle.message("ssh.ask.pin.title")
    override val serviceName: String = ExternalProcessAuthHelperBundle.message("label.credential.store.key.ssh.pin")
    override fun parseDescription(description: String): Prompt? {
      val matcher = SSHUtil.PKCS_PIN_TOKEN_PROMPT.matcher(description)
      if (!matcher.matches()) return null

      val username = SSHUtil.extractPkcsTokenLabel(matcher)
      val promptMessage = ExternalProcessAuthHelperBundle.message("ssh.ask.pin.message", username)
      return Prompt(username, promptMessage)
    }
  }

  private abstract inner class PasswordPromptHandler : PromptHandler {
    private var lastAskedKey: String? = null

    protected abstract val title: String
    protected abstract val serviceName: String
    protected abstract fun parseDescription(description: String): Prompt?

    override fun handleInput(description: String): PromptAnswer {
      val prompt = parseDescription(description)
      if (prompt == null) return PromptAnswer.NotHandled

      val resetPassword = prompt.askedKey == lastAskedKey
      lastAskedKey = prompt.askedKey

      val answer = askPassword(project, prompt, resetPassword)
      return PromptAnswer.Answer(answer)
    }

    private fun askPassword(project: Project?, prompt: Prompt, resetPassword: Boolean): @NonNls String? {
      if (authenticationMode == AuthenticationMode.NONE) return null

      if (doNotRememberPasswords) {
        return askUserOnEdt {
          Messages.showPasswordDialog(project, prompt.promptMessage, title, null)
        }
      }

      val serviceName = generateServiceName(serviceName, prompt.askedKey)
      val credentialAttributes = CredentialAttributes(serviceName, prompt.askedKey)

      if (!resetPassword) {
        val credentials = PasswordSafe.instance.get(credentialAttributes)
        if (credentials != null) {
          val password = credentials.getPasswordAsString()
          if (password != null) return password
        }
      }

      if (authenticationMode == AuthenticationMode.SILENT) return null

      return askPassword(project, title, prompt.promptMessage, credentialAttributes, true)
    }
  }

  private inner class ConfirmationPromptHandler : PromptHandler {
    private var lastAskedConfirmationInput: String? = null

    override fun handleInput(description: String): PromptAnswer {
      if (!description.contains(SSHUtil.CONFIRM_CONNECTION_PROMPT)) {
        return PromptAnswer.NotHandled
      }

      val answer = askUserOnEdt {
        val message = stripYesNoSuffix(description)

        val knownAnswer = authenticationGate.getSavedInput(message)
        if (knownAnswer != null && lastAskedConfirmationInput == null) {
          lastAskedConfirmationInput = knownAnswer
          return@askUserOnEdt knownAnswer
        }

        val answer = Messages.showYesNoDialog(project, message, ExternalProcessAuthHelperBundle.message("title.ssh.confirmation"), null)
        val textAnswer = when (answer) {
          Messages.YES -> "yes"
          Messages.NO -> "no"
          else -> throw AssertionError(answer)
        }

        authenticationGate.saveInput(message, textAnswer)
        return@askUserOnEdt textAnswer
      }
      return PromptAnswer.Answer(answer)
    }

    private fun stripYesNoSuffix(description: String): @NlsSafe String {
      return StringUtil.replace(description,
                                SSHUtil.CONFIRM_CONNECTION_PROMPT + " (yes/no)?",
                                SSHUtil.CONFIRM_CONNECTION_PROMPT + "?")
    }
  }

  private fun askGenericInput(description: @Nls String): String? {
    return askUserOnEdt {
      Messages.showPasswordDialog(project, description,
                                  ExternalProcessAuthHelperBundle.message("ssh.keyboard.interactive.title"),
                                  null)
    }
  }

  private fun askUserOnEdt(query: () -> String?): String? {
    if (authenticationMode != AuthenticationMode.FULL) return null

    return invokeAndWaitIfNeeded(ModalityState.any(), query)
  }

  private interface PromptHandler {
    fun handleInput(description: String): PromptAnswer
  }

  private sealed interface PromptAnswer {
    data object NotHandled : PromptAnswer
    data class Answer(val value: String?) : PromptAnswer
  }

  private data class Prompt(val askedKey: @NonNls String,
                            val promptMessage: @Nls String)
}
