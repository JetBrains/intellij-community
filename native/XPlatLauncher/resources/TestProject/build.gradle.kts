plugins {
    id("java")
    id("me.filippov.gradle.jvm.wrapper") version "0.11.0"
}

group = "com.intellij.idea"
version = "SNAPSHOT"

repositories {
    mavenCentral()
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "com.intellij.idea.Main"
    }
    archiveFileName.set("app.jar")
}

// TODO: gradle.properties
jvmWrapper {
    winJvmInstallDir = "gradle-jvm"
    unixJvmInstallDir = "gradle-jvm"
    linuxAarch64JvmUrl = "https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-17.0.3-linux-aarch64-b469.37.tar.gz"
    linuxX64JvmUrl = "https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-17.0.3-linux-x64-b469.37.tar.gz"
    macAarch64JvmUrl = "https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-17.0.3-osx-aarch64-b469.37.tar.gz"
    macX64JvmUrl = "https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-17.0.3-osx-x64-b469.37.tar.gz"
    windowsX64JvmUrl = "https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-17.0.3-windows-x64-b469.37.tar.gz"
}
