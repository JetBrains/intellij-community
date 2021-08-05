package com.intellij.codeInspection.tests

import com.intellij.codeInspection.DependencyInspection

abstract class DependencyInspectionTestBase : UastInspectionTestBase(inspection) {
  companion object {
    private val inspection = DependencyInspection()
  }
}