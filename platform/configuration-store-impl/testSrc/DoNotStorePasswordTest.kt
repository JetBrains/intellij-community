// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.testFramework.ProjectRule
import com.intellij.util.xmlb.XmlSerializerUtil
import org.jdom.Attribute
import org.jdom.Element
import org.junit.ClassRule
import org.junit.Test
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

class DoNotStorePasswordTest {
  companion object {
    @JvmField
    @ClassRule
    val projectRule = ProjectRule()
  }

  @Test
  fun printPasswordComponents() {
    val processor: (componentClass: Class<*>, plugin: PluginDescriptor?) -> Unit = p@{ aClass, _ ->
      val stateAnnotation = getStateSpec(aClass)
      if (stateAnnotation == null || stateAnnotation.name.isEmpty()) {
        return@p
      }

      for (i in aClass.genericInterfaces) {
        if (checkType(i)) {
          return@p
        }
      }

      // public static class Project extends WebServersConfigManagerBaseImpl<WebServersConfigManagerBaseImpl.State> {
      // so, we check not only PersistentStateComponent
      checkType(aClass.genericSuperclass)
    }

    val app = ApplicationManager.getApplication() as ComponentManagerImpl
    app.processAllImplementationClasses(processor)
    // yes, we don't use default project here to be sure
    (projectRule.project as ComponentManagerImpl).processAllImplementationClasses(processor)

    for (container in listOf(app, projectRule.project as ComponentManagerImpl)) {
      container.processAllImplementationClasses { aClass, _ ->
        if (PersistentStateComponent::class.java.isAssignableFrom(aClass)) {
          processor(aClass, null)
        }
      }
    }
  }

  fun check(clazz: Class<*>) {
    @Suppress("DEPRECATION")
    if (clazz.isEnum || clazz === Attribute::class.java || clazz === Element::class.java ||
        clazz === java.lang.String::class.java || clazz === java.lang.Integer::class.java || clazz === java.lang.Boolean::class.java ||
        Map::class.java.isAssignableFrom(clazz) || com.intellij.openapi.util.JDOMExternalizable::class.java.isAssignableFrom(clazz)) {
      return
    }

    for (accessor in XmlSerializerUtil.getAccessors(clazz)) {
      val name = accessor.name
      if (BaseXmlOutputter.doesNameSuggestSensitiveInformation(name)) {
        if (clazz.typeName != "com.intellij.docker.registry.DockerRegistry") {
          throw RuntimeException("${clazz.typeName}.${accessor.name}")
        }
      }
      else if (!accessor.valueClass.isPrimitive) {
        if (Collection::class.java.isAssignableFrom(accessor.valueClass)) {
          val genericType = accessor.genericType
          if (genericType is ParameterizedType) {
            val type = genericType.actualTypeArguments[0]
            if (type is Class<*>) {
              check(type)
            }
          }
        }
        else if (accessor.valueClass != clazz) {
          check(accessor.valueClass)
        }
      }
    }
  }

  private fun checkType(type: Type): Boolean {
    if (type !is ParameterizedType || !PersistentStateComponent::class.java.isAssignableFrom(type.rawType as Class<*>)) {
      return false
    }

    type.actualTypeArguments[0].let {
      if (it is ParameterizedType) {
        check(it.rawType as Class<*>)
      }
      else {
        check(it as Class<*>)
      }
    }
    return true
  }
}