/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.codeStyle.arrangement

import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.*

class JavaRearrangerAnonymousClassesTest extends AbstractJavaRearrangerTest {

  public void "test rearrangement doesn't brake anon classes alignment"() {

    def text = '''\
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
'''
    doTest(
      initial: text,
      expected: text,
      rules: classic
    )
  }

  public void "test anonymous classes inside method"() {
    doTest(
      initial: '''\
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
''',
      expected: '''\
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
''',
      rules: [rule(FIELD),
              rule(ENUM),
              rule(INTERFACE),
              rule(CLASS),
              rule(CONSTRUCTOR),
              rule(METHOD)]
    )
  }
}
