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
package org.jetbrains.jps

import org.jetbrains.jps.LayoutInfo
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
  final ideaSystem
  final ideaConfig

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

    ideaSystem = "$sandbox/system"
    ideaConfig = "$sandbox/config"
  }
}

class Steps {
  def zipSources = true
  def compile = true
  def layout = true
  def build_searchable_options = true
  def zipwin = true
  def targz = true
  def dmg = true
  def sit = true
}

class Build {
  def product
  def productCode  
  def modules
  Steps steps
  Paths paths
  def home
  ProjectBuilder projectBuilder
  def buildNumber

  def compile() {
    projectBuilder.stage("Compilation")
    if (steps.compile) {
	  projectBuilder.setTargetFolder(paths.classesTarget)
      println "targetFolder: " + paths.classesTarget
	  projectBuilder.cleanOutput()
	  projectBuilder.buildModules(modules, false)
    }
    projectBuilder.stage("Compilation finished")
  }
  
  def scramble() {}
  def install() {}

  Build(String arg_home, ProjectBuilder prjBuilder){
    home = arg_home
	projectBuilder = prjBuilder
    steps = new Steps()
    paths = new Paths(home)
  }
  
  def includeFile(String filename) {
	Script s = groovyShell.parse(new File("this.home/build/scripts/$filename"))
	s.setBinding(binding)
	s
  }
}
