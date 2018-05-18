// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.passwordSafe

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.openapi.components.ServiceManager
import org.jetbrains.concurrency.Promise
import javax.swing.JCheckBox

abstract class PasswordSafe : PasswordStorage {
  companion object {
    @JvmStatic
    val instance: PasswordSafe
      get() = ServiceManager.getService(PasswordSafe::class.java)
  }

  /**
   * State of "Remember" check box is global. If user did uncheck for one dialog, it should be unchecked for another.
   * Use [RememberCheckBoxState.isSelected] to get initial value or use [RememberCheckBoxState.createCheckBox] to create check box.
   */
  abstract val rememberCheckBoxState: RememberCheckBoxState

  abstract val isMemoryOnly: Boolean

  abstract operator fun set(attributes: CredentialAttributes, credentials: Credentials?, memoryOnly: Boolean)

  abstract fun getAsync(attributes: CredentialAttributes): Promise<Credentials?>

  abstract fun isPasswordStoredOnlyInMemory(attributes: CredentialAttributes, credentials: Credentials): Boolean
}

interface RememberCheckBoxState {
  val isSelected: Boolean

  fun update(component: JCheckBox)

  fun createCheckBox(toolTip: String? = null): JCheckBox
}
