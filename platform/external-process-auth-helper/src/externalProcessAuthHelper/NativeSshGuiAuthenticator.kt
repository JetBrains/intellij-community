// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.externalProcessAuthHelper

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.askPassword
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ssh.SSHUtil
import com.intellij.util.PathUtil
import externalApp.nativessh.NativeSshAskPassAppHandler
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

class NativeSshGuiAuthenticator(private val myProject: Project,
                                private val myAuthenticationGate: AuthenticationGate,
                                private val myAuthenticationMode: AuthenticationMode,
                                private val myDoNotRememberPasswords: Boolean) : NativeSshAskPassAppHandler {
  private var myLastAskedKeyPath: String? = null
  private var myLastAskedUserName: String? = null
  private var myLastAskedConfirmationInput: String? = null

  override fun handleInput(description: @NlsSafe String): String? {
    if (myAuthenticationMode == AuthenticationMode.NONE) return null
    return myAuthenticationGate.waitAndCompute {
      when {
        isKeyPassphrase(description) -> askKeyPassphraseInput(description)
        isSshPassword(description) -> askSshPasswordInput(description)
        isConfirmation(description) -> askConfirmationInput(description)
        else -> askGenericInput(description)
      }
    }
  }

  private fun askKeyPassphraseInput(description: String): String? {
    val matcher = SSHUtil.PASSPHRASE_PROMPT.matcher(description)
    check(matcher.matches()) { description }
    val keyPath = SSHUtil.extractKeyPath(matcher)
    val resetPassword = keyPath == myLastAskedKeyPath
    myLastAskedKeyPath = keyPath

    if (myDoNotRememberPasswords) {
      return askUser {
        val message = ExternalProcessAuthHelperBundle.message("ssh.ask.passphrase.message", PathUtil.getFileName(keyPath))
        Messages.showPasswordDialog(myProject, message,
                                    ExternalProcessAuthHelperBundle.message("ssh.ask.passphrase.title"), null)
      }
    }
    else {
      return askPassphrase(myProject, keyPath, resetPassword, myAuthenticationMode)
    }
  }

  private fun askSshPasswordInput(description: String): String? {
    val matcher = SSHUtil.PASSWORD_PROMPT.matcher(description)
    check(matcher.matches()) { description }
    val username = SSHUtil.extractUsername(matcher)
    val resetPassword = username == myLastAskedUserName
    myLastAskedUserName = username

    if (myDoNotRememberPasswords) {
      return askUser {
        val message = ExternalProcessAuthHelperBundle.message("ssh.password.message", username)
        Messages.showPasswordDialog(myProject, message,
                                    ExternalProcessAuthHelperBundle.message("ssh.password.title"), null)
      }
    }
    else {
      return askPassword(myProject, username, resetPassword, myAuthenticationMode)
    }
  }

  private fun askConfirmationInput(description: @NlsSafe String): String? {
    return askUser {
      val message: @NlsSafe String = StringUtil.replace(description,
                                                        SSHUtil.CONFIRM_CONNECTION_PROMPT + " (yes/no)?",
                                                        SSHUtil.CONFIRM_CONNECTION_PROMPT + "?")

      val knownAnswer = myAuthenticationGate.getSavedInput(message)
      if (knownAnswer != null && myLastAskedConfirmationInput == null) {
        myLastAskedConfirmationInput = knownAnswer
        return@askUser knownAnswer
      }

      val answer = Messages.showYesNoDialog(myProject, message, ExternalProcessAuthHelperBundle.message("title.ssh.confirmation"), null)
      val textAnswer = when (answer) {
        Messages.YES -> "yes"
        Messages.NO -> "no"
        else -> throw AssertionError(answer)
      }

      myAuthenticationGate.saveInput(message, textAnswer)
      return@askUser textAnswer
    }
  }

  private fun askGenericInput(description: @Nls String): String? {
    return askUser {
      Messages.showPasswordDialog(myProject, description,
                                  ExternalProcessAuthHelperBundle.message("ssh.keyboard.interactive.title"),
                                  null)
    }
  }

  private fun askUser(query: Computable<String?>): String? {
    if (myAuthenticationMode != AuthenticationMode.FULL) return null

    val answerRef = Ref<String>()
    ApplicationManager.getApplication().invokeAndWait({ answerRef.set(query.compute()) }, ModalityState.any())
    return answerRef.get()
  }

  companion object {
    private fun isKeyPassphrase(description: String): Boolean {
      return SSHUtil.PASSPHRASE_PROMPT.matcher(description).matches()
    }

    private fun isSshPassword(description: String): Boolean {
      return SSHUtil.PASSWORD_PROMPT.matcher(description).matches()
    }

    private fun isConfirmation(description: @NlsSafe String): Boolean {
      return description.contains(SSHUtil.CONFIRM_CONNECTION_PROMPT)
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

    private fun passphraseCredentialAttributes(key: @Nls String): CredentialAttributes {
      val serviceName = generateServiceName(ExternalProcessAuthHelperBundle.message("label.credential.store.key.ssh.passphrase"), key)
      return CredentialAttributes(serviceName, key)
    }

    private fun passwordCredentialAttributes(key: @Nls String): CredentialAttributes {
      val serviceName = generateServiceName(ExternalProcessAuthHelperBundle.message("label.credential.store.key.ssh.password"), key)
      return CredentialAttributes(serviceName, key)
    }
  }
}
