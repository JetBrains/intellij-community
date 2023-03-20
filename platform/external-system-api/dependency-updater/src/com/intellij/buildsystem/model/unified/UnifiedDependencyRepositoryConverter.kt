package com.intellij.buildsystem.model.unified

import com.intellij.buildsystem.model.BuildDependencyRepository

interface UnifiedDependencyRepositoryConverter<T : BuildDependencyRepository> {
    fun convert(buildDependencyRepository: T): UnifiedDependencyRepository
}
