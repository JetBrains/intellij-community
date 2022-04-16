package com.intellij.buildsystem.model

interface OperationFailureException<T : OperationItem>

class DependencyConflictException(dependency: BuildDependency) : RuntimeException(
        "The dependency $dependency or an equivalent one is already declared in the build file"
), OperationFailureException<BuildDependency>

class RepositoryConflictException(repository: BuildDependencyRepository) : RuntimeException(
        "The repository $repository or an equivalent one is already declared in the build file"
), OperationFailureException<BuildDependencyRepository>

class DependencyNotFoundException(dependency: BuildDependency) : RuntimeException(
        "The dependency $dependency or an equivalent is not declared in the build file"
), OperationFailureException<BuildDependency>

class RepositoryNotFoundException(repository: BuildDependencyRepository) : RuntimeException(
        "The repository $repository or an equivalent is not declared in the build file"
), OperationFailureException<BuildDependencyRepository>
