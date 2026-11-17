// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jvm.analysis.internal.testFramework

import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.refactoring.migration.MigrationMap
import com.intellij.refactoring.migration.MigrationMapEntry
import com.intellij.refactoring.migration.MigrationProcessor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.Assertions.assertEquals

/**
 * The fixture of a package and class migration test. Create it with [migrationFixture].
 */
@TestOnly
class MigrationFixture internal constructor(
  /**
   * The code insight fixture of the test.
   */
  val fixture: LightJavaCodeInsightTestFixture,
) {
  /**
   * Configures [before] as a file of [lang], runs the migration of [migrations] on the EDT, and checks that the file text is [after].
   */
  suspend fun migrationTest(lang: JvmLanguage, before: String, after: String, vararg migrations: MigrationMapEntry) {
    val migrationMap = MigrationMap(migrations)
    fixture.configureByText("UnderTest${lang.ext}", before)
    withContext(Dispatchers.EDT) {
      MigrationProcessor(fixture.project, migrationMap).run()
    }
    assertEquals(after, readAction { fixture.file.text })
  }
}

/**
 * Creates a [MigrationFixture] for one test. It replaces [MigrationTestBase]. Declare it as an instance field.
 */
@TestOnly
fun migrationFixture(projectFixture: TestFixture<LightJavaProject>): TestFixture<MigrationFixture> = testFixture("migrationFixture") {
  initialized(MigrationFixture(lightJavaCodeInsightFixture(projectFixture).init())) {}
}
