// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import groovy.transform.CompileStatic

import java.nio.file.Path

@CompileStatic
final class PluginRepositorySpec {
  final Path pluginZip
  // content of plugin.xml
  final byte[] pluginXml

  PluginRepositorySpec(Path pluginZip, byte[] pluginXml) {
    this.pluginZip = pluginZip
    this.pluginXml = pluginXml
  }
}