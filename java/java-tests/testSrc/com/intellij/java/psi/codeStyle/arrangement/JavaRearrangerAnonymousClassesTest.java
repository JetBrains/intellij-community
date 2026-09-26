// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.codeStyle.arrangement;

import org.intellij.lang.annotations.Language;

import java.util.List;

import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.CLASS;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.CONSTRUCTOR;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.ENUM;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.FIELD;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.INTERFACE;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.METHOD;

public class JavaRearrangerAnonymousClassesTest extends AbstractJavaRearrangerTest {
  public void testDoNotBreakAnonymousClassAlignment() {
    @Language("JAVA") String text = """
      public final class Test {
          public static void main(String[] args) {
              Action action1 = new AbstractAction() {
                  @Override
                  public void actionPerformed(ActionEvent e) {
                  }
              };
              Action action2 = new AbstractAction() {
                  @Override
                  public void actionPerformed(ActionEvent e) {
                  }
              };
          }
      }
      """;
    doTest(text, text, classic);
  }

  public void testAnonymousAndLocalClasses() {
    String text = """
      class Main {
          void run() {
              empty(new Cloneable() {
              });
              class MyRunnable {
              }
          }

          void empty(Object o) {
          }
      }""";
    doTest(text, text, classic);
  }

  public void testAnonymousClassesInsideMethod() {
    doTest("""
             public class Rearranging {

                 public void Testing() {

                     class Model {
                         private Cat cat = new Cat();
                         private Dog dog = new Dog();
                         class Cat { private String catSound = "MIAU"; }
                         class Dog { private String dogSound = "AUAU"; }
                     }

                     class Born { private String date; }

                     class Die { private String date; }

                 }

                 private int value;
             }
             """, """
             public class Rearranging {

                 private int value;

                 public void Testing() {

                     class Model {
                         private Cat cat = new Cat();
                         private Dog dog = new Dog();
                         class Cat { private String catSound = "MIAU"; }
                         class Dog { private String dogSound = "AUAU"; }
                     }

                     class Born { private String date; }

                     class Die { private String date; }

                 }
             }
             """, List.of(rule(FIELD), rule(ENUM), rule(INTERFACE), rule(CLASS), rule(CONSTRUCTOR), rule(METHOD)));
  }
}
