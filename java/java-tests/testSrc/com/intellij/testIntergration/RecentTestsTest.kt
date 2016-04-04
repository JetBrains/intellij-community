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

import com.intellij.execution.Location
import com.intellij.execution.TestStateStorage
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testFramework.LightIdeaTestCase
import com.intellij.testIntegration.*
import org.assertj.core.api.Assertions.assertThat
import org.mockito.Matchers
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.util.*

fun passed(date: Date) = TestStateStorage.Record(TestStateInfo.Magnitude.PASSED_INDEX.value, date)
fun failed(date: Date) = TestStateStorage.Record(TestStateInfo.Magnitude.FAILED_INDEX.value, date)

class RecentTestsStepTest: LightIdeaTestCase() {
  val runner = mock(RecentTestRunner::class.java)

  val passed = passed(Date(0))
  val failed = failed(Date(0))
  
  class TestStorage {
    private val map: MutableMap<String, TestStateStorage.Record> = hashMapOf()
    
    fun addSuite(name: String, pass: Boolean, date: Date = Date(0), language: String = "java") {
      val magnitude = if (pass) TestStateInfo.Magnitude.PASSED_INDEX else TestStateInfo.Magnitude.FAILED_INDEX
      addSuite(name, magnitude, date, language)
    }
    
    fun addSuite(name: String, magnitude: TestStateInfo.Magnitude, date: Date = Date(0), language: String = "java") {
      val record = TestStateStorage.Record(magnitude.value, date)
      map.put("$language:suite://$name", record)
    }
    
    fun addTest(name: String, magnitude: TestStateInfo.Magnitude, date: Date = Date(0)) {
      val record = TestStateStorage.Record(magnitude.value, date)
      map.put("java:test://$name", record)
    }

    fun addTest(name: String, pass: Boolean, date: Date = Date(0)) {
      val magnitude = if (pass) TestStateInfo.Magnitude.PASSED_INDEX else TestStateInfo.Magnitude.FAILED_INDEX
      addTest(name, magnitude, date)
    }
    
    fun getMap() = map

    fun getSuite(name: String, language: String = "java") = map["$language:suite://$name"]
    
    fun removeUrl(url: String) {
      map.remove(url)
    }
  }
  
  fun `test show sorted by date`() {
    val storage = TestStorage()
    
    storage.addSuite("ASTest", true, Date(1000))
    storage.addSuite("JSTest", true, Date(1200))
    
    val sortedUrlList = getSortedList(storage.getMap())
    val values = sortedUrlList.map { VirtualFileManager.extractPath(it) }
    
    assertThat(values).isEqualTo(listOf("JSTest", "ASTest"))
  }
  
  fun `test show tests sorted by date`() {
    val storage = TestStorage()
    
    storage.addSuite("ASTest", false, Date(0))
    storage.addTest("ASTest.xxxx", true, Date(99999))

    storage.addTest("ASTest.aaaa", false, Date(10000))
    storage.addTest("ASTest.cccc", false, Date(20000))
    storage.addTest("ASTest.bbbb", false, Date(30000))
    
    val sortedUrlList = getSortedList(storage.getMap())
    val values = sortedUrlList.map { VirtualFileManager.extractPath(it) }

    assertThat(values).isEqualTo(listOf("ASTest", "ASTest.bbbb", "ASTest.cccc", "ASTest.aaaa"))
  }
  
  fun `test show ignored`() {
    val storage = TestStorage()
    storage.addSuite("ASTest", TestStateInfo.Magnitude.IGNORED_INDEX)
    storage.addTest("ASTest.ignored", TestStateInfo.Magnitude.IGNORED_INDEX)
    storage.addTest("ASTest.passed", pass = true)
    
    val sortedUrlList = getSortedList(storage.getMap())
    val values = sortedUrlList.map { VirtualFileManager.extractPath(it) }

    assertThat(values).isEqualTo(listOf("ASTest"))
  }
  
