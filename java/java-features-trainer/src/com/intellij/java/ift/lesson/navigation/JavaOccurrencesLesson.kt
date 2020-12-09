// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.ift.lesson.navigation

import com.intellij.java.ift.JavaLessonsBundle
import com.intellij.testGuiFramework.framework.GuiTestUtil
import com.intellij.testGuiFramework.impl.actionButton
import com.intellij.testGuiFramework.util.Key
import com.intellij.usageView.UsageViewBundle
import training.learn.interfaces.Module
import training.learn.lesson.kimpl.KLesson
import training.learn.lesson.kimpl.LessonContext
import training.learn.lesson.kimpl.LessonUtil
import training.learn.lesson.kimpl.LessonUtil.restoreIfModifiedOrMoved
import training.learn.lesson.kimpl.parseLessonSample

class JavaOccurrencesLesson(module: Module)
  : KLesson("java.occurrences.lesson", JavaLessonsBundle.message("java.find.occurrences.lesson.name"), module, "JAVA") {
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

    actionTask("Find") {
      restoreIfModifiedOrMoved()
      JavaLessonsBundle.message("java.find.occurrences.invoke.find", code("cellphone"), action(it))
    }
    task("FindNext") {
      trigger("com.intellij.find.editorHeaderActions.NextOccurrenceAction")
      text(JavaLessonsBundle.message("java.find.occurrences.find.next", LessonUtil.rawEnter(), action(it)))
      test {
        ideFrame {
          actionButton(UsageViewBundle.message("action.next.occurrence")).click()
        }
      }
    }
    task("FindPrevious") {
      trigger("com.intellij.find.editorHeaderActions.PrevOccurrenceAction")
      text(JavaLessonsBundle.message("java.find.occurrences.find.previous", action("FindPrevious")))
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
      test { GuiTestUtil.shortcut(Key.ESCAPE) }
    }
    actionTask("FindNext") {
      JavaLessonsBundle.message("java.find.occurrences.find.next.in.editor", action(it))
    }
    actionTask("FindPrevious") {
      JavaLessonsBundle.message("java.find.occurrences.find.previous.in.editor", action(it))
    }
    text(JavaLessonsBundle.message("java.find.occurrences.note.about.cyclic", action("FindNext"), action("FindPrevious")))
  }
}