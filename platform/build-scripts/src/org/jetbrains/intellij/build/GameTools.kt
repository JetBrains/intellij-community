package org.jetbrains.intellij.build

import org.jetbrains.intellij.build.io.substituteTemplatePlaceholders
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class GameTools(private val context: BuildContext, private val os: OsFamily, private val arch: JvmArchitecture) {
  fun copyAdditionalFiles(targetDir: Path) {
    if (os == OsFamily.WINDOWS) {
      copyAdditionalFilesForWindows(targetDir)
    }

    if (os == OsFamily.LINUX) {
      copyAdditionalFilesForLinux(targetDir)
    }
  }

  private fun getClassPathJars() : List<String> {
    val classPaths = mutableListOf<String>()

    classPaths.addAll(context.bootClassPathJarNames.map { "lib/${it}" })

    // We add "plugins/android/lib/*" to the classpath so that "plugins/android/lib/game-tools.jar" will be on the
    // classpath. "plugins/android/lib/game-tools.jar" contains "AndroidGameDevelopmentToolsPlugin.xml" which defines
    // the game-tools plugin. See go/project-aplos-design for details.
    classPaths.add("plugins/android/lib/*")

    // We add these directories because "AndroidGameDevelopmentToolsPlugin.xml" references components from them.
    classPaths.addAll(listOf(
      "plugins/android/resources/*",
      "plugins/java/lib/java-api.jar",
      "plugins/java/lib/java-frontback.jar",
      "plugins/java/lib/java-impl.jar",
      "plugins/java/lib/resources.jar",
      "plugins/java/lib/java_resources_en.jar",
      "plugins/java/lib/modules/*"
    ))

    return classPaths
  }

  private fun getJavaArgs() : List<String> {
    val args = mutableListOf<String>()

    args.addAll(context.getAdditionalJvmArguments(os, arch, isScript = true))

    // We disable plugins ("-Didea.load.plugins=false") because AndroidGameDevelopmentToolsPlugin.xml directly
    // references the components (and not the plugins) to avoid including unused functionality (e.g. shift-shift to find
    // everything). See go/project-aplos-design for details.
    args.add("-Didea.load.plugins=false")

    // We disable the task-based UI because it doesn't work properly in standalone mode (b/338285051).
    args.add("-Dprofiler.task.based.ux=false")

    args.addAll(listOf(
      "-Didea.platform.prefix=AndroidGameDevelopmentTools",
      "-Didea.initially.ask.config=never"
    ))

    return args
  }

  private fun getTemplateArgs() : Map<String, String> {
    val map = mutableMapOf<String, String>()

    map["product_full"] = context.applicationInfo.fullProductName + " Game Tools"
    map["product_uc"] = context.productProperties.getEnvironmentVariableBaseName(context.applicationInfo)
    map["product_vendor"] = context.applicationInfo.shortCompanyName
    map["system_selector"] = "AndroidGameDevelopmentTools"
    map["ide_jvm_args"] = getJavaArgs().joinToString(" ")
    map["main_class_name"] = context.productProperties.mainClassName

    val classPathJars = getClassPathJars()

    if (os == OsFamily.WINDOWS) {
      var classPath = ""
      for (jar in classPathJars) {
        classPath += "\nECHO|SET /P=\"\"%IDE_HOME:\\=/%/${jar};\"\" >> \"%ARG_FILE%\""
      }

      map["class_path"] = classPath
      map["vm_options"] = "${context.productProperties.baseFileName}64.exe"
      map["base_name"] = "game_tools"
    }

    if (os == OsFamily.LINUX) {
      var classPath = $$"CLASS_PATH=\"$IDE_HOME/$${classPathJars[0]}\""
      for (i in 1 until classPathJars.size) {
        classPath += $$"\nCLASS_PATH=\"$CLASS_PATH:$IDE_HOME/$${classPathJars[i]}\""
      }

      map["class_path"] = classPath
      map["vm_options"] = context.productProperties.baseFileName
    }

    return map
  }

  private fun copyAdditionalFilesForWindows(targetDir: Path) {
    val scripts = context.paths.communityHomeDir.resolve("platform/build-scripts/resources/win/scripts")

    substituteTemplatePlaceholders(
      scripts.resolve("executable-template.bat"),
      targetDir.resolve("game-tools.bat"),
      "@@",
      getTemplateArgs().map { Pair(it.key, it.value) })

    Files.copy(
      scripts.resolve("profiler.bat"),
      targetDir.resolve("profiler.bat"),
      StandardCopyOption.REPLACE_EXISTING)

    Files.copy(
      context.paths.communityHomeDir.resolve("../../prebuilts/tools/windows/game-tools/GameToolsWinLauncher/ProfilerWinLauncher.exe"),
      targetDir.resolve("profiler.exe"),
      StandardCopyOption.REPLACE_EXISTING)
  }

  private fun copyAdditionalFilesForLinux(targetDir: Path) {
    val scripts = context.paths.communityHomeDir.resolve("platform/build-scripts/resources/linux/scripts")

    substituteTemplatePlaceholders(
      scripts.resolve("executable-template.sh"),
      targetDir.resolve("game-tools.sh"),
      "__",
      getTemplateArgs().map { Pair(it.key, it.value) })

    Files.copy(
      scripts.resolve("profiler.sh"),
      targetDir.resolve("profiler.sh"),
      StandardCopyOption.REPLACE_EXISTING)
  }
}