  fun `test when suite passed - show only suite`() {
    val map: MutableMap<String, TestStateStorage.Record> = hashMapOf()

    map.put("java:suite://JavaFormatterSuperDuperTest", passed)
    map.put("java:test://Test.textXXX", passed)
    map.put("java:suite://Test", passed)
    map.put("java:test://Test.textYYY", passed)
    map.put("java:test://Test.textZZZ", passed)
    map.put("java:test://JavaFormatterSuperDuperTest.testItMakesMeSadToFixIt", passed)
    map.put("java:test://Test.textQQQ", passed)
    map.put("java:test://JavaFormatterSuperDuperTest.testUnconditionalAlignmentErrorneous", passed)  
    
    val expected = listOf(
        "java:suite://JavaFormatterSuperDuperTest",
        "java:suite://Test"
    )
    
    val list = getSortedList(map)
    assertThat(list).isEqualTo(expected)
  }

  private fun getSortedList(map: MutableMap<String, TestStateStorage.Record>): List<String> {
    val provider = RecentTestsListProvider(map)
    return provider.urlsToShowFromHistory
  }

  
  fun `test show only java tests`() {
    val storage = TestStorage()
    
    storage.addSuite("JavaTest1", true)
    storage.addSuite("JavaTest2", true)
    
    storage.addSuite("JsSuite1", true, Date(0), "js")
    storage.addSuite("JsSuite2", true, Date(0), "js")
    storage.addSuite("JsSuite3", true, Date(0), "js")
    
    val values = getSortedList(storage.getMap())
    assertThat(values.map { VirtualFileManager.extractPath(it) }).isEqualTo(listOf("JavaTest1", "JavaTest2"))
  }
  
  fun `test show failed first`() {
    val map: MutableMap<String, TestStateStorage.Record> = hashMapOf()

    map.put("java:suite://JavaFormatterSuperDuperTest", failed)
    map.put("java:test://Test.textXXX", passed)
    map.put("java:suite://Test", passed)
    map.put("java:suite://JavaFormatterFailed", failed)
    map.put("java:test://JavaFormatterFailed.fail", failed)
    map.put("java:test://JavaFormatterFailed.notFail", passed)
    map.put("java:test://Test.textYYY", passed)
    map.put("java:test://Test.textZZZ", passed)
    map.put("java:test://JavaFormatterSuperDuperTest.testFail", failed)
    map.put("java:test://Test.textQQQ", passed)
    map.put("java:test://JavaFormatterSuperDuperTest.testUnconditionalAlignmentErrorneous", passed)
    
    val values = getSortedList(map)
    
    val expected = listOf(
        "java:test://JavaFormatterFailed.fail",
        "java:suite://JavaFormatterFailed",
        "java:test://JavaFormatterSuperDuperTest.testFail",
        "java:suite://JavaFormatterSuperDuperTest",
        "java:suite://Test"
    )
    
    assertThat(values).isEqualTo(expected)
  }
  
  fun `test if failed more than 2 tests show suite first`() {
    val storage = TestStorage()
    storage.addSuite("ASTest", false)
    storage.addTest("ASTest.failed1", false, Date(3000))
    storage.addTest("ASTest.failed2", false, Date(2000))
    storage.addTest("ASTest.failed3", false, Date(1000))
    storage.addTest("ASTest.passed1", true)

    val sortedUrlList = getSortedList(storage.getMap())
    val values = sortedUrlList.map { VirtualFileManager.extractPath(it) }

    assertThat(values).isEqualTo(listOf(
        "ASTest",
        "ASTest.failed1",
        "ASTest.failed2",
        "ASTest.failed3"
    ))
  }
  
