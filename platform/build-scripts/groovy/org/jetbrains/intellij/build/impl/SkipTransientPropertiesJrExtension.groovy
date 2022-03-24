// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.fasterxml.jackson.jr.ob.JacksonJrExtension
import com.fasterxml.jackson.jr.ob.api.ExtensionContext
import com.fasterxml.jackson.jr.ob.api.ReaderWriterModifier
import com.fasterxml.jackson.jr.ob.impl.BeanPropertyIntrospector
import com.fasterxml.jackson.jr.ob.impl.JSONWriter
import com.fasterxml.jackson.jr.ob.impl.POJODefinition
import groovy.transform.CompileStatic

import java.beans.Transient

/**
 * This extension is needed to serialize Groovy classes until https://github.com/FasterXML/jackson-jr/issues/93 is fixed.
 */
@CompileStatic
class SkipTransientPropertiesJrExtension extends JacksonJrExtension {
  @Override
  protected void register(ExtensionContext context) {
    context.appendModifier(new ReaderWriterModifier() {
      @Override
      POJODefinition pojoDefinitionForSerialization(JSONWriter writeContext, Class<?> pojoType) {
        POJODefinition definition = BeanPropertyIntrospector.instance().pojoDefinitionForSerialization(writeContext, pojoType);
        def properties = definition.properties.findAll { it.getter.getAnnotation(Transient) == null }
        return definition.withProperties(properties)
      }
    })
  }
}
