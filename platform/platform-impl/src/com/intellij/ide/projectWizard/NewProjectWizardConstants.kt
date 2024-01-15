// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectWizard

object NewProjectWizardConstants {
  object Language {
    const val JAVA: String = "Java"
    const val KOTLIN: String = "Kotlin"
    const val GROOVY: String = "Groovy"
    const val PYTHON: String = "Python"
    const val PHP: String = "PHP"
    const val RUBY: String = "Ruby"
    const val GO: String = "Go"
    const val SCALA: String = "Scala"
    const val RUST: String = "Rust"
  }

  object BuildSystem {
    const val INTELLIJ: String = "IntelliJ"
    const val GRADLE: String = "Gradle"
    const val MAVEN: String = "Maven"
    const val SBT: String = "sbt"
    const val AMPER: String = "Amper"
  }

  object Generators {
    const val EMPTY_PROJECT: String = "empty-project"
    const val EMPTY_WEB_PROJECT: String = "empty-web-project"
    const val SIMPLE_PROJECT: String = "simple-project"
    const val SIMPLE_MODULE: String = "simple-module"
  }

  object GroovySdk {
    const val MAVEN: String = "Maven"
    const val LOCAL: String = "Local"
    const val NONE: String = "None"
  }

  const val OTHER: String = "other"
}