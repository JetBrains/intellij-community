// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.settings

import com.intellij.codeInsight.AnnotationsPanel
import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.ui.JavaDebuggerSupport
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Splitter
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiParameter
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Async
import javax.swing.JComponent

private val LOG = logger<CaptureConfigurable>()

class CaptureConfigurable(private val project: Project) : BoundSearchableConfigurable(
  JavaDebuggerBundle.message("async.stacktraces.configurable.display.name"),
  "reference.idesettings.debugger.capture"
) {

  override fun createPanel(): DialogPanel {
    val settings = DebuggerSettings.getInstance()
    lateinit var debuggerAgent: Cell<JBCheckBox>

    return panel {
      row {
        debuggerAgent = checkBox(JavaDebuggerBundle.message("label.capture.configurable.debugger.agent"))
          .bindSelected(settings::INSTRUMENTING_AGENT)
        button(JavaDebuggerBundle.message("label.capture.configurable.annotations.configure")) {
          AsyncAnnotationsDialog(project).show()
        }
      }
      indent {
        row {
          checkBox(JavaDebuggerBundle.message("label.capture.configurable.throttling"))
            .bindSelected(settings::AGENT_THROTTLING)
            .enabledIf(debuggerAgent.selected)
        }
      }
    }
  }
}

internal fun interface CapturePointConsumer<R> {
  fun accept(capture: Boolean, e: PsiModifierListOwner, annotation: PsiAnnotation): R
}

internal fun <R> processCaptureAnnotations(project: Project?, consumer: CapturePointConsumer<R>): List<R> {
  val contextProject = project ?: JavaDebuggerSupport.getContextProjectForEditorFieldsInDebuggerConfigurables() // fallback
  if (contextProject.isDefault) {
    return emptyList()
  }
  val debuggerProjectSettings = DebuggerProjectSettings.getInstance(contextProject)
  return scanPointsInt(contextProject, debuggerProjectSettings, true, consumer) +
         scanPointsInt(contextProject, debuggerProjectSettings, false, consumer)
}

private fun <R> scanPointsInt(
  project: Project,
  debuggerProjectSettings: DebuggerProjectSettings,
  capture: Boolean,
  consumer: CapturePointConsumer<R>,
): List<R> {
  try {
    return NodeRendererSettings.visitAnnotatedElements(getAsyncAnnotations(debuggerProjectSettings, capture), project,
                                                       { e, annotation -> consumer.accept(capture, e, annotation) },
                                                       PsiMethod::class.java, PsiParameter::class.java)
  }
  catch (_: IndexNotReadyException) {
  }
  catch (_: ProcessCanceledException) {
  }
  catch (e: Exception) {
    LOG.error(e)
  }
  return emptyList()
}

private fun getAnnotationName(capture: Boolean): String {
  return (if (capture) Async.Schedule::class.java else Async.Execute::class.java).name.replace("$", ".")
}

private fun getAsyncAnnotations(debuggerProjectSettings: DebuggerProjectSettings, capture: Boolean): List<String> {
  val annotations = if (capture) debuggerProjectSettings.myAsyncScheduleAnnotations else debuggerProjectSettings.myAsyncExecuteAnnotations
  return listOf(getAnnotationName(capture)) + annotations
}

@Suppress("SplitModeApiUsage")
private class AsyncAnnotationsDialog(project: Project) : DialogWrapper(project, true) {
  private val settings = DebuggerProjectSettings.getInstance(project)
  private val asyncSchedulePanel = AnnotationsPanel(project,
                                                    JavaDebuggerBundle.message("settings.async.schedule"),
                                                    getAsyncAnnotations(settings, true),
                                                    listOf(getAnnotationName(true)))
  private val asyncExecutePanel = AnnotationsPanel(project,
                                                   JavaDebuggerBundle.message("settings.async.execute"),
                                                   getAsyncAnnotations(settings, false),
                                                   listOf(getAnnotationName(false)))

  init {
    init()
    title = JavaDebuggerBundle.message("settings.async.annotations.configuration")
  }

  override fun createCenterPanel(): JComponent {
    val splitter = Splitter(true)
    splitter.firstComponent = asyncSchedulePanel.component
    splitter.secondComponent = asyncExecutePanel.component
    splitter.setHonorComponentsMinimumSize(true)
    splitter.preferredSize = JBUI.size(300, 400)
    return splitter
  }

  override fun doOKAction() {
    settings.myAsyncScheduleAnnotations = asyncSchedulePanel.annotations
      .filter { it != getAnnotationName(true) }
      .toTypedArray()
    settings.myAsyncExecuteAnnotations = asyncExecutePanel.annotations
      .filter { it != getAnnotationName(false) }
      .toTypedArray()
    super.doOKAction()
  }

  override fun getHelpId(): String = "reference.idesettings.debugger.customAsyncAnnotations"
}
