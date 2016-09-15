/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.tests.gui.script

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFileManager

/**
 * Created by @author Sergey Karashevich on 12/09/16.
 */
class ScriptCompileAction: AnAction(){

  fun homePath() = PathManager.getHomePath()
  //we have to load idea project to compile kotlin files on the fly. Let's do it in a lazy manner.
  val project: Project? by lazy {
    ProjectManagerEx.getInstanceEx().loadProject("${homePath()}/idea.iml");
  }

  val srcPath = "community/community-tests/src"
  val myPackage = "com.intellij.tests.gui.test"

  override fun actionPerformed(e: AnActionEvent?) {
    compileAndRun()
  }

  fun compileAndRun() {
    val myPackagePath = myPackage.replace(".", "/")

    val url = VfsUtil.pathToUrl("${homePath()}/${srcPath}/${myPackagePath}/CurrentTest.kt")
    val virtualFile = VirtualFileManager.getInstance().findFileByUrl(url)

    CompilerManager.getInstance(project).compile(arrayOf(virtualFile), { aborted, errors, warnings, compileContext ->
      if (!aborted) {
        print("Compilation done")
        ScriptAction("CurrentTest").actionPerformed(AnActionEvent.createFromDataContext("nothing", null, DataContext.EMPTY_CONTEXT))
      }
    })
  }

}