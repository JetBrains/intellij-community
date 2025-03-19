// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.instanceContainer.internal

internal sealed interface RegistrationAction {
  class Register(val initializer: InstanceInitializer) : RegistrationAction
  class Override(val initializer: InstanceInitializer) : RegistrationAction
  data object Remove : RegistrationAction
}
