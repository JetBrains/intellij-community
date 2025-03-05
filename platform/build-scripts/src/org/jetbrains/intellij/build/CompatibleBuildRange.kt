// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

enum class CompatibleBuildRange {
  EXACT,

  /**
   * A plugin will be compatible with IDE builds which number differs from plugin build number only in the last component,
   * i.e., plugins produced in 163.1111.22 build will be compatible with 163.1111.* builds.
   */
  RESTRICTED_TO_SAME_RELEASE,

  /**
   * A plugin will be compatible with newer IDE builds from the same baseline
   */
  NEWER_WITH_SAME_BASELINE,

  /**
   * A plugin will be compatible with all IDE builds from the same baseline, i.e., with 163.* builds.
   */
  ANY_WITH_SAME_BASELINE
}