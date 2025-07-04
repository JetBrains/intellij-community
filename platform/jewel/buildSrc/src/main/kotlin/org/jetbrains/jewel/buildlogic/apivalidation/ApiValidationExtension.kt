package org.jetbrains.jewel.buildlogic.apivalidation

import org.gradle.api.provider.SetProperty

interface ApiValidationExtension {
    val excludedClassRegexes: SetProperty<String>
}
