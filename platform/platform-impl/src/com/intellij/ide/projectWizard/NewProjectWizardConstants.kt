// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectWizard

object NewProjectWizardConstants {
  object Language {
    const val JAVA = "Java"
    const val KOTLIN = "Kotlin"
    const val GROOVY = "Groovy"
    const val JAVASCRIPT = "JavaScript"
    const val HTML = "HTML"
    const val PYTHON = "Python"
    const val PHP = "PHP"
    const val RUBY = "Ruby"
    const val GO = "Go"
    const val SCALA = "Scala"

    val ALL = arrayOf(JAVA, KOTLIN, GROOVY, JAVASCRIPT, HTML, PYTHON, PHP, RUBY, GO, SCALA)
    val ALL_DSL = arrayOf(KOTLIN, GROOVY)
  }

  object BuildSystem {
    const val INTELLIJ = "IntelliJ"
    const val GRADLE = "Gradle"
    const val MAVEN = "Maven"
    private const val SBT = "SBT"

    val ALL = arrayOf(INTELLIJ, GRADLE, MAVEN, SBT)
  }

  object Generators {
    const val EMPTY_PROJECT = "empty-project"
    const val EMPTY_WEB_PROJECT = "empty-web-project"
    const val SIMPLE_PROJECT = "simple-project"
    const val SIMPLE_MODULE = "simple-module"
  }

  const val OTHER = "other"
  const val NULL = "null"
}