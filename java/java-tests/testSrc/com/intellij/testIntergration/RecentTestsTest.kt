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
import com.intellij.execution.testframework.JavaTestLocator
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testIntegration.RecentTestRunner
import com.intellij.testIntegration.SelectTestStep
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.mockito.Matchers
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.util.*

class RecentTestsStepTest {
  val runner = createRunner()

  val passed = TestStateStorage.Record(TestStateInfo.Magnitude.PASSED_INDEX.value, Date())
  val failed = TestStateStorage.Record(TestStateInfo.Magnitude.FAILED_INDEX.value, Date()) 
  
  @Test
  fun `when suite passed - show only suite`() {
    val map: MutableMap<String, TestStateStorage.Record> = hashMapOf()

    map.put("java:suite://JavaFormatterSuperDuperTest", passed)
    map.put("java:test://Test.textXXX", passed)
    map.put("java:suite://Test", passed)
    map.put("java:test://Test.textYYY", passed)
    map.put("java:test://Test.textZZZ", passed)
    map.put("java:test://JavaFormatterSuperDuperTest.testItMakesMeSadToFixIt", passed)
    map.put("java:test://Test.textQQQ", passed)
    map.put("java:test://JavaFormatterSuperDuperTest.testUnconditionalAlignmentErrorneous", passed)  
    
    val step = SelectTestStep(map, runner)

    val expected = listOf(
        "java:suite://JavaFormatterSuperDuperTest",
        "java:suite://Test"
    )
    
    assertThat(step.values).isEqualTo(expected)
  }


  @Test
  fun `show failed first`() {
    val map: MutableMap<String, TestStateStorage.Record> = hashMapOf()

    map.put("java:suite://JavaFormatterSuperDuperTest", failed)
    map.put("java:test://Test.textXXX", passed)
    map.put("java:suite://Test", passed)
    map.put("java:suite://JavaFormatterFailed", failed)
    map.put("java:test://JavaFormatterFailed.fail", failed)
    map.put("java:test://Test.textYYY", passed)
    map.put("java:test://Test.textZZZ", passed)
    map.put("java:test://JavaFormatterSuperDuperTest.testFail", failed)
    map.put("java:test://Test.textQQQ", passed)
    map.put("java:test://JavaFormatterSuperDuperTest.testUnconditionalAlignmentErrorneous", passed)
    
    val step = SelectTestStep(map, runner)
    
    val expected = listOf(
        "java:suite://JavaFormatterFailed",
        "java:test://JavaFormatterFailed.fail",
        "java:suite://JavaFormatterSuperDuperTest",
        "java:test://JavaFormatterSuperDuperTest.testFail",
        "java:suite://Test"
    )
    
    assertThat(step.values).isEqualTo(expected)
  }

  private fun createRunner(): RecentTestRunner {
    val runner = mock(RecentTestRunner::class.java)
    `when`(runner.isSuite(Matchers.anyString())).thenAnswer {
      val url = it.arguments[0] as String
      val protocol = VirtualFileManager.extractProtocol(url)
      JavaTestLocator.SUITE_PROTOCOL.startsWith(protocol.toString())
    }
    return runner
  }

  @Test
  fun `shown value without protocol`() {
    val step = SelectTestStep(emptyMap(), runner)
    var shownValue = step.getTextFor("java:suite://JavaFormatterSuperDuperTest")
    assertThat(shownValue).isEqualTo("JavaFormatterSuperDuperTest")
    
    shownValue = step.getTextFor("java:test://JavaFormatterSuperDuperTest.testItMakesMeSadToFixIt")
    assertThat(shownValue).isEqualTo("JavaFormatterSuperDuperTest.testItMakesMeSadToFixIt")
  }
  
}