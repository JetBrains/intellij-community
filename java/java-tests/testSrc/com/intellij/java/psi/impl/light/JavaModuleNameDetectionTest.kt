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
package com.intellij.java.psi.impl.light

import com.intellij.psi.impl.light.LightJavaModule
import org.junit.Test
import kotlin.test.assertEquals

class JavaModuleNameDetectionTest {
  @Test fun plain() = doTest("foo", "foo")
  @Test fun versioned() = doTest("foo-1.2.3-SNAPSHOT", "foo")
  @Test fun trailing() = doTest("foo2bar3", "foo2bar3")
  @Test fun replacing() = doTest("foo_bar", "foo.bar")
  @Test fun collapsing() = doTest("foo...bar", "foo.bar")
  @Test fun trimming() = doTest("...foo.bar...", "foo.bar")

  private fun doTest(original: String, expected: String) = assertEquals(expected, LightJavaModule.moduleName(original))
}