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







}
