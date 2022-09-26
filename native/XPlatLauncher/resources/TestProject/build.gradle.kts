import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java")
    id("me.filippov.gradle.jvm.wrapper")
    kotlin("jvm") version "1.7.10"
}

group = "com.intellij.idea"
version = "SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
  implementation("com.google.code.gson", "gson", "2.9.1")
  implementation(kotlin("stdlib-jdk8"))
}

val fatJar = task("fatJar", type = Jar::class) {
  dependsOn.addAll(listOf("compileJava", "compileKotlin", "processResources")) // We need this for Gradle optimization to work
  archiveClassifier.set("standalone") // Naming the jar
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
  archiveFileName.set("app.jar")
  manifest {
    attributes["Main-Class"] = "com.intellij.idea.Main"
  }
  val sourcesMain = sourceSets.main.get()
  val contents = configurations.runtimeClasspath.get()
    .map { if (it.isDirectory) it else zipTree(it) } +
    sourcesMain.output
  from(contents)
}

val jbrsdkVersion: String by project
val jbrsdkBuildNumber: String by project

jvmWrapper {
    winJvmInstallDir = "gradle-jvm"
    unixJvmInstallDir = "gradle-jvm"
    linuxAarch64JvmUrl = "https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-$jbrsdkVersion-linux-aarch64-b$jbrsdkBuildNumber.tar.gz"
    linuxX64JvmUrl = "https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-$jbrsdkVersion-linux-x64-b$jbrsdkBuildNumber.tar.gz"
    macAarch64JvmUrl = "https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-$jbrsdkVersion-osx-aarch64-b$jbrsdkBuildNumber.tar.gz"
    macX64JvmUrl = "https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-$jbrsdkVersion-osx-x64-b$jbrsdkBuildNumber.tar.gz"
    windowsX64JvmUrl = "https://download.oracle.com/java/18/archive/jdk-18.0.1.1_windows-x64_bin.zip"
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
  jvmTarget = "1.8"
}
val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
  jvmTarget = "1.8"
}