package com.intellij.jvm.analysis.internal.testFramework

import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.refactoring.migration.MigrationMap
import com.intellij.refactoring.migration.MigrationMapEntry
import com.intellij.refactoring.migration.MigrationProcessor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import junit.framework.TestCase

abstract class MigrationTestBase : LightJavaCodeInsightFixtureTestCase() {
  fun migrationTest(lang: JvmLanguage, before: String, after: String, vararg migrations: MigrationMapEntry) {
    val migrationMap = MigrationMap(migrations)
    myFixture.configureByText("UnderTest${lang.ext}", before)
    MigrationProcessor(project, migrationMap).run()
    TestCase.assertEquals(after, myFixture.file.text)
  }
}