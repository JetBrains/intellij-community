// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.retry

import groovy.transform.CompileStatic

@CompileStatic
class StopTrying extends Exception {
  StopTrying(Exception cause) {
    super(cause)
  }
}