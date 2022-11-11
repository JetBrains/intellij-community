package com.intellij.ide.customize.transferSettings.providers.vswin.utilities.registryUtils

interface IRegistryRoot {
    fun fromKey(key: String): IRegistryKey
}