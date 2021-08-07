// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection

import com.intellij.debugger.DebuggerManagerEx
import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.execution.stacktrace.StackTraceLine
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.psi.*
import com.intellij.uast.UastHintedVisitorAdapter
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor
import javax.swing.Icon

class TestFailedLineInspection : AbstractBaseUastLocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor =
    UastHintedVisitorAdapter.create(holder.file.language, TestFailedVisitor(holder, isOnTheFly), arrayOf(UCallExpression::class.java), true)

  class TestFailedVisitor(private val holder: ProblemsHolder, private val isOnTheFly: Boolean) : AbstractUastNonRecursiveVisitor() {
    override fun visitCallExpression(node: UCallExpression): Boolean {
      val sourcePsi = node.sourcePsi ?: return true
      val state = holder.project.service<TestFailedLineManager>().getFailedLineState(node) ?: return true
      if (state.magnitude <= TestStateInfo.Magnitude.IGNORED_INDEX.value) return true
      val fixes = arrayOf<LocalQuickFix>(
        DebugActionFix(sourcePsi, state.topStacktraceLine),
        RunActionFix(sourcePsi, DefaultRunExecutor.EXECUTOR_ID)
      )
      val identifier = node.methodIdentifier.sourcePsiElement ?: return true
      val descriptor = InspectionManager.getInstance(holder.project).createProblemDescriptor(
        identifier, state.errorMessage, isOnTheFly, fixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING
      ).apply { setTextAttributes(CodeInsightColors.RUNTIME_ERROR) }
      holder.registerProblem(descriptor)
      return true
    }
  }

  private open class RunActionFix(element: PsiElement, executorId: String) : LocalQuickFix, Iconable {
    private val myExecutor = ExecutorRegistry.getInstance().getExecutorById(executorId) ?: throw IllegalStateException(
      "Could not create action because executor $executorId was not found.")

    private val myConfiguration: RunnerAndConfigurationSettings =
      ConfigurationContext(element).configuration ?: throw IllegalStateException(
        "Could not create action because configuration context was not found for element $element.")

    override fun getFamilyName(): @Nls(capitalization = Nls.Capitalization.Sentence) String =
      UIUtil.removeMnemonic(myExecutor.getStartActionText(ProgramRunnerUtil.shortenName(myConfiguration.name, 0)))

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      ExecutionUtil.runConfiguration(myConfiguration, myExecutor)
    }

    override fun getIcon(flags: Int): Icon = myExecutor.icon
  }

  private class DebugActionFix(
    element: PsiElement,
    private val myTopStacktraceLine: String
  ) : RunActionFix(element, DefaultDebugExecutor.EXECUTOR_ID) {
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val line = StackTraceLine(project, myTopStacktraceLine)
      line.getMethodLocation(project)?.let { location ->
        PsiDocumentManager.getInstance(project).getDocument(location.psiElement.containingFile)?.let { document ->
          DebuggerManagerEx.getInstanceEx(project).breakpointManager.addLineBreakpoint(document, line.lineNumber)
        }
      }
      super.applyFix(project, descriptor)
    }
  }
}