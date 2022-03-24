// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import groovy.transform.CompileStatic

@CompileStatic
enum JetBrainsRuntimeDistribution {
  /**
   * JBR with JCEF
   */
  JCEF('jcef'),

  /**
   * JBR with DCEVM and JCEF
   */
  DCEVM('dcevm'),

  /**
   * JBR without JCEF and DCEVM
   */
  LIGHTWEIGHT('nomod'),

  /**
   * Distinguishes artifacts of different JBR distributions
   */
  final String classifier

  JetBrainsRuntimeDistribution(String classifier) {
    this.classifier = classifier
  }
}
