package com.intellij.codeInspection.tests

import com.intellij.codeInspection.DependencyInspection

abstract class DependencyInspectionTestBase : UastInspectionTestBase() {
  override val inspection = DependencyInspection()
}