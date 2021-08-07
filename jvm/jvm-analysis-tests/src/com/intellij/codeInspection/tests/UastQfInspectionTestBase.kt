package com.intellij.codeInspection.tests

import com.intellij.codeInspection.InspectionProfileEntry

abstract class UastQfInspectionTestBase(inspection: InspectionProfileEntry) : UastInspectionTestBase(inspection) {
  abstract val fileExt: String

  protected fun doQfAvailableTest(name: String, hint: String) {
    myFixture.configureByFile("$name.$fileExt")
    val action = myFixture.getAvailableIntention(hint) ?: throw AssertionError("Quickfix '$hint' is not available.")
    myFixture.launchAction(action)
    myFixture.checkResultByFile("$name.after.$fileExt")
  }

  protected fun doQfUnavailableTest(name: String, hint: String) {
    myFixture.configureByFile("$name.$fileExt")
    assertEmpty("Quickfix '$hint' is available but should not.", myFixture.filterAvailableIntentions(hint))
  }
}