/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.psi.impl.search

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.psi.impl.search.JavaNullMethodArgumentIndex
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.indexing.FileContentImpl

class JavaNullMethodArgumentIndexTest : BasePlatformTestCase() {

  fun testIndex() {
    val file = myFixture.configureByText(JavaFileType.INSTANCE, """
            package org.some;

            class Main111 {

                Main111(Object o) {

                }

                void someMethod(Object o, Object o2, Object o3) {
                }

                static void staticMethod(Object o, Object o2, Object o3) {
                }

                public static void main(String[] args) {
                    staticMethod(null
, "", "");
                    org.some.Main111.staticMethod("", "", null""" + '\t' +  """);
                    new Main111(null).someMethod("", "", null  );

                    String s = null;

                    Main111 m = new Main111(null);
                    m.someMethod(null, "", "");
                }

                static class SubClass {
                    SubClass(Object o) {

                    }
                }

                static class SubClass2 {
                    SubClass2(Object o) {

                    }
                }

                static void main() {
                    new org.some.Main111(null);
                    new org.some.Main111.SubClass(null);
                    new SubClass2(null);


                    new ParametrizedRunnable(null) {
                      @Override
                      void run() {

                      }};
                }

                abstract class ParametrizedRunnable {
                  Object parameter;

                  ParametrizedRunnable(Object parameter){
                    this.parameter = parameter;
                  }

                  abstract void run();
                }
            }
    """).virtualFile
    val content = FileContentImpl.createByFile(file, project)
    val data = JavaNullMethodArgumentIndex().indexer.map(content).keys

    assertSize(8, data)
    assertContainsElements(data,
                           JavaNullMethodArgumentIndex.MethodCallData("staticMethod", 0),
                           JavaNullMethodArgumentIndex.MethodCallData("staticMethod", 2),
                           JavaNullMethodArgumentIndex.MethodCallData("someMethod", 0),
                           JavaNullMethodArgumentIndex.MethodCallData("someMethod", 2),
                           JavaNullMethodArgumentIndex.MethodCallData("Main111", 0),
                           JavaNullMethodArgumentIndex.MethodCallData("SubClass", 0),
                           JavaNullMethodArgumentIndex.MethodCallData("SubClass2", 0),
                           JavaNullMethodArgumentIndex.MethodCallData("ParametrizedRunnable", 0))

  }
}