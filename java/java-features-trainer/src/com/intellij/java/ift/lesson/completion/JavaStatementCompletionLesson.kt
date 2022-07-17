// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.ift.lesson.completion

import com.intellij.java.ift.JavaLessonsBundle
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiForStatement
import com.intellij.psi.util.PsiTreeUtil
import training.dsl.LessonContext
import training.dsl.LessonUtil
import training.dsl.LessonUtil.checkExpectedStateOfEditor
import training.dsl.LessonUtil.restoreIfModifiedOrMoved
import training.dsl.TaskRuntimeContext
import training.dsl.parseLessonSample
import training.learn.course.KLesson

class JavaStatementCompletionLesson
  : KLesson("Statement completion", JavaLessonsBundle.message("java.statement.completion.lesson.name")) {

  val sample = parseLessonSample("""
    class PrimeNumbers {
        public static void main(String[] args) {
            System.out.println("Prime numbers between 1 and 100");
    
            for (int i = 2; i < 100; i++) {
                boolean isPrime = true;
    
                for (int j = 2; j < i; j++)<caret>
    
                if (isPrime) {
                    System.out.print(i + " ");
                }
            }
        }
    }
  """.trimIndent())

  override val lessonContent: LessonContext.() -> Unit = {
    prepareSample(sample)
    actionTask("EditorCompleteStatement") {
      restoreIfModifiedOrMoved(sample)
      JavaLessonsBundle.message("java.statement.completion.complete.for", action(it), code("for"))
    }
    task("EditorCompleteStatement") {
      text(JavaLessonsBundle.message("java.statement.completion.complete.if", code("if"), action(it)))
      stateCheck {
        return@stateCheck checkIfAppended()
      }
      proposeRestore {
        checkExpectedStateOfEditor(previous.sample) { typedString -> "if".startsWith(typedString) }
      }
      test {
        type("if")
        actions(it)
      }
    }
    task("EditorCompleteStatement") {
      val code = "i % j == 0"
      text(JavaLessonsBundle.message("java.statement.completion.complete.condition",
                                     code(code), action(it), code("if")))
      trigger(it)
      test {
        type(code)
        actions(it)
      }
    }
    task("EditorCompleteStatement") {
      val code = "isPrime = false; break"
      text(JavaLessonsBundle.message("java.statement.completion.complete.finish.body",
                                     code(code), action(it)))
      trigger(it)
      test {
        type(code)
        actions(it)
      }
    }
  }

  private fun TaskRuntimeContext.checkIfAppended(): Boolean {
    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
    val psiForStatements = PsiTreeUtil.findChildrenOfType(psiFile, PsiForStatement::class.java).toTypedArray()
    if (psiForStatements.size < 2) return false

    val psiForStatement = psiForStatements[1] as PsiForStatement

    val text = psiForStatement.body!!.text
    val trimmedText = text.replace("\\s+".toRegex(), "")

    return trimmedText == "{if(){}}"
  }

  override val suitableTips = listOf("CompleteStatement", "FinishBySmartEnter")

  override val helpLinks: Map<String, String> get() = mapOf(
    Pair(JavaLessonsBundle.message("java.statement.completion.help.link"),
         LessonUtil.getHelpLink("auto-completing-code.html#statements_completion")),
  )
}