  fun `test if failed less than 3 tests, show tests first`() {
    val storage = TestStorage()

    storage.addSuite("ASTest", false)
    storage.addTest("ASTest.failed1", false, Date(3000))
    storage.addTest("ASTest.failed2", false, Date(2000))
    storage.addTest("ASTest.passed1", true)

    val sortedUrlList = getSortedList(storage.getMap())
    val values = sortedUrlList.map { VirtualFileManager.extractPath(it) }

    assertThat(values).isEqualTo(listOf(
        "ASTest.failed1",
        "ASTest.failed2",
        "ASTest"
    ))
  }
  
  fun `test if all failed show only suite`() {
    val storage = TestStorage()

    storage.addSuite("ASTest", false)
    storage.addTest("ASTest.failed1", false)
    storage.addTest("ASTest.failed2", false)
    storage.addTest("ASTest.failed3", false)
    storage.addTest("ASTest.failed4", false)

    val sortedUrlList = getSortedList(storage.getMap())
    val values = sortedUrlList.map { VirtualFileManager.extractPath(it) }

    assertThat(values).isEqualTo(listOf("ASTest"))
  }
  
  private fun locatorReturningNullIfContains(substring: String): TestLocator {
    val locator = mock(TestLocator::class.java)
    `when`(locator.getLocation(Matchers.anyString())).thenAnswer {
      val url = it.arguments[0] as String
      if (url.contains(substring)) null else mock(Location::class.java)
    }
    return locator
  }
  
  fun `test shown value without protocol`() {
    val step = SelectTestStep(emptyList(), emptyMap(), runner, mock(TestLocator::class.java))
    var shownValue = step.getTextFor("java:suite://JavaFormatterSuperDuperTest")
    assertThat(shownValue).isEqualTo("JavaFormatterSuperDuperTest")
    
    shownValue = step.getTextFor("java:test://JavaFormatterSuperDuperTest.testItMakesMeSadToFixIt")
    assertThat(shownValue).isEqualTo("JavaFormatterSuperDuperTest.testItMakesMeSadToFixIt")
  }

  fun `test do not show urls which we can locate without location`() {
    val storage = TestStorage()

    storage.addSuite("ASTest", true)
    storage.addSuite("BSTest", false)
    storage.addTest("BSTest.fff", false)
    storage.addTest("BSTest.ppp", true)

    storage.addTest("<default package>", false)
    storage.addSuite("<default package>", false)

    val testStorageMock = createMockStorage(storage)

    val map = storage.getMap()

    val cleaner = DeadTestsCleaner(testStorageMock, map.keys.toList(), locatorReturningNullIfContains("<"))
    cleaner.run()

    val sortedUrlList = getSortedList(storage.getMap())
    val values = sortedUrlList.map { VirtualFileManager.extractPath(it) }

    assertThat(values).isEqualTo(listOf("BSTest.fff", "BSTest", "ASTest"))
    assertThat(storage.getSuite("<default package>")).isEqualTo(null)
  }

  fun `test do not remove tests if we are unable to locate them`() {
    val storage = TestStorage()

    storage.addSuite("ASTest", true)
    
    storage.addSuite("JsSuite1", true, Date(), "js")
    storage.addSuite("JsSuite2", true, Date(), "js")
    
    val testStorageMock = createMockStorage(storage)
    
    val sortedUrlList = getSortedList(storage.getMap())
    
    val cleaner = DeadTestsCleaner(testStorageMock, sortedUrlList, locatorReturningNullIfContains("JsSuite"))
    cleaner.run()

    assertThat(storage.getSuite("JsSuite1", "js")).isNotEqualTo(null)
    assertThat(storage.getSuite("JsSuite2", "js")).isNotEqualTo(null)
  }

  private fun createMockStorage(storage: TestStorage): TestStateStorage? {
    val testStorageMock = mock(TestStateStorage::class.java)
    `when`(testStorageMock.removeState(Matchers.anyString())).then {
      val url = it.arguments[0]as String
      storage.removeUrl(url)
    }
    return testStorageMock
  }


}