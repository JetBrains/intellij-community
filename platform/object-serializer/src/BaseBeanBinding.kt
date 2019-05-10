// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.serialization

import java.lang.reflect.Constructor
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible

open class BaseBeanBinding(protected val beanClass: Class<*>) {
  // kotlin data class constructor is never cached, because we have (and it is good) very limited number of such classes
  @Volatile
  private var constructor: Constructor<*>? = null

  fun newInstance(): Any {
    var constructor = constructor
    if (constructor != null) {
      return constructor.newInstance()
    }

    try {
      constructor = beanClass.getDeclaredConstructor()!!
      try {
        constructor.isAccessible = true
      }
      catch (ignored: SecurityException) {
        return beanClass.newInstance()
      }

      val instance = constructor.newInstance()
      // cache only if constructor is valid and applicable
      this.constructor = constructor
      return instance
    }
    catch (e: RuntimeException) {
      return createUsingKotlin(beanClass) ?: throw e
    }
    catch (e: NoSuchMethodException) {
      return createUsingKotlin(beanClass) ?: throw e
    }
  }
}

// ReflectionUtil uses another approach to do it - unreliable because located in util module, where Kotlin cannot be used.
// Here we use Kotlin reflection and this approach is more reliable because we are prepared for future Kotlin versions.
private fun createUsingKotlin(clazz: Class<*>): Any? {
  // if cannot create data class
  val kClass = clazz.kotlin
  val kFunction = kClass.primaryConstructor ?: kClass.constructors.first()
  try {
    kFunction.isAccessible = true
  }
  catch (ignored: SecurityException) {
  }
  return kFunction.callBy(emptyMap())
}