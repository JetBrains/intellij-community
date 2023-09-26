// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.externalProcessAuthHelper

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.askPassword
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ssh.SSHUtil
import com.intellij.util.PathUtil
import externalApp.nativessh.NativeSshAskPassAppHandler
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

class NativeSshGuiAuthenticator(private val project: Project,
                                private val authenticationGate: AuthenticationGate,
                                private val authenticationMode: AuthenticationMode,
                                private val doNotRememberPasswords: Boolean) : NativeSshAskPassAppHandler {

  private val passwordHandlers = listOf(
    KeyPassphrasePromptHandler(),
    SshPasswordPromptHandler(),
    ConfirmationPromptHandler()
  )

  override fun handleInput(description: @NlsSafe String): String? {
    if (authenticationMode == AuthenticationMode.NONE) {
      return null
    }
    return authenticationGate.waitAndCompute { doHandleInput(description) }
  }

  private fun doHandleInput(description: @NlsSafe String): String? {
    for (passwordHandler in passwordHandlers) {
      val answer = passwordHandler.handleInput(description)
      if (answer is PromptAnswer.Answer) {
        return answer.value
      }
    }

    return askGenericInput(description)
  }

  private inner class KeyPassphrasePromptHandler : PromptHandler {
    private var lastAskedKeyPath: String? = null

    override fun handleInput(description: String): PromptAnswer {
      if (isKeyPassphrase(description)) {
        val answer = askKeyPassphraseInput(description)
        return PromptAnswer.Answer(answer)
      }
      return PromptAnswer.NotHandled
    }

    private fun isKeyPassphrase(description: String): Boolean {
      return SSHUtil.PASSPHRASE_PROMPT.matcher(description).matches()
    }

    private fun askKeyPassphraseInput(description: String): String? {
      val matcher = SSHUtil.PASSPHRASE_PROMPT.matcher(description)
      check(matcher.matches()) { description }
      val keyPath = SSHUtil.extractKeyPath(matcher)
      val resetPassword = keyPath == lastAskedKeyPath
      lastAskedKeyPath = keyPath

      if (doNotRememberPasswords) {
        return askUserOnEdt {
          val message = ExternalProcessAuthHelperBundle.message("ssh.ask.passphrase.message", PathUtil.getFileName(keyPath))
          Messages.showPasswordDialog(project, message,
                                      ExternalProcessAuthHelperBundle.message("ssh.ask.passphrase.title"), null)
        }
      }
      else {
        return askPassphrase(project, keyPath, resetPassword, authenticationMode)
      }
    }

    private fun askPassphrase(project: Project?,
                              keyPath: @NlsSafe String,
                              resetPassword: Boolean,
                              authenticationMode: AuthenticationMode): @NonNls String? {
      if (authenticationMode == AuthenticationMode.NONE) return null
      val newAttributes = passphraseCredentialAttributes(keyPath)
      val credentials = PasswordSafe.instance.get(newAttributes)
      if (credentials != null && !resetPassword) {
        val password = credentials.getPasswordAsString()
        if (!password.isNullOrEmpty()) return password
      }
      if (authenticationMode == AuthenticationMode.SILENT) return null
      return askPassword(project,
                         ExternalProcessAuthHelperBundle.message("ssh.ask.passphrase.title"),
                         ExternalProcessAuthHelperBundle.message("ssh.ask.passphrase.message",
                                                                 PathUtil.getFileName(keyPath)),
                         newAttributes, true)
    }

    private fun passphraseCredentialAttributes(key: @Nls String): CredentialAttributes {
      val serviceName = generateServiceName(ExternalProcessAuthHelperBundle.message("label.credential.store.key.ssh.passphrase"), key)
      return CredentialAttributes(serviceName, key)
    }
  }

  private inner class SshPasswordPromptHandler : PromptHandler {
    private var lastAskedUserName: String? = null

    override fun handleInput(description: String): PromptAnswer {
      if (isSshPassword(description)) {
        val answer = askSshPasswordInput(description)
        return PromptAnswer.Answer(answer)
      }
      return PromptAnswer.NotHandled
    }

    private fun isSshPassword(description: String): Boolean {
      return SSHUtil.PASSWORD_PROMPT.matcher(description).matches()
    }

    private fun askSshPasswordInput(description: String): String? {
      val matcher = SSHUtil.PASSWORD_PROMPT.matcher(description)
      check(matcher.matches()) { description }
      val username = SSHUtil.extractUsername(matcher)
      val resetPassword = username == lastAskedUserName
      lastAskedUserName = username

      if (doNotRememberPasswords) {
        return askUserOnEdt {
          val message = ExternalProcessAuthHelperBundle.message("ssh.password.message", username)
          Messages.showPasswordDialog(project, message,
                                      ExternalProcessAuthHelperBundle.message("ssh.password.title"), null)
        }
      }
      else {
        return askPassword(project, username, resetPassword, authenticationMode)
      }
    }

    private fun askPassword(project: Project?,
                            username: @NlsSafe String,
                            resetPassword: Boolean,
                            authenticationMode: AuthenticationMode): @NonNls String? {
      if (authenticationMode == AuthenticationMode.NONE) return null
      val newAttributes = passwordCredentialAttributes(username)
      val credentials = PasswordSafe.instance.get(newAttributes)
      if (credentials != null && !resetPassword) {
        val password = credentials.getPasswordAsString()
        if (password != null) return password
      }
      if (authenticationMode == AuthenticationMode.SILENT) return null
      return askPassword(project,
                         ExternalProcessAuthHelperBundle.message("ssh.password.title"),
                         ExternalProcessAuthHelperBundle.message("ssh.password.message", username),
                         newAttributes, true)
    }

    private fun passwordCredentialAttributes(key: @Nls String): CredentialAttributes {
      val serviceName = generateServiceName(ExternalProcessAuthHelperBundle.message("label.credential.store.key.ssh.password"), key)
      return CredentialAttributes(serviceName, key)
    }
  }

  private inner class ConfirmationPromptHandler : PromptHandler {
    private var lastAskedConfirmationInput: String? = null

    override fun handleInput(description: String): PromptAnswer {
      if (isConfirmation(description)) {
        val answer = askConfirmationInput(description)
        return PromptAnswer.Answer(answer)
      }
      return PromptAnswer.NotHandled
    }

    private fun isConfirmation(description: @NlsSafe String): Boolean {
      return description.contains(SSHUtil.CONFIRM_CONNECTION_PROMPT)
    }

    private fun askConfirmationInput(description: @NlsSafe String): String? {
      return askUserOnEdt {
        val message: @NlsSafe String = StringUtil.replace(description,
                                                          SSHUtil.CONFIRM_CONNECTION_PROMPT + " (yes/no)?",
                                                          SSHUtil.CONFIRM_CONNECTION_PROMPT + "?")

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
