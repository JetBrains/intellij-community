import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption

plugins {
    id("java")
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

tasks.compileJava {
  @Suppress("SpellCheckingInspection")
  options.compilerArgs = listOf("-source", "17", "-target", "17", "--add-modules=jdk.jcmd", "--add-exports=jdk.jcmd/sun.tools.jps=ALL-UNNAMED")
}

task("fatJar", type = Jar::class) {
  dependsOn.addAll(listOf("compileJava", "processResources")) // We need this for Gradle optimization to work

  archiveFileName.set("app.jar")
  archiveClassifier.set("standalone") // Naming the .jar file

  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
  val contents =
    configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) } +
    sourceSets.main.get().output
  from(contents)
}

val jbrSdkVersion: String by project
val jbrSdkBuildNumber: String by project

task("downloadJbr") {
  val (os, arch) = getOsAndArch()
  val buildDir = project.layout.buildDirectory.asFile.get().toPath()
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
  val os = if (osName.startsWith("windows")) "windows"
    else if (osName.startsWith("mac")) "osx"
    else if (osName.startsWith("linux")) "linux"
    else throw UnsupportedOperationException("Unsupported OS: '${osName}'")

  @Suppress("SpellCheckingInspection")
  val arch = when (val archName = System.getProperty("os.arch", "").lowercase()) {
    "x86_64", "amd64" -> "x64"
    "aarch64", "arm64" -> "aarch64"
    else -> throw UnsupportedOperationException("Unsupported architecture: '${archName}'")
  }

  return os to arch
}
