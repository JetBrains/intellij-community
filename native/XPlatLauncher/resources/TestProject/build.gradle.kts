// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption

plugins {
  id("java")
}

group = "com.intellij.idea"
version = "SNAPSHOT"

repositories {
  maven { url = URI("https://cache-redirector.jetbrains.com/plugins.gradle.org") }
  maven { url = URI("https://cache-redirector.jetbrains.com/maven-central") }
  maven { url = URI("https://cache-redirector.jetbrains.com/intellij-dependencies") }
}

dependencies {
  implementation("com.google.code.gson:gson:2.9.1")
  implementation("org.jetbrains.intellij.deps:async-profiler:2.9-15")
}

tasks.compileJava {
  dependsOn("downloadJbr")
  sourceCompatibility = "25"
  targetCompatibility = "25"
  val (os, _) = getOsAndArch()
  options.isFork = true
  options.forkOptions.javaHome = layout.buildDirectory.dir(if (os == "osx") ("jbr/Contents/Home") else "jbr").get().asFile
  options.compilerArgs = listOf("--add-modules=jdk.jcmd", "--add-exports=jdk.jcmd/sun.tools.jps=ALL-UNNAMED")
}

tasks.register<Jar>("fatJar") {
  description = "Building the test app"
  archiveFileName.set("app.jar")
  archiveClassifier.set("standalone")
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
  from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
  from(sourceSets.main.get().output)
}

val jbrSdkVersion: String by project
val jbrSdkBuildNumber: String by project

tasks.register<Task>("downloadJbr") {
  description = "Downloading Java runtime"

  val (os, arch) = getOsAndArch()
  val buildDir = layout.buildDirectory.asFile.get().toPath()
  val output = buildDir.resolve("jbr")

  onlyIf {
    val release = output.resolve("release")
    !Files.isRegularFile(release) || Files.lines(release).use { lines ->
      lines.noneMatch { it.startsWith("IMPLEMENTOR_VERSION=") && it.contains(jbrSdkVersion) && it.contains(jbrSdkBuildNumber) }
    }
  }

  doLast {
    val tmp = buildDir.resolve("tmp")
    Files.createDirectories(tmp)

    val file = Files.createTempFile(tmp, "jbr_", ".tgz")
    val uri = URI("https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-${jbrSdkVersion}-${os}-${arch}-b${jbrSdkBuildNumber}.tar.gz")
    uri.toURL().openStream().use { Files.copy(it, file, StandardCopyOption.REPLACE_EXISTING) }

    val dir = tmp.resolve(file.fileName.toString().replace(".tgz", ""))
    copy {
      from(tarTree(file))
      into(dir)
    }

    val content = Files.list(dir).use { it.toList() }
    val jbr = if (content.size == 1) content[0] else dir
    delete(output)
    Files.move(jbr, output)

    delete(file, dir)
  }
}

fun getOsAndArch(): Pair<String, String> {
  val osName = System.getProperty("os.name", "").lowercase()
  val os = when {
    osName.startsWith("windows") -> "windows"
    osName.startsWith("mac") -> "osx"
    osName.startsWith("linux") -> "linux"
    else -> throw UnsupportedOperationException("Unsupported OS: '${osName}'")
  }

  @Suppress("SpellCheckingInspection")
  val arch = when (val archName = System.getProperty("os.arch", "").lowercase()) {
    "x86_64", "amd64" -> "x64"
    "aarch64", "arm64" -> "aarch64"
    else -> throw UnsupportedOperationException("Unsupported architecture: '${archName}'")
  }

  return os to arch
}
