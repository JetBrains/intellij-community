// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.ift.lesson.navigation

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.java.analysis.JavaAnalysisBundle
import com.intellij.java.ift.JavaLessonsBundle
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.impl.content.BaseLabel
import com.intellij.ui.InplaceButton
import com.intellij.ui.UIBundle
import training.dsl.*
import training.dsl.LessonUtil.restoreIfModifiedOrMoved
import training.learn.course.KLesson

class JavaInheritanceHierarchyLesson
  : KLesson("java.inheritance.hierarchy.lesson", JavaLessonsBundle.message("java.inheritance.hierarchy.lesson.name")) {
  override val sampleFilePath: String = "src/InheritanceHierarchySample.java"

  private val TaskRuntimeContext.editorOrNull: Editor?
    get() = FileEditorManager.getInstance(project).selectedTextEditor

  private val TaskRuntimeContext.virtualFileOrNull: VirtualFile?
    get() = editorOrNull?.document?.let { FileDocumentManager.getInstance().getFile(it) }

  override val lessonContent: LessonContext.() -> Unit = {
    sdkConfigurationTasks()

    caret("foo(demo)")

    actionTask("GotoImplementation") {
      restoreIfModifiedOrMoved()
      JavaLessonsBundle.message("java.inheritance.hierarchy.goto.implementation", action(it), code("SomeInterface#foo"))
    }

    task {
      text(JavaLessonsBundle.message("java.inheritance.hierarchy.choose.any.implementation", LessonUtil.rawEnter()))

      stateCheck {
        val editor = editorOrNull ?: return@stateCheck false
        val file = virtualFileOrNull ?: return@stateCheck false
        (file.name == "DerivedClass1.java" || file.name == "DerivedClass2.java") && editor.atDeclarationPosition()
      }

      restoreState(delayMillis = defaultRestoreDelay) {
        focusOwner is EditorComponentImpl
      }

      test {
        Thread.sleep(1000)
        invokeActionViaShortcut("ENTER")
      }
    }

    task("GotoSuperMethod") {
      text(JavaLessonsBundle.message("java.inheritance.hierarchy.navigate.to.base", action(it), icon(AllIcons.Gutter.ImplementingMethod)))
      stateCheck {
        val editor = editorOrNull ?: return@stateCheck false
        val file = virtualFileOrNull ?: return@stateCheck false
        file.name == "SomeInterface.java" && editor.atDeclarationPosition()
      }
      restoreIfModifiedOrMoved()

      test { actions(it) }
    }

    task("GotoImplementation") {
      text(JavaLessonsBundle.message("java.inheritance.hierarchy.invoke.implementations.again", icon(AllIcons.Gutter.ImplementedMethod),
                                     action(it)))
      triggerAndFullHighlight().component { ui: InplaceButton ->
        ui.toolTipText == IdeBundle.message("show.in.find.window.button.name")
      }
      restoreIfModifiedOrMoved()

      test { actions(it) }
    }

    task {
      before {
        closeAllFindTabs()
      }
      text(JavaLessonsBundle.message("java.inheritance.hierarchy.open.in.find.tool.window", findToolWindow(),
                                     icon(AllIcons.General.Pin_tab)))
      triggerUI().component { ui: BaseLabel ->
        ui.text == (CodeInsightBundle.message("goto.implementation.findUsages.title", "foo")) ||
        ui.text == (JavaAnalysisBundle.message("navigate.to.overridden.methods.title", "foo"))
      }
      restoreState(delayMillis = defaultRestoreDelay) {
        focusOwner is EditorComponentImpl
      }

      test {
        ideFrame {
          val target = previous.ui!!
          jComponent(target).click()
          jComponent(target).click() // for some magic reason one click sometimes doesn't work :(
        }
      }
    }

    task("HideActiveWindow") {
      text(JavaLessonsBundle.message("java.inheritance.hierarchy.hide.find.tool.window", action(it), findToolWindow()))
      checkToolWindowState("Find", false)
      restoreIfModifiedOrMoved()
      test { actions(it) }
    }

    actionTask("MethodHierarchy") {
      restoreIfModifiedOrMoved()
      JavaLessonsBundle.message("java.inheritance.hierarchy.open.method.hierarchy", action(it))
    }

    task("HideActiveWindow") {
      text(JavaLessonsBundle.message("java.inheritance.hierarchy.hide.method.hierarchy", hierarchyToolWindow(), action(it)))
      checkToolWindowState("Hierarchy", false)
      restoreIfModifiedOrMoved()
      test { actions(it) }
    }

    actionTask("TypeHierarchy") {
      restoreIfModifiedOrMoved()
      JavaLessonsBundle.message("java.inheritance.hierarchy.open.class.hierarchy", action(it))
    }

    text(JavaLessonsBundle.message("java.inheritance.hierarchy.last.note",
                                   action("GotoImplementation"),
                                   action("GotoSuperMethod"),
                                   action("MethodHierarchy"),
                                   action("TypeHierarchy"),
                                   action("GotoAction"),
                                   strong("hierarchy")))
  }

  private fun Editor.atDeclarationPosition(): Boolean {
    return document.charsSequence.let {
      it.subSequence(caretModel.currentCaret.offset, it.length).startsWith("foo(FileStructureDemo demo)")
    }
  }

  private fun TaskContext.findToolWindow() = strong(UIBundle.message("tool.window.name.find"))
  private fun TaskContext.hierarchyToolWindow() = strong(UIBundle.message("tool.window.name.hierarchy"))

  override val helpLinks: Map<String, String> get() = mapOf(
    Pair(JavaLessonsBundle.message("java.inheritance.hierarchy.help.link"),
         LessonUtil.getHelpLink("viewing-structure-and-hierarchy-of-the-source-code.html")),
  )
}
