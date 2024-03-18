// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.codeStyle.arrangement;

import com.intellij.psi.codeStyle.arrangement.AbstractRearrangerTest;

import java.util.List;

import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.*;

public class JavaRearrangerAnonymousClassesTest extends AbstractJavaRearrangerTest {
  public void test_rearrangement_doesn_t_brake_anon_classes_alignment() {

    String text = """
      public class Test {
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

  public void test_anonymous_classes_inside_method() {
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
             """, List.of(AbstractRearrangerTest.rule(FIELD), AbstractRearrangerTest.rule(ENUM), AbstractRearrangerTest.rule(INTERFACE),
                          AbstractRearrangerTest.rule(CLASS), AbstractRearrangerTest.rule(CONSTRUCTOR), AbstractRearrangerTest.rule(METHOD)));
  }
}
