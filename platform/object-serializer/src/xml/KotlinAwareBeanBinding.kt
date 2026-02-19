// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.serialization.xml

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.serialization.PropertyAccessor
import com.intellij.util.ObjectUtils
import com.intellij.util.xmlb.BeanBinding
import com.intellij.util.xmlb.SerializationFilter
import it.unimi.dsi.fastutil.ints.IntArrayList
import org.jdom.Element
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible

private val emptyConstructorMethodType = MethodType.methodType(Void.TYPE)
private val METHOD_LOOKUP = MethodHandles.lookup()

internal class KotlinAwareBeanBinding(beanClass: Class<*>) : BeanBinding(beanClass) {
  @Volatile
  private var instantiator: (() -> Any)? = null

  private fun resolveInstantiator(): () -> Any {
    val constructor = try {
      MethodHandles.privateLookupIn(beanClass, METHOD_LOOKUP).findConstructor(beanClass, emptyConstructorMethodType)
    }
    catch (e: NoSuchMethodException) {
      null
    }
    val instantiator = if (constructor == null) {
      {
        createUsingKotlin(beanClass)
      }
    }
    else {
      {
        constructor.invoke()
      }
    }
    this.instantiator = instantiator
    return instantiator
  }

  // only for accessor, not field
  private fun findBindingIndex(name: String): Int {
    // accessors sorted by name
    val bindings = bindings!!
    val index = ObjectUtils.binarySearch(0, bindings.size) { index -> bindings[index].accessor.name.compareTo(name) }
    if (index >= 0) {
      return index
    }

    for ((i, binding) in bindings.withIndex()) {
      val accessor = binding.accessor
      if (accessor is PropertyAccessor && accessor.getterName == name) {
        return i
      }
    }

    return -1
  }

  override fun serializeProperties(bean: Any, preCreatedElement: Element?, filter: SerializationFilter?): Element? {
    return when (bean) {
      is BaseState -> serializeBaseStateInto(bean = bean, _element = preCreatedElement, filter = filter)
      else -> super.serializeProperties(bean = bean, preCreatedElement = preCreatedElement, filter = filter)
    }
  }

  fun serializeBaseStateInto(
    bean: BaseState,
    @Suppress("LocalVariableName") _element: Element?,
    filter: SerializationFilter?,
    excludedPropertyNames: Collection<String>? = null,
  ): Element? {
    var element = _element
    // order of bindings must be used, not order of properties
    var bindingIndices: IntArrayList? = null
    for (property in bean.__getProperties()) {
      val propertyName = property.name!!
      if (property.isEqualToDefault() || (excludedPropertyNames != null && excludedPropertyNames.contains(propertyName))) {
        continue
      }

      val propertyBindingIndex = findBindingIndex(propertyName)
      if (propertyBindingIndex < 0) {
        logger<BaseState>().debug { "cannot find binding for property $propertyName" }
        continue
      }

      if (bindingIndices == null) {
        bindingIndices = IntArrayList()
      }
      bindingIndices.add(propertyBindingIndex)
    }

    if (bindingIndices != null) {
      val bindings = bindings!!
      bindingIndices.sort()
      for (i in 0 until bindingIndices.size) {
        element = serializeProperty(
          binding = bindings[bindingIndices.getInt(i)],
          bean = bean,
          parentElement = element,
          filter = filter,
          isFilterPropertyItself = false,
        )
      }
    }
    return element
  }

  override fun newInstance(): Any {
    instantiator?.let {
      return it()
    }
    return resolveInstantiator()()
  }
}

// ReflectionUtil uses another approach to do it - unreliable because located in the util module, where Kotlin cannot be used.
// Here we use Kotlin reflection, and this approach is more reliable because we are prepared for future Kotlin versions.
private fun createUsingKotlin(aClass: Class<*>): Any {
  val kClass = aClass.kotlin
  val kFunction = kClass.primaryConstructor ?: kClass.constructors.first()
  try {
    kFunction.isAccessible = true
  }
  catch (ignored: SecurityException) {
  }
  return kFunction.callBy(emptyMap())
}