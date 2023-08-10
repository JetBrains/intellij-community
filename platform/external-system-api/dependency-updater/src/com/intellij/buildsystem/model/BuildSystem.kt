package com.intellij.buildsystem.model

interface BuildSystem<D : BuildDependency, R : BuildDependencyRepository> : BuildManager<D, R> {

    val name: String
}
