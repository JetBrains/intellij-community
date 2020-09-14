package com.intellij.tools.launch

interface ModulesProvider {
  val mainModule: String
  val additionalModules: List<String> get() = listOf()
  val excludedModules: List<String> get() = listOf()
  val includeTestDependencies: Boolean get() = false
}