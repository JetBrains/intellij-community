// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import groovy.transform.CompileStatic

@CompileStatic
final class OpenedPackages implements Iterable<String> {
  private static final List<String> OPENED_PACKAGES = List.of(
    '--add-opens=java.base/java.lang=ALL-UNNAMED',
    '--add-opens=java.base/java.text=ALL-UNNAMED',
    '--add-opens=java.base/java.time=ALL-UNNAMED',
    '--add-opens=java.base/java.util=ALL-UNNAMED',
    '--add-opens=java.base/java.util.concurrent=ALL-UNNAMED',
    '--add-opens=java.base/java.io=ALL-UNNAMED',
    '--add-opens=java.base/java.net=ALL-UNNAMED',
    '--add-opens=java.base/jdk.internal.vm=ALL-UNNAMED',
    '--add-opens=java.base/sun.nio.ch=ALL-UNNAMED',
    '--add-opens=java.base/java.nio.charset=ALL-UNNAMED',
    '--add-opens=java.desktop/java.awt=ALL-UNNAMED',
    '--add-opens=java.desktop/java.awt.event=ALL-UNNAMED',
    '--add-opens=java.desktop/java.awt.image=ALL-UNNAMED',
    '--add-opens=java.desktop/java.awt.peer=ALL-UNNAMED',
    '--add-opens=java.desktop/javax.swing=ALL-UNNAMED',
    '--add-opens=java.desktop/javax.swing.plaf.basic=ALL-UNNAMED',
    '--add-opens=java.desktop/javax.swing.text.html=ALL-UNNAMED',
    '--add-opens=java.desktop/sun.awt=ALL-UNNAMED',
    '--add-opens=java.desktop/sun.awt.image=ALL-UNNAMED',
    '--add-opens=java.desktop/sun.awt.windows=ALL-UNNAMED',
    '--add-opens=java.desktop/sun.font=ALL-UNNAMED',
    '--add-opens=java.desktop/sun.java2d=ALL-UNNAMED',
    '--add-opens=java.desktop/sun.lwawt=ALL-UNNAMED',
    '--add-opens=java.desktop/sun.lwawt.macosx=ALL-UNNAMED',
    '--add-opens=java.desktop/sun.swing=ALL-UNNAMED',
    '--add-opens=java.desktop/com.apple.eawt=ALL-UNNAMED',
    '--add-opens=java.desktop/com.apple.eawt.event=ALL-UNNAMED',
    '--add-opens=java.desktop/com.apple.laf=ALL-UNNAMED',
    '--add-opens=jdk.attach/sun.tools.attach=ALL-UNNAMED',
    '--add-opens=jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED',
    '--add-opens=jdk.jdi/com.sun.tools.jdi=ALL-UNNAMED'
  )

  static final OpenedPackages INSTANCE = new OpenedPackages()

  private OpenedPackages() {}

  @Override
  Iterator<String> iterator() {
    return OPENED_PACKAGES.iterator()
  }
}
