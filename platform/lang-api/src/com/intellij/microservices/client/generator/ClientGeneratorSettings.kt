package com.intellij.microservices.client.generator

data class ClientGeneratorSetting(
  var boilerplate: Boolean = false,
  var frameworkLanguage: String? = null,
  var frameworkVersion: String? = null
)

interface AvailableClientSettings {
  val boilerplateAvailable: Boolean
    get() = false
  val frameworkLanguages: Set<String>
    get() = emptySet()
  val frameworkVersions: Set<String>
    get() = emptySet()
  val actualClientSettings: ClientGeneratorSetting
    get() = ClientGeneratorSetting()
}

internal val EMPTY_SETTINGS: AvailableClientSettings = object : AvailableClientSettings {}