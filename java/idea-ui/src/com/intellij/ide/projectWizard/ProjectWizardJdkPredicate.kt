// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectWizard

import com.intellij.java.JavaBundle
import com.intellij.util.lang.JavaVersion
import org.jetbrains.annotations.Nls

fun interface ProjectWizardJdkPredicate {

  /**
   * @param version the version to test
   * @param name the name of the tested JDK
   * @return null if the test passes, an error message else
   */
  fun getError(version: JavaVersion, name: String?): @Nls String?

  abstract class IsAtLeast(val version: Int) : ProjectWizardJdkPredicate {
    override fun getError(version: JavaVersion, name: String?): @Nls String? {
      if (version.isAtLeast(this.version)) return null
      return errorMessage(version, name)
    }

    abstract fun errorMessage(version: JavaVersion, name: String?): @Nls String
  }

  class IsJdkSupported(): IsAtLeast(8) {
    override fun errorMessage(version: JavaVersion, name: String?): @Nls String {
      return JavaBundle.message("unsupported.jdk.notification.content", name)
    }
  }

  companion object {
    fun ProjectWizardJdkPredicate.getError(versionString: String, name: String?): @Nls String? {
      val version = JavaVersion.tryParse(versionString) ?: return null
      return getError(version, name)
    }
  }
}