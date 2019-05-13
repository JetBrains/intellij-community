// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.model

import com.intellij.serialization.BeanConstructed
import com.intellij.serialization.ReadConfiguration
import com.intellij.serialization.WriteConfiguration

val externalSystemBeanConstructed: BeanConstructed = {
  if (it is ProjectSystemId) {
    it.intern()
  }
  else {
    it
  }
}

fun createDataNodeReadConfiguration(classLoader: ClassLoader): ReadConfiguration {
  return ReadConfiguration(allowAnySubTypes = true, classLoader = classLoader, beanConstructed = externalSystemBeanConstructed)
}

val dataNodeWriteConfiguration = WriteConfiguration(allowAnySubTypes = true)