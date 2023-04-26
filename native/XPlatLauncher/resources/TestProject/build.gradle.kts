import java.net.URI

plugins {
    id("java")
    id("me.filippov.gradle.jvm.wrapper")
}

group = "com.intellij.idea"
version = "SNAPSHOT"

repositories {
  mavenCentral()
  maven { url = URI("https://cache-redirector.jetbrains.com/intellij-dependencies") }
}

dependencies {
  implementation("com.google.code.gson", "gson", "2.9.1")
  implementation("org.jetbrains.intellij.deps", "async-profiler", "2.9-15")
}

val fatJar = task("fatJar", type = Jar::class) {
  dependsOn.addAll(listOf("compileJava", "processResources")) // We need this for Gradle optimization to work

  archiveFileName.set("app.jar")
  archiveClassifier.set("standalone") // Naming the .jar file

  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
  val contents =
    configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) } +
    sourceSets.main.get().output
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
