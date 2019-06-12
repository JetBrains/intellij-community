// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization

import com.amazon.ion.Timestamp
import com.intellij.util.containers.ContainerUtil
import gnu.trove.THashMap
import java.io.File
import java.lang.reflect.*
import java.nio.file.FileSystems
import java.nio.file.Path
import java.util.*

internal typealias NestedBindingFactory = (accessor: MutableAccessor) -> Binding
internal typealias RootBindingFactory = () -> Binding

internal class IonBindingProducer(override val propertyCollector: PropertyCollector) : BindingProducer() {
  companion object {
    private val classToNestedBindingFactory = THashMap<Class<*>, NestedBindingFactory>(32, ContainerUtil.identityStrategy())
    private val classToRootBindingFactory = THashMap<Class<*>, RootBindingFactory>(32, ContainerUtil.identityStrategy())

    init {
      // for root resolved factory doesn't make sense because root bindings will be cached
      classToRootBindingFactory.put(File::class.java) { FileBinding() }
      classToRootBindingFactory.put(Path::class.java) { PathBinding() }
      classToRootBindingFactory.put(Date::class.java) { DateBinding() }
      classToRootBindingFactory.put(ByteArray::class.java) { ByteArrayBinding() }

      val numberFactory = ::NumberAsObjectBinding
      classToRootBindingFactory.put(java.lang.Short::class.java, numberFactory)
      classToRootBindingFactory.put(java.lang.Integer::class.java, numberFactory)
      classToRootBindingFactory.put(java.lang.Long::class.java, numberFactory)

      registerPrimitiveBindings(classToRootBindingFactory, classToNestedBindingFactory)

      classToRootBindingFactory.forEachEntry { key, factory ->
        classToNestedBindingFactory.put(key) { factory() }
        true
      }
    }
  }

  override fun createRootBinding(aClass: Class<*>?, type: Type): Binding {
    val customFactory = classToRootBindingFactory.get(aClass)
    if (customFactory != null) {
      return customFactory.invoke()
    }

    if (aClass == null) {
      val genericArrayType = type as GenericArrayType
      val typeVariable = genericArrayType.genericComponentType as TypeVariable<*>
      return ArrayBinding(typeVariable.bounds[0] as Class<*>, this)
    }

    return when {
      Collection::class.java.isAssignableFrom(aClass) -> {
        CollectionBinding(type as ParameterizedType, this)
      }
      Map::class.java.isAssignableFrom(aClass) -> {
        val typeArguments = (type as ParameterizedType).actualTypeArguments
        MapBinding(typeArguments[0], typeArguments[1], this)
      }
      aClass.isArray -> {
        ArrayBinding(aClass.componentType, this)
      }
      aClass.isEnum -> {
        @Suppress("UNCHECKED_CAST")
        EnumBinding(aClass as Class<out Enum<*>>)
      }
      aClass.isInterface || Modifier.isAbstract(aClass.modifiers) || aClass == Object::class.java -> {
        PolymorphicBinding(aClass)
      }
      java.lang.Number::class.java.isAssignableFrom(aClass) -> NumberAsObjectBinding()
      aClass is Proxy -> {
        throw SerializationException("$aClass class is not supported")
      }
      aClass == Class::class.java -> {
        // Class can be supported, but it will be implemented only when will be a real use case
        throw SerializationException("$aClass class is not supported")
      }
      else -> {
        BeanBinding(aClass)
      }
    }
  }

  // note about field name - Ion binary writer interns string automatically, no need to intern (text writer doesn't support symbol tables)
  override fun getNestedBinding(accessor: MutableAccessor): Binding {
    val type = accessor.genericType

    val isGenericArray = type is GenericArrayType
          //GenericArrayType genericArrayType = (GenericArrayType)type;
          //TypeVariable typeVariable = (TypeVariable)genericArrayType.getGenericComponentType();
          //return (Class<?>)typeVariable.getBounds()[0];

    // PrimitiveBinding can serialize conditionally, but for the sake of optimization, use special bindings to avoid comparison for each value
    // yes - if field typed as Object, Number and Boolean types are not supported (because bean binding will be created)
    if (isGenericArray) {
      return getRootBinding(null, type)
    }

    val aClass = ClassUtil.typeToClass(type)
    classToNestedBindingFactory.get(aClass)?.let {
      return it(accessor)
    }

    return when {
      aClass.isInterface || Modifier.isAbstract(aClass.modifiers) || aClass == Object::class.java -> {
        val annotation = accessor.getAnnotation(Property::class.java)
        if (annotation == null) {
          // todo respect configuration
          return getRootBinding(aClass, type)
          // throw SerializationException("Allowed types are not specified", linkedMapOf("accessor" to accessor))
        }

        // even if annotation in Java, Kotlin forces to use Klass, so, use java bridge
        val allowedTypes = PropertyAnnotationUtil.getAllowedClass(annotation)
        if (allowedTypes.isEmpty()) {
          throw SerializationException("Allowed types list is empty", linkedMapOf("accessor" to accessor))
        }
        InterfacePropertyBinding(allowedTypes)
      }
      else -> {
        getRootBinding(aClass, type)
      }
    }
  }
}

private class FileBinding : Binding {
  override fun deserialize(context: ReadContext): Any {
    return File(context.reader.stringValue())
  }

  override fun serialize(obj: Any, context: WriteContext) {
    context.writer.writeSymbol((obj as File).path)
  }
}

private class PathBinding : Binding {
  override fun deserialize(context: ReadContext): Any {
    return FileSystems.getDefault().getPath(context.reader.stringValue())
  }

  override fun serialize(obj: Any, context: WriteContext) {
    context.writer.writeSymbol((obj as Path).toString())
  }
}

private class EnumBinding(private val valueClass: Class<out Enum<*>>) : Binding {
  override fun deserialize(context: ReadContext): Any {
    val enumConstants = valueClass.enumConstants
    val value = context.reader.stringValue()
    return enumConstants.firstOrNull { it.name == value }
           ?: enumConstants.firstOrNull { it.name.equals(value, ignoreCase = true) }
           ?: enumConstants.first()
  }

  override fun serialize(obj: Any, context: WriteContext) {
    context.writer.writeSymbol((obj as Enum<*>).name)
  }
}

private class DateBinding : Binding {
  override fun serialize(obj: Any, context: WriteContext) {
    context.writer.writeTimestamp(Timestamp.forDateZ(obj as Date))
  }

  override fun deserialize(context: ReadContext): Any {
    return context.reader.dateValue()
  }
}

private class ByteArrayBinding : Binding {
  override fun serialize(obj: Any, context: WriteContext) {
    context.writer.writeBlob(obj as ByteArray)
  }

  override fun deserialize(context: ReadContext): Any {
    return context.reader.newBytes()
  }
}

internal fun createElementBindingByType(type: Type, context: BindingInitializationContext): Binding {
  return context.bindingProducer.getRootBinding(ClassUtil.typeToClass(type), type)
}