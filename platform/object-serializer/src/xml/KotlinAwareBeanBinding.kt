// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.serialization.xml

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.diagnostic.logger
import com.intellij.serialization.BaseBeanBinding
import com.intellij.serialization.PropertyAccessor
import com.intellij.util.ObjectUtils
import com.intellij.util.xmlb.BeanBinding
import com.intellij.util.xmlb.SerializationFilter
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntList
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class KotlinAwareBeanBinding(beanClass: Class<*>) : BeanBinding(beanClass) {
  private val beanBinding = BaseBeanBinding(beanClass)

  // only for accessor, not field
  private fun findBindingIndex(name: String): Int {
    // accessors sorted by name
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

  override fun serializeInto(o: Any, element: Element?, filter: SerializationFilter?): Element? {
    return when (o) {
      is BaseState -> serializeBaseStateInto(o, element, filter)
      else -> super.serializeInto(o, element, filter)
    }
  }

  fun serializeBaseStateInto(o: BaseState,
                             @Suppress("LocalVariableName") _element: Element?,
                             filter: SerializationFilter?,
                             excludedPropertyNames: Collection<String>? = null): Element? {
    var element = _element
    // order of bindings must be used, not order of properties
    var bindingIndices: IntList? = null
    for (property in o.__getProperties()) {
      val propertyName = property.name!!

      if (property.isEqualToDefault() || (excludedPropertyNames != null && excludedPropertyNames.contains(propertyName))) {
        continue
      }

      val propertyBindingIndex = findBindingIndex(propertyName)
      if (propertyBindingIndex < 0) {
        logger<BaseState>().debug("cannot find binding for property ${propertyName}")
        continue
      }

      if (bindingIndices == null) {
        bindingIndices = IntArrayList()
      }
      bindingIndices.add(propertyBindingIndex)
    }

    if (bindingIndices != null) {
      bindingIndices.sort()
      for (i in 0 until bindingIndices.size) {
        element = serializePropertyInto(bindings[bindingIndices.getInt(i)], o, element, filter, false)
      }
    }
    return element
  }

  override fun newInstance(): Any {
    return beanBinding.newInstance()
  }
}