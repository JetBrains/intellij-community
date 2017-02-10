/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.testIntegration

class UrlsCollector : TestEntryVisitor() {
  val urls = mutableListOf<String>()

  override fun visitSuite(suite: SuiteEntry) {
    urls.add(suite.suiteUrl)
    suite.tests.forEach { urls.add(it.url) }
  }

  override fun visitRunConfiguration(configuration: RunConfigurationEntry) {
    configuration.suites.forEach { visitSuite(it) }
  }
}


class SingleTestCollector : TestEntryVisitor() {
  val tests = mutableListOf<SingleTestEntry>()

  override fun visitTest(test: SingleTestEntry) {
    tests.add(test)
  }

  override fun visitSuite(suite: SuiteEntry) {
    suite.tests.forEach { it.accept(this) }
  }

  override fun visitRunConfiguration(configuration: RunConfigurationEntry) {
    configuration.suites.forEach { it.accept(this) }
  }
}


class ConfigurationsCollector : TestEntryVisitor() {
  val entries = mutableListOf<RecentTestsPopupEntry>()

  override fun visitRunConfiguration(configuration: RunConfigurationEntry) {
    entries.add(configuration)
  }

  override fun visitSuite(suite: SuiteEntry) {
    entries.add(suite)
  }
}


class TestConfigurationCollector : TestEntryVisitor() {
  private val items = mutableListOf<RecentTestsPopupEntry>()
  
  fun getEnclosingConfigurations(): List<RecentTestsPopupEntry> = items
  
  override fun visitTest(test: SingleTestEntry) {
    val configurationEntry = test.suite?.runConfigurationEntry ?: RunConfigurationEntry(test.runConfiguration)
    items.add(configurationEntry)
    if (test.suite != null && configurationEntry.suites.size > 1) {
      items.add(0, test.suite!!)
    }
  }
  
}