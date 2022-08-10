plugins {
    id("java")
    id("me.filippov.gradle.jvm.wrapper")
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

val jbrsdkVersion: String by project
val jbrsdkBuildNumber: String by project

jvmWrapper {
    winJvmInstallDir = "gradle-jvm"
    unixJvmInstallDir = "gradle-jvm"
    linuxAarch64JvmUrl = "https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-$jbrsdkVersion-linux-aarch64-b$jbrsdkBuildNumber.tar.gz"
    linuxX64JvmUrl = "https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-$jbrsdkVersion-linux-x64-b$jbrsdkBuildNumber.tar.gz"
    macAarch64JvmUrl = "https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-$jbrsdkVersion-osx-aarch64-b$jbrsdkBuildNumber.tar.gz"
    macX64JvmUrl = "https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-$jbrsdkVersion-osx-x64-b$jbrsdkBuildNumber.tar.gz"
    windowsX64JvmUrl = "https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-$jbrsdkVersion-windows-x64-b$jbrsdkBuildNumber.tar.gz"
}
