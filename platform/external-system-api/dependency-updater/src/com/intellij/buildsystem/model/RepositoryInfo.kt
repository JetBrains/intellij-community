package com.intellij.buildsystem.model

open class RepositoryInfo<T : BuildDependencyRepository>(
    open val repository: T,
    open val metadata: BuildScriptEntryMetadata
)
