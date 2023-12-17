// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.serialization

import java.lang.reflect.Constructor
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible

internal open class BaseBeanBinding(internal val beanClass: Class<*>) {
  // kotlin data class constructor is never cached, because we have (and it is a good) very limited number of such classes
  @Volatile
  private var constructor: Constructor<*>? = null

  @Throws(SecurityException::class, NoSuchMethodException::class)
  fun resolveConstructor(): Constructor<*> {
    var constructor = constructor
    if (constructor != null) {
      return constructor
    }

    constructor = beanClass.getDeclaredConstructor()
    constructor.isAccessible = true
    this.constructor = constructor
    return constructor
  }

  fun newInstance(): Any {
    val constructor = try {
      resolveConstructor()
    }
    catch (e: NoSuchMethodException) {
      return createUsingKotlin(beanClass)
    }
    return constructor.newInstance()
  }

  override fun toString(): String = "${javaClass.simpleName}(beanClass=$beanClass)"
}

// ReflectionUtil uses another approach to do it - unreliable because located in the util module, where Kotlin cannot be used.
// Here we use Kotlin reflection, and this approach is more reliable because we are prepared for future Kotlin versions.
private fun createUsingKotlin(clazz: Class<*>): Any {
  // if we cannot create a data class
  val kClass = clazz.kotlin
  val kFunction = kClass.primaryConstructor ?: kClass.constructors.first()
  try {
    kFunction.isAccessible = true
  }
  catch (ignored: SecurityException) {
  }
  return kFunction.callBy(emptyMap())
}