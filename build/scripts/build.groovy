/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.util

import org.jetbrains.jps.LayoutInfo
import org.jetbrains.jps.gant.JpsGantProjectBuilder
import org.jetbrains.jps.gant.JpsGantTool
import static org.jetbrains.jps.idea.IdeaProjectLoader.guessHome


class Paths {
  final projectHome
  final buildDir
  final sandbox
  final classesTarget
  final distWin
  final distWinZip
  final distAll
  final distJars
  final distUnix
  final distMac
  final distDev
  final artifacts
  final artifacts_core_upsource
  final ideaSystem
  final ideaConfig
  def jdkHome

  def Paths(String home) {
    projectHome = new File(home).getCanonicalPath()
    buildDir = "$projectHome/build"
    sandbox = "$projectHome/out/release"

    classesTarget = "$sandbox/classes"
    distWin = "$sandbox/dist.win"
    distWinZip = "$sandbox/dist.win.zip"
    distAll = "$sandbox/dist.all"
    distJars = "$sandbox/dist.jars"
    distUnix = "$sandbox/dist.unix"
    distMac = "$sandbox/dist.mac"
    distDev = "$sandbox/dist.dev"
    artifacts = "$sandbox/artifacts"

    artifacts_core_upsource = "$artifacts/core-upsource"

    ideaSystem = "$sandbox/system"
    ideaConfig = "$sandbox/config"
  }
}

class Steps {
  def clear = true
  def zipSources = true
  def compile = true
  def scramble = true
  def layout = true
  def build_searchable_options = true
  def zipwin = true
  def targz = true
  def dmg = true
  def sit = true
}

class Build {
  def buildName
  def product
  def productCode  
  def modules
  def steps
  def paths
  def home
  def projectBuilder
  def buildNumber
  def ant = new AntBuilder()
  Map layout_args
  Script utils
  Script ultimate_utils
  Script layouts
  Script libLicenses

  Build(String arg_home, JpsGantProjectBuilder prjBuilder,
        Script script_utils, Script script_ultimate_utils,
        Script script_layouts, Script script_libLicenses){
    home = arg_home
    projectBuilder = prjBuilder
    paths = new Paths(home)
    steps = new Steps()
    utils = script_utils
    ultimate_utils = script_ultimate_utils
    layouts = script_layouts
    libLicenses = script_libLicenses
  }

  def init () {
    projectBuilder.stage("Cleaning, creating folders, initializing timestamps")
    utils.loadProject()

    if (steps.clear) {
      projectBuilder.stage("Cleaning up sandbox folder")
      utils.forceDelete(paths.sandbox)
      projectBuilder.stage("Creating folders")
      [paths.sandbox, paths.classesTarget, paths.distWin, paths.distWinZip, paths.distAll,
       paths.distJars, paths.distUnix, paths.distMac, paths.distDev, paths.artifacts].each {
        ant.mkdir(dir: it)
      }
    }
    ant.tstamp() {
      format(property: "today.year", pattern: "yyyy")
    }
    projectBuilder.stage("Init done")
  }

  def zip() {
    if (steps.zipSources) {
      projectBuilder.stage("zip: $home $paths.artifacts")
      utils.zipSources(home, paths.artifacts)
    }
  }

  def compile(String jdk) {
    paths.jdkHome = jdk
    projectBuilder.stage("Compilation")
    projectBuilder.stage("step paths - " + paths.jdkHome + "/lib/tools.jar")
    projectBuilder.stage("step home - " + home + "/community/lib/junit.jar")

    if (steps.compile) {
      projectBuilder.arrangeModuleCyclesOutputs = true
      projectBuilder.targetFolder = paths.classesTarget
      println "targetFolder: " + paths.classesTarget
      projectBuilder.cleanOutput()
      projectBuilder.buildProduction()
    }	

/*    projectBuilder.stage("step - additionalCompilationStep")
    if (utils.isDefined("additionalCompilationStep")) {
      projectBuilder.info("Using additional compilation step: " + additionalCompilationStep)
      def script = includeFile(additionalCompilationStep)
      script.doCustomCompile();
    }

    projectBuilder.stage("step - wireBuildDate")
    utils.wireBuildDate(buildName, appInfoFile())

    projectBuilder.stage("step - steps.build_searchable_options - finished")*/
  }

  def layout(){
    projectBuilder.stage("step - layout")
    if (steps.layout) {
      LayoutInfo layoutInfo = layouts.layoutFull(paths.distJars)

      ultimate_utils.layoutUpdater(paths.artifacts)

      ultimate_utils.layoutInternalUtilities(paths.artifacts)
      layouts.layout_duplicates(paths.artifacts, "duplicates.jar")

      layouts.layout_core_upsource(home, paths.artifacts_core_upsource)
      utils.notifyArtifactBuilt(paths.artifacts_core_upsource)

      libLicenses.generateLicensesTable("${paths.artifacts}/third-party-libraries.txt", layoutInfo.usedModules)

      def jpsArtifactsPath = "$paths.artifacts/jps"
      ant.mkdir(dir: jpsArtifactsPath)
      layouts.layoutJps(home, jpsArtifactsPath)
      utils.notifyArtifactBuilt(jpsArtifactsPath)
    }
    projectBuilder.stage("layout - Finished")
  }

