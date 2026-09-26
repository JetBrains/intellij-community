package com.intellij.buildsystem.model

interface BuildDependency : OperationItem {
    val displayName: String

    interface Coordinates {
        val displayName: String
    }
}
