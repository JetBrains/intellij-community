// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.ift.lesson.navigation

import com.intellij.find.SearchTextArea
import com.intellij.java.ift.JavaLessonsBundle
import com.intellij.usageView.UsageViewBundle
import training.dsl.LessonContext
import training.dsl.LessonUtil
import training.dsl.LessonUtil.restoreIfModifiedOrMoved
import training.dsl.parseLessonSample
import training.learn.course.KLesson
import training.learn.course.LessonType

class JavaOccurrencesLesson
  : KLesson("java.occurrences.lesson", JavaLessonsBundle.message("java.find.occurrences.lesson.name")) {

  override val lessonType = LessonType.SINGLE_EDITOR

  val sample = parseLessonSample("""
    class OccurrencesDemo {
        final private String DATABASE = "MyDataBase";
        DataEntry myPerson;

        OccurrencesDemo(String name, int age, String <select>cellphone</select>) {
            myPerson = new Person(name, age, "Cellphone: " + cellphone);
        }

        interface DataEntry {
            String getCellphone();
            
            String getName();
        }

        class Person implements DataEntry {

            public Person(String name, int age, String cellphone) {
                this.name = name;
                this.age = age;
                this.cellphone = cellphone;
            }

            private String name;
            private int age;
            private String cellphone;

            public String getCellphone() {
                return cellphone;
            }

            public String getName() {
                return name;
            }

        }
    }
  """.trimIndent())


  override val lessonContent: LessonContext.() -> Unit = {
    prepareSample(sample)

    task("Find") {
      text(JavaLessonsBundle.message("java.find.occurrences.invoke.find", code("cellphone"), action(it)))
      triggerUI().component { _: SearchTextArea -> true }
      restoreIfModifiedOrMoved(sample)
      test { actions(it) }
    }
    task("FindNext") {
      trigger("com.intellij.find.editorHeaderActions.NextOccurrenceAction")
      text(JavaLessonsBundle.message("java.find.occurrences.find.next", LessonUtil.rawEnter(), action(it)))
      restoreByUi()
      test {
        ideFrame {
          actionButton(UsageViewBundle.message("action.next.occurrence")).click()
        }
      }
    }
    task("FindPrevious") {
      trigger("com.intellij.find.editorHeaderActions.PrevOccurrenceAction")
      text(JavaLessonsBundle.message("java.find.occurrences.find.previous", action("FindPrevious")))
      showWarning(JavaLessonsBundle.message("java.find.occurrences.search.closed.warning", action("Find"))) {
        editor.headerComponent == null
      }
      test {
        ideFrame {
          actionButton(UsageViewBundle.message("action.previous.occurrence")).click()
        }
      }
    }
    task("EditorEscape") {
      text(JavaLessonsBundle.message("java.find.occurrences.close.search.tool", action(it)))
      stateCheck {
        editor.headerComponent == null
      }
      test { invokeActionViaShortcut("ESCAPE") }
    }
    actionTask("FindNext") {
      JavaLessonsBundle.message("java.find.occurrences.find.next.in.editor", action(it))
    }
    actionTask("FindPrevious") {
      JavaLessonsBundle.message("java.find.occurrences.find.previous.in.editor", action(it))
    }
    text(JavaLessonsBundle.message("java.find.occurrences.note.about.cyclic", action("FindNext"), action("FindPrevious")))
  }

  override val helpLinks: Map<String, String> get() = mapOf(
    Pair(JavaLessonsBundle.message("java.find.help.link"),
         LessonUtil.getHelpLink("finding-and-replacing-text-in-file.html")),
  )
}