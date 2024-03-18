// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.ift.lesson.navigation

import training.dsl.LessonSample
import training.dsl.parseLessonSample
import training.learn.lesson.general.navigation.OccurrencesLesson

class JavaOccurrencesLesson : OccurrencesLesson() {
  override val sample: LessonSample = parseLessonSample("""
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
}