  def scramble (Map args) {
    projectBuilder.stage("scramble - ")
    if (utils.isUnderTeamCity()) {
      projectBuilder.stage("Scrambling - getPreviousLogs")
      getPreviousLogs()
      projectBuilder.stage("Scrambling - prevBuildLog ")
      def prevBuildLog = "$paths.sandbox/prevBuild/logs/ChangeLog.txt"
      if (!new File(prevBuildLog).exists()) prevBuildLog = null

      projectBuilder.stage("Scrambling - inc ")
      def inc = prevBuildLog != null ? "looseChangeLogFileIn=\"${prevBuildLog}\"" : ""
      utils.copyAndPatchFile("$home/build/conf/script.zkm.stub", "$paths.sandbox/script.zkm",
                       ["CLASSES": "\"$paths.distJars/lib/${args.jarName}\"", "SCRAMBLED_CLASSES": "$paths.distJars/lib", "INCREMENTAL": inc])

      ant.mkdir(dir: "$paths.artifacts/${args.jarName}.unscrambled")
      def unscrambledPath = "$paths.artifacts/${args.jarName}.unscramble"
      ant.copy(file: "$paths.distJars/lib/${args.jarName}", todir: unscrambledPath)
      utils.notifyArtifactBuilt("$unscrambledPath/${args.jar}")

      ultimate_utils.zkmScramble("$paths.sandbox/script.zkm", paths.distJars/lib, args.jarName)

      ant.zip(destfile: "$paths.artifacts/logs.zip") {
        fileset(file: "ChangeLog.txt")

        fileset(file: "ZKM_log.txt")
        fileset(file: "$paths.sandbox/script.zkm")
      }

      ant.delete(file: "ChangeLog.txt")
      ant.delete(file: "ZKM_log.txt")
    }
    else {
      projectBuilder.info("teamcity.buildType.id is not defined. Incremental scrambling is disabled")
    }
    projectBuilder.stage("Scrambling - finished")
  }

  private lastPinnedBuild() {
    "http://buildserver/httpAuth/repository/download/${this."teamcity.buildType.id"}/.lastPinned"
  }

  private getPreviousLogs() {
    def removeZip = "${lastPinnedBuild()}/logs.zip"
    def localZip = "${paths.sandbox}/prevBuild/logs.zip"
    ant.mkdir(dir: "${paths.sandbox}/prevBuild")
    ant.get(src: removeZip,
            dest: localZip,
            username: "builduser",
            password: "qpcv4623nmdu",
            ignoreerrors: "true"
    )

    if (new File(localZip).exists()) {
      ant.unzip(src: localZip, dest: "${paths.sandbox}.prevBuild/logs") {
        patternset {
          include(name: "ChangeLog.txt")
        }
      }
    }
  }

  def install() {
    projectBuilder.stage("--- layoutShared ---")
    layouts.layoutShared(layout_args, paths.distAll)

    projectBuilder.stage("--- layoutWin ---")
    layouts.layoutWin(layout_args, paths.distWin)

    projectBuilder.stage("--- layoutUnix ---")
    layouts.layoutUnix(layout_args, paths.distUnix)

    projectBuilder.stage("--- layoutMac ---")
    layouts.layoutMac(layout_args, paths.distMac)

    projectBuilder.stage("--- buildNSISs ---")
    buildNSISs()

    projectBuilder.stage("--- targz ---")
    utils.buildTeamServer()

    projectBuilder.stage("--- checkLibLicenses ---")
    libLicenses.checkLibLicenses();
  }

  // think: should be relocated in utils.gant script
/*  def includeFile(String filename) {
    Script s = groovyShell.parse(new File("$home/build/scripts/$filename"))
    s.setBinding(binding)
    s
  }*/

  // think: to be implement for each product
/*  String appInfoFile() {
    projectBuilder.stage("step - wireBuildDate")
    //return projectBuilder.moduleOutput(utils.findModule("ultimate-resources")) + "/idea/ApplicationInfo.xml"
    return "${projectBuilder.moduleOutput(utils.findModule("ultimate-resources"))}/idea/ApplicationInfo.xml"
  }*/

  // rewrite to create one unspecified launcher
/*
  def buildLaunchers() {
    Map args = ["tools_jar": "true"]
    ultimate_utils.buildExe("$home/build/conf/idea.exe4j", system_selector, args)
    ultimate_utils.buildExe("$home/build/conf/idea64.exe4j", system_selector, args + ["output.file": "idea64.exe"])

    List ceResourcePaths = ["$ch/community-resources/src", "$ch/platform/icons/src"]
    utils.buildWinLauncher(ch, "$ch/bin/WinLauncher/WinLauncher.exe", "$paths.sandbox/dist.win.ce/bin/idea.exe", appInfoFileCE(),
                     "$home/build/conf/ideaCE-launcher.properties", system_selector_CE, ceResourcePaths)
    utils.buildWinLauncher(ch, "$ch/bin/WinLauncher/WinLauncher64.exe", "$paths.sandbox/dist.win.ce/bin/idea64.exe", appInfoFileCE(),
                     "$home/build/conf/ideaCE64-launcher.properties", system_selector_CE, ceResourcePaths)
  }*/

  // rewrite to create one unspecified installation
  def buildNSISs() {
    ultimate_utils.buildNSIS([paths.distAll, paths.distJars, paths.distWin],
              "$home/build/conf/nsis/strings.nsi", "$home/build/conf/nsis/paths.nsi",
              "ideaIU-", true, true, system_selector)
    ultimate_utils.buildNSIS(["$paths.sandbox/dist.all.ce", "$paths.sandbox/dist.win.ce"],
              "$home/build/conf/nsis/stringsCE.nsi", "$home/build/conf/nsis/pathsCE.nsi",
              "ideaIC-", true, true, system_selector_CE)
  }
}