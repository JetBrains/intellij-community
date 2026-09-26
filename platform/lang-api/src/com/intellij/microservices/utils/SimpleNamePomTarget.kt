// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.microservices.utils

import com.intellij.pom.PomRenameableTarget

open class SimpleNamePomTarget(private var name: String) : PomRenameableTarget<Any?> {
  override fun setName(newName: String): Any? {
    name = newName
    return null
  }

  override fun getName(): String = name

  override fun isValid(): Boolean = true

  override fun isWritable(): Boolean = true

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    other as SimpleNamePomTarget
    return name == other.name
  }

  override fun hashCode(): Int = name.hashCode()

  override fun toString(): String = "${this.javaClass.simpleName}($name)"
}