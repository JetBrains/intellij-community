// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/**
 * To publish a new mock to repository, create gradle.properties file and define "spaceUsername" and "spacePassword"
 * properties. The created mockJDK version is the same as Gradle bootstrap JDK, so carefully set it.
 * 
 * Use `gradle clean build` to ensure that proper lib is created under build/libs
 * Use `gradle clean publish` to publish
 */

import java.nio.file.Files

plugins {
  id("java")
  `maven-publish`
}
val spaceUsername: String by project
val spacePassword: String by project

val javaVersion: String = Runtime.version().feature().toString()

val jmodDir = project.file("$buildDir/jmod")

tasks.register("ensureDirectory") {
  doLast {
    Files.createDirectories(jmodDir.toPath())
  }
}

task<Exec>("jmodUnpack") {
  println("Preparing mockJDK-$javaVersion")
  dependsOn("ensureDirectory")
  workingDir = jmodDir
  val javaHome = System.getProperty("java.home")
  commandLine("$javaHome/bin/jmod", "extract", "$javaHome/jmods/java.base.jmod")
}

task<Copy>("jmodCopy") {
  dependsOn("jmodUnpack")
  from("$jmodDir/classes") {
    include("java/**")
    include("module-info.class")
  }
  into(layout.buildDirectory.dir("resources"))
}

tasks {
  withType<JavaCompile>() {
    dependsOn("jmodCopy")
  }
  withType<Jar>() {
    from("$buildDir/resources")
  }
}

publishing {
  publications {
    create<MavenPublication>("maven") {
      groupId = "org.jetbrains.mockjdk"
      artifactId = "mockjdk-base-java"
      version = "${javaVersion}.0"
      from(components["java"])
      pom {
        licenses {
          license { 
            name = "GNU General Public License, version 2, with the Classpath Exception"
            url = "https://openjdk.org/legal/gplv2+ce.html"
          }
        }
      }
    }
  }
  repositories {
    maven {
      url = uri("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
      credentials {
        username = spaceUsername
        password = spacePassword
      }
    }
  }
}
