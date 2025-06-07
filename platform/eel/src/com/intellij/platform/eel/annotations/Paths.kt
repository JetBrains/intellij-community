// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.annotations

/**
 * This annotation should be applied to strings that could be directly used to construct [java.nio.file.Path] instances.
 * These strings are either local to the IDE process or have prefix pointing to the specific environment.
 * This environment could be, for example, a WSL machine or a Docker container.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(
  AnnotationTarget.PROPERTY,
  AnnotationTarget.FIELD,
  AnnotationTarget.LOCAL_VARIABLE,
  AnnotationTarget.VALUE_PARAMETER,
  AnnotationTarget.PROPERTY_GETTER,
  AnnotationTarget.PROPERTY_SETTER,
  AnnotationTarget.TYPE,
)
annotation class MultiRoutingFileSystemPath

/**
 * This is the path within the specific environment.
 * For example, for a path in WSL it would be a Unix path within the WSL machine,
 * and for a path in a Docker container it would be a path within this Docker container.
 *
 * It should not be directly used in the [java.nio.file.Path] constructions methods [java.nio.file.Path.of] and [java.nio.file.Paths.get].
 *
 * @see NativeContext
 */
@Retention(AnnotationRetention.SOURCE)
@Target(
  AnnotationTarget.PROPERTY,
  AnnotationTarget.FIELD,
  AnnotationTarget.LOCAL_VARIABLE,
  AnnotationTarget.VALUE_PARAMETER,
  AnnotationTarget.PROPERTY_GETTER,
  AnnotationTarget.PROPERTY_SETTER,
  AnnotationTarget.TYPE,
)
annotation class NativePath

/**
 * Denotes that the annotated element represents a simple filename without any path components.
 * This annotation is meant to indicate that the corresponding string values must contain only
 * the bare filename (e.g., "file.txt") without any directory separators, drive letters,
 * or path components (e.g., not "C:\folder\file.txt" or "/home/user/file.txt").
 *
 * The annotation is retained only in the source code and is not visible in the compiled bytecode.
 *
 * Applicable targets include properties, fields, local variables, value parameters,
 * property getters, property setters, and type usage.
 */

@Retention(AnnotationRetention.SOURCE)
@Target(
  AnnotationTarget.PROPERTY,
  AnnotationTarget.FIELD,
  AnnotationTarget.LOCAL_VARIABLE,
  AnnotationTarget.VALUE_PARAMETER,
  AnnotationTarget.PROPERTY_GETTER,
  AnnotationTarget.PROPERTY_SETTER,
  AnnotationTarget.TYPE,
)
annotation class Filename

@Retention(AnnotationRetention.SOURCE)
@Target(
  AnnotationTarget.PROPERTY,
  AnnotationTarget.FIELD,
  AnnotationTarget.LOCAL_VARIABLE,
  AnnotationTarget.VALUE_PARAMETER,
  AnnotationTarget.PROPERTY_GETTER,
  AnnotationTarget.PROPERTY_SETTER,
  AnnotationTarget.TYPE,
)
annotation class LocalPath