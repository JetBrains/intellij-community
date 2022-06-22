// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.ift.lesson.refactorings

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.java.ift.JavaLessonsBundle
import com.intellij.java.refactoring.JavaRefactoringBundle
import com.intellij.ui.UIBundle
import training.dsl.*
import training.dsl.LessonUtil.rawEnter
import training.dsl.LessonUtil.restoreIfModifiedOrMoved
import training.learn.LessonsBundle
import training.learn.course.KLesson
import javax.swing.JDialog

class JavaExtractMethodCocktailSortLesson
  : KLesson("Refactorings.ExtractMethod", LessonsBundle.message("extract.method.lesson.name")) {
  override val lessonContent: LessonContext.() -> Unit
    get() = {
      prepareSample(javaSortSample)
      showWarningIfInplaceRefactoringsDisabled()

      actionTask("ExtractMethod") {
        restoreIfModifiedOrMoved(javaSortSample)
        LessonsBundle.message("extract.method.invoke.action", action(it))
      }

      task {
        transparentRestore = true
        stateCheck {
          TemplateManagerImpl.getTemplateState(editor) != null
        }
      }

      val processDuplicatesTitle = JavaRefactoringBundle.message("process.duplicates.title")
      task {
        text(JavaLessonsBundle.message("java.extract.method.edit.method.name", rawEnter()))

        triggerUI().component { dialog: JDialog ->
          dialog.title == processDuplicatesTitle
        }

        restoreState(delayMillis = defaultRestoreDelay) {
          TemplateManagerImpl.getTemplateState(editor) == null
        }

        test(waitEditorToBeReady = false) {
          invokeActionViaShortcut("ENTER")
        }
      }

      val replaceButtonText = UIBundle.message("replace.prompt.replace.button").dropMnemonic()
      task {
        text(LessonsBundle.message("extract.method.confirm.several.replaces", strong(replaceButtonText)))

        stateCheck {
          previous.ui?.isShowing?.not() ?: true
        }

        test(waitEditorToBeReady = false) {
          dialog(processDuplicatesTitle) {
            button(replaceButtonText).click()
          }
        }
      }

      restoreRefactoringOptionsInformer()
    }

  override val suitableTips = listOf("ExtractMethod")

  override val helpLinks: Map<String, String> get() = mapOf(
    Pair(LessonsBundle.message("extract.method.help.link"),
         LessonUtil.getHelpLink("extract-method.html")),
  )
}

private val javaSortSample = parseLessonSample("""
  class Demo {
      public static void cocktailSort(int[] a) {
          boolean swapped = true;
          int start = 0;
          int end = a.length;
  
          while (swapped) {
              swapped = false;
  
              for (int i = start; i < end - 1; ++i) {
  <select>                if (a[i] > a[i + 1]) {
                      int temp = a[i];
                      a[i] = a[i + 1];
                      a[i + 1] = temp;
                      swapped = true;
                  }
  </select>            }
  
              if (!swapped)
                  break;
  
              swapped = false;
              end = end - 1;

              for (int i = end - 1; i >= start; i--) {
                  if (a[i] > a[i + 1]) {
                      int temp = a[i];
                      a[i] = a[i + 1];
                      a[i + 1] = temp;
                      swapped = true;
                  }
              }
  
              start = start + 1;
          }
      }
  }
""".trimIndent())
