// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.compilation

import com.google.gson.annotations.SerializedName
import groovy.transform.CompileStatic

/**
 * Configuration on which compilation parts to download and from where.
 * <br/>
 * URL for each part should be constructed like: <pre>${serverUrl}/${prefix}/${files.key}/${files.value}.jar</pre>
 */
@CompileStatic
final class CompilationPartsMetadata {
  @SerializedName('server-url')
  String serverUrl
  String branch
  String prefix
  /**
   * Map compilation part path to a hash, for now SHA-256 is used.
   * sha256(file) == hash, though that may be changed in the future.
   */
  Map<String, String> files

  CompilationPartsMetadata() {
  }
}
