package com.intellij.ide.customize.transferSettings.providers.vswin.utilities.registryUtils

interface IRegistryKey {
    fun withSuffix(suffix: String): IRegistryKey
    fun inChild(child: String): IRegistryKey
    operator fun div(child: String) = inChild(child)
    fun getStringValue(value: String): String?
    fun getKeys(): List<String>?
    fun getValues(): Map<String, Any>?
}