package com.intellij.tools.launch

interface ModulesProvider {
  val mainModule: String
  val additionalModules: List<String>
  val excludedModules: List<String>
  val includeTestDependencies: Boolean get() = false
}