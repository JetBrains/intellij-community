// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.model

internal class MultiLoaderWrapper(parent: ClassLoader, private val myDelegates: Collection<ClassLoader>) : ClassLoader(parent) {
  override fun findClass(name: String): Class<*> {
    for (delegate in myDelegates) {
      try {
        return Class.forName(name, false, delegate)
      }
      catch (ignore: ClassNotFoundException) {
        // try next one
      }
    }

    throw ClassNotFoundException(name)
  }
}
