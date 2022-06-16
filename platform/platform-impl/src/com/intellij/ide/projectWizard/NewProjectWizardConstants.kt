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
    const val SBT = "SBT"

    val ALL = arrayOf(INTELLIJ, GRADLE, MAVEN, SBT)
  }

  object Generators {
    const val EMPTY_PROJECT = "empty-project"
    const val EMPTY_WEB_PROJECT = "empty-web-project"
    const val SIMPLE_PROJECT = "simple-project"
    const val SIMPLE_MODULE = "simple-module"

    const val ALL_REACT = "AllReact"
    const val ALL_HTML5 = "AllHTML5"
    const val DJANGO = "django"
    const val FAST_API = "FastApi"
    const val FLASK = "Flask"
    const val MAVEN_ARCHETYPE = "MavenArchetype"

    const val ANGULAR_CLI = "AngularCLI"
    const val COMPOSER = "Composer"
    const val CORDOVA = "Cordova"
    const val DART = "Dart"
    const val DRUPAL = "Drupal"
    const val EXPRESS_APP = "ExpressApp"
    const val HTML5 = "HTML5"
    const val JOOMLA = "Joomla"
    const val METEOR = "Meteor"
    const val NODE_JS = "NodeJS"
    const val PHP = "PHP"
    const val PHP_STORM_WORKSHOP = "PhpStormWorkshop"
    const val R_PROJECT = "R_PROJECT"
    const val R_PACKAGE = "R_PACKAGE"
    const val REACT = "React"
    const val REACT_NATIVE = "ReactNative"
    const val TWITTER = "TwitterBootstrap"
    const val VUE_JS = "VueJS"
    const val WORD_PRESS = "WordPress"

    const val ANDROID = "Android"
    const val CLOUD = "Cloud"
    const val COMPOSE = "ComposeModuleBuilder"
    const val GAUGE = "gauge"
    const val GRAILS_FORGE = "GrailsForge"
    const val GRAILS = "Grails"
    const val GROOVY = "groovy"
    const val HELIDON = "helidon"
    const val IDEA_PLUGIN = "idea-plugin"
    const val JAVAEE = "javaee"
    const val JAVAFX = "javafx"
    const val KTOR = "KtorStarter"
    const val MICRONAUT = "micronaut"
    const val QUARKUS = "quarkus-jetbrains"
    const val PUPPET = "Puppet"
    const val RUBY_GEM = "RubyGem"
    const val RUBY_ON_RAILS = "RubyOnRails"
    const val SELENIUM = "selenium"
    const val SPRING_INITIALIZR = "SpringInitializr"
    const val YEOMAN = "Yeoman"

    const val FLASH = "Flex"
    const val KOTLIN_MPP = "kotlin.newProjectWizard.builder"

    const val LEGACY_DART = "DartModuleBuilder"
    const val LEGACY_GRADLE = "InternalGradleModuleBuilder"
    const val LEGACY_MAVEN = "InternalMavenModuleBuilder"
    const val LEGACY_JAVA_EE = "LegacyJavaEE"
    const val LEGACY_SPRING = "LegacySpring"

    val ALL = arrayOf(
      EMPTY_PROJECT, EMPTY_WEB_PROJECT, SIMPLE_PROJECT, SIMPLE_MODULE,

      ALL_REACT, ALL_HTML5, DJANGO, FAST_API, FLASK, MAVEN_ARCHETYPE,

      ANGULAR_CLI, COMPOSER, CORDOVA, DART, DRUPAL, EXPRESS_APP, HTML5, JOOMLA, METEOR, NODE_JS, PHP, PHP_STORM_WORKSHOP, R_PROJECT,
      R_PACKAGE, REACT, REACT_NATIVE, TWITTER, VUE_JS, WORD_PRESS,

      ANDROID, CLOUD, COMPOSE, GAUGE, GRAILS_FORGE, GRAILS, GROOVY, HELIDON, IDEA_PLUGIN, JAVAEE, JAVAFX, KTOR, MICRONAUT, QUARKUS, PUPPET,
      RUBY_GEM, RUBY_ON_RAILS, SELENIUM, SPRING_INITIALIZR, YEOMAN,

      FLASH, KOTLIN_MPP,

      LEGACY_DART, LEGACY_GRADLE, LEGACY_MAVEN, LEGACY_JAVA_EE, LEGACY_SPRING
    )
  }

  const val OTHER = "other"
}