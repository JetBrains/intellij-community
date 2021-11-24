// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.compilation

import com.intellij.openapi.diagnostic.Logger
import groovy.transform.CompileStatic
import org.jetbrains.annotations.Nullable

@CompileStatic
final class CloseStreamUtil {
  static void closeStream(@Nullable Closeable stream) {
    if (stream != null) {
      try {
        stream.close();
      }
      catch (IOException var2) {
        Logger.getInstance(CloseStreamUtil.class).error(var2);
      }
    }
  }
}

