// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.ift.lesson.refactorings

import com.intellij.java.refactoring.JavaRefactoringBundle
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.extractMethod.ExtractMethodHandler
import com.intellij.ui.UIBundle
import training.dsl.LessonContext
import training.dsl.LessonUtil.restoreIfModifiedOrMoved
import training.dsl.dropMnemonic
import training.dsl.parseLessonSample
import training.learn.LessonsBundle
import training.learn.course.KLesson
import javax.swing.JDialog

class JavaExtractMethodCocktailSortLesson
  : KLesson("Refactorings.ExtractMethod", LessonsBundle.message("extract.method.lesson.name")) {
  override val lessonContent: LessonContext.() -> Unit
    get() = {
      prepareSample(javaSortSample)

      actionTask("ExtractMethod") {
        restoreIfModifiedOrMoved()
        LessonsBundle.message("extract.method.invoke.action", action(it))
      }
      // Now will be open the first dialog

      val processDuplicatesTitle = JavaRefactoringBundle.message("process.duplicates.title")
      task {
        val refactorButtonText = RefactoringBundle.message("refactor.button").dropMnemonic()
        text(LessonsBundle.message("extract.method.start.refactoring", strong(refactorButtonText)))

        // Wait until the second dialog
        triggerByUiComponentAndHighlight(highlightBorder = false, highlightInside = false) { dialog : JDialog ->
          dialog.title == processDuplicatesTitle
        }

        var needRestore = false
        restoreState {
          val insideDialog = Thread.currentThread().stackTrace.any {
            it.className.contains(ExtractMethodHandler::class.simpleName!!)
          }
          (needRestore && !insideDialog).also { needRestore = insideDialog }
        }

        test {
          dialog(RefactoringBundle.message("extract.method.title")) {
            button(refactorButtonText).click()
          }
        }
      }

      val replaceButtonText = UIBundle.message("replace.prompt.replace.button").dropMnemonic()
      task {
        text(LessonsBundle.message("extract.method.confirm.several.replaces", strong(replaceButtonText)))

        stateCheck {
          previous.ui?.isShowing?.not() ?: true
        }

        test {
          dialog(processDuplicatesTitle) {
            button(replaceButtonText).click()
          }
        }
      }
    }
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
