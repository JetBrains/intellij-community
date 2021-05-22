// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.ift

import com.intellij.ide.impl.NewProjectUtil
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.impl.LanguageLevelProjectExtensionImpl
import com.intellij.openapi.roots.ui.configuration.SdkLookup
import com.intellij.openapi.roots.ui.configuration.SdkLookupDecision
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.java.LanguageLevel
import com.intellij.util.lang.JavaVersion
import training.lang.AbstractLangSupport
import training.learn.LearnBundle
import training.project.ProjectUtils
import training.project.ReadMeCreator
import training.util.getFeedbackLink
import java.nio.file.Path

class JavaLangSupport : AbstractLangSupport() {
  override val defaultProjectName = "IdeaLearningProject"
  override val projectResourcePath = "learnProjects/java/LearnProject"

  override val primaryLanguage: String = "JAVA"

  override val defaultProductName: String = "IDEA"

  override val filename: String = "Learning.java"

  override val projectSandboxRelativePath: String = "Sample.java"

  override val langCourseFeedback get() = getFeedbackLink(this, false)

  override val readMeCreator: ReadMeCreator = object : ReadMeCreator() {
    private val sharedIndexesRemark: String = JavaLessonsBundle.message("readme.shared.indexes.remark")
    override val indexingDescription = "${super.indexingDescription}\n\n$sharedIndexesRemark"
  }

  override fun installAndOpenLearningProject(projectPath: Path,
                                             projectToClose: Project?,
                                             postInitCallback: (learnProject: Project) -> Unit) {
    super.installAndOpenLearningProject(projectPath, projectToClose) { project ->
      findJavaSdkAsync(project) { sdk ->
        if (sdk != null) {
          applyProjectSdk(sdk, project)
        }
        postInitCallback(project)
      }
    }
  }

  private fun findJavaSdkAsync(project: Project, onSdkSearchCompleted: (Sdk?) -> Unit) {
    val javaSdkType = JavaSdk.getInstance()
    val notification = ProjectUtils.createSdkDownloadingNotification()
    SdkLookup.newLookupBuilder()
      .withSdkType(javaSdkType)
      .withVersionFilter {
        JavaVersion.tryParse(it)?.isAtLeast(6) == true
      }
      .onDownloadableSdkSuggested { sdkFix ->
        val userDecision = invokeAndWaitIfNeeded {
          Messages.showYesNoDialog(
            LearnBundle.message("learn.project.initializing.jdk.download.message", sdkFix.downloadDescription),
            LearnBundle.message("learn.project.initializing.jdk.download.title"),
            null
          )
        }
        if (userDecision == Messages.YES) {
          notification.notify(project)
          SdkLookupDecision.CONTINUE
        }
        else {
          onSdkSearchCompleted(null)
          SdkLookupDecision.STOP
        }
      }
      .onSdkResolved { sdk ->
        if (sdk != null && sdk.sdkType === javaSdkType) {
          onSdkSearchCompleted(sdk)
          DumbService.getInstance(project).runWhenSmart {
            notification.expire()
          }
        }
      }
      .executeLookup()
  }

  override fun getSdkForProject(project: Project): Sdk? {
    return null
  }

  override fun applyProjectSdk(sdk: Sdk, project: Project) {
    val applySdkAction = {
      runWriteAction { NewProjectUtil.applyJdkToProject(project, sdk) }
    }
    runInEdt {
      CommandProcessor.getInstance().executeCommand(project, applySdkAction, null, null)
    }
  }

  override fun applyToProjectAfterConfigure(): (Project) -> Unit = { newProject ->
    //Set language level for LearnProject
    LanguageLevelProjectExtensionImpl.getInstanceImpl(newProject).currentLevel = LanguageLevel.JDK_1_6
  }

  override fun checkSdk(sdk: Sdk?, project: Project) {}

  override fun blockProjectFileModification(project: Project, file: VirtualFile): Boolean {
    return file.name != projectSandboxRelativePath
  }
}
