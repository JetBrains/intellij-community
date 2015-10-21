/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.testIntergration

import com.intellij.execution.TestStateStorage
import com.intellij.openapi.project.Project
import com.intellij.testFramework.UsefulTestCase.*
import com.intellij.testIntegration.LocationTestRunner
import com.intellij.testIntegration.SelectTestStep
import org.junit.Test
import org.mockito.Mockito.mock
import java.util.*

class RecentTestsStepTest {
  val project = mock(Project::class.java)
  val runner = mock(LocationTestRunner::class.java)
  
  @Test
  fun `suites comes before their children tests`() {
    val map: MutableMap<String, TestStateStorage.Record> = hashMapOf()
    val now = Date()
    map.put("java:suite://JavaFormatterSuperDuperTest", TestStateStorage.Record(1, now))  
    map.put("java:test://Test.textXXX", TestStateStorage.Record(1, now))  
    map.put("java:suite://Test", TestStateStorage.Record(1, now))  
    map.put("java:test://Test.textYYY", TestStateStorage.Record(1, now))  
    map.put("java:test://Test.textZZZ", TestStateStorage.Record(1, now))  
    map.put("java:test://JavaFormatterSuperDuperTest.testItMakesMeSadToFixIt", TestStateStorage.Record(1, now))  
    map.put("java:test://Test.textQQQ", TestStateStorage.Record(1, now))  
    map.put("java:test://JavaFormatterSuperDuperTest.testUnconditionalAlignmentErrorneous", TestStateStorage.Record(1, now))  
    
    val step = SelectTestStep(project, map, runner)

    val expected = listOf(
        "java:suite://JavaFormatterSuperDuperTest",
        "java:test://JavaFormatterSuperDuperTest.testItMakesMeSadToFixIt",
        "java:test://JavaFormatterSuperDuperTest.testUnconditionalAlignmentErrorneous",
        "java:suite://Test",
        "java:test://Test.textQQQ",
        "java:test://Test.textXXX",
        "java:test://Test.textYYY",
        "java:test://Test.textZZZ"
    )
    assertContainsOrdered(step.values, expected)
    assertSize(8, step.values)
  }
  
  
  @Test
  fun `shown value without protocol`() {
    val step = SelectTestStep(project, emptyMap(), runner)
    var shownValue = step.getTextFor("java:suite://JavaFormatterSuperDuperTest")
    assertEquals(shownValue, "JavaFormatterSuperDuperTest")
    
    shownValue = step.getTextFor("java:test://JavaFormatterSuperDuperTest.testItMakesMeSadToFixIt")
    assertEquals(shownValue, "JavaFormatterSuperDuperTest.testItMakesMeSadToFixIt")
  }
  
}