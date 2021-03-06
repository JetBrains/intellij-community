// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.ift.lesson.run

import training.dsl.parseLessonSample

object JavaRunLessonsUtils {
  const val demoClassName = "Sample"

  val demoSample = parseLessonSample("""
    public class $demoClassName {
      public static void main(String[] args) {
        double average = findAverage(prepareValues());
        System.out.println("The average is " + average);
      }

      private static double findAverage(String[] input) {
        checkInput(input);
        double result = 0;
        for (String s : input) {
          <caret>result += <select id=1>validateNumber(extractNumber(removeQuotes(s)))</select>;
        }
        <caret id=3/>return result;
      }
    
      private static String[] prepareValues() {
        return new String[] {"'apple 1'", "orange 2", "'tomato 3'"};
      }

      private static int extractNumber(String s) {
        return Integer.parseInt(<select id=2>s.split(" ")[0]</select>);
      }

      private static void checkInput(String[] input) {
        if (input == null || input.length == 0) {
          throw new IllegalArgumentException("Invalid input");
        }
      }

      private static String removeQuotes(String s) {
        if (s.startsWith("'") && s.endsWith("'") && s.length() > 1) {
          return s.substring(1, s.length() - 1);
        }
        return s;
      }

      private static int validateNumber(int number) {
        if (number < 0) throw new IllegalArgumentException("Invalid number: " + number);
        return number;
      }
    }
  """.trimIndent())

}