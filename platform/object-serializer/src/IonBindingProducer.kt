// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization

import com.amazon.ion.IonType
import com.amazon.ion.Timestamp
import com.intellij.util.SystemProperties
import com.intellij.util.containers.ContainerUtil
import gnu.trove.THashMap
import java.io.File
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.nio.file.FileSystems
import java.nio.file.Path
import java.util.*

internal typealias NestedBindingFactory = (accessor: MutableAccessor) -> NestedBinding
internal typealias RootBindingFactory = () -> RootBinding

internal class IonBindingProducer(override val propertyCollector: PropertyCollector) : BindingProducer<RootBinding>(), BindingInitializationContext {
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
        classToNestedBindingFactory.put(key) { PropertyBinding(factory()) }
        true
      }
    }
  }

  override val isResolveConstructorOnInit = SystemProperties.`is`("idea.serializer.resolve.ctor.on.init")

  override val bindingProducer: BindingProducer<RootBinding>
    get() = this

  override fun createCacheKey(aClass: Class<*>, originalType: Type): Type {
    if (aClass !== originalType && !Collection::class.java.isAssignableFrom(aClass) && !classToRootBindingFactory.contains(aClass)) {
      // type parameters for bean binding doesn't play any role, should be the only binding for such class
      return aClass
    }
    else {
      return originalType
    }
  }

  override fun createRootBinding(aClass: Class<*>, type: Type, cacheKey: Type, map: MutableMap<Type, RootBinding>): RootBinding {
    val binding = doCreateRootBinding(aClass, type, cacheKey)
    map.put(cacheKey, binding)
    try {
      binding.init(type, this)
    }
    catch (e: Throwable) {
      map.remove(type)
      throw e
    }
    return binding
  }

  private fun doCreateRootBinding(aClass: Class<*>, type: Type, cacheKey: Type): RootBinding {
    val customFactory = classToRootBindingFactory.get(aClass)
    if (customFactory != null) {
      return customFactory.invoke()
    }

    return when {
      Collection::class.java.isAssignableFrom(aClass) -> createCollectionBinding(type)
      Map::class.java.isAssignableFrom(aClass) -> {
        val typeArguments = (type as ParameterizedType).actualTypeArguments
        MapBinding(typeArguments[0], typeArguments[1], this)
      }
      aClass.isArray -> ArrayBinding(aClass.componentType, this)
      aClass.isEnum -> {
        @Suppress("UNCHECKED_CAST")
        EnumBinding(aClass as Class<out Enum<*>>)
      }
      else -> {
        assert(cacheKey === aClass)
        if (aClass.isInterface || Modifier.isAbstract(aClass.modifiers)) {
          PolymorphicBinding(aClass)
        }
        else {
          BeanBinding(aClass)
        }
      }
    }
  }

  // note about field name - Ion binary writer interns string automatically, no need to intern (text writer doesn't support symbol tables)
  override fun getNestedBinding(accessor: MutableAccessor): NestedBinding {
    val type = accessor.genericType
    val aClass = ClassUtil.typeToClass(type)

    // PrimitiveBinding can serialize conditionally, but for the sake of optimization, use special bindings to avoid comparison for each value
    // yes - if field typed as Object, Number and Boolean types are not supported (because bean binding will be created)
    classToNestedBindingFactory.get(aClass)?.let {
      return it(accessor)
    }

    // CollectionBinding implements NestedBinding directly because can mutate property value directly
    return when {
      Collection::class.java.isAssignableFrom(aClass) -> createCollectionBinding(type)
      Map::class.java.isAssignableFrom(aClass) -> {
        val typeArguments = (type as ParameterizedType).actualTypeArguments
        MapBinding(typeArguments[0], typeArguments[1], this)
      }
      aClass.isArray -> ArrayBinding(aClass.componentType, this)
      java.lang.Number::class.java.isAssignableFrom(aClass) -> PropertyBinding(NumberAsObjectBinding())
      aClass.isInterface || Modifier.isAbstract(aClass.modifiers) -> {
        val annotation = accessor.getAnnotation(Property::class.java)
        if (annotation == null) {
          // todo respect configuration
          return PropertyBinding(getRootBinding(aClass, type))
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
        PropertyBinding(getRootBinding(aClass, type))
      }
    }
  }

  private fun createCollectionBinding(type: Type): CollectionBinding {
    return CollectionBinding(type as ParameterizedType, this)
  }
}

private class FileBinding : RootBinding {
  override fun deserialize(context: ReadContext): Any {
    return File(context.reader.stringValue())
  }

  override fun serialize(obj: Any, context: WriteContext) {
    context.writer.writeSymbol((obj as File).path)
  }
}

private class PathBinding : RootBinding {
  override fun deserialize(context: ReadContext): Any {
    return FileSystems.getDefault().getPath(context.reader.stringValue())
  }

  override fun serialize(obj: Any, context: WriteContext) {
    context.writer.writeSymbol((obj as Path).toString())
  }
}

private class EnumBinding(private val valueClass: Class<out Enum<*>>) : RootBinding {
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

private class DateBinding : RootBinding {
  override fun serialize(obj: Any, context: WriteContext) {
    context.writer.writeTimestamp(Timestamp.forDateZ(obj as Date))
  }

  override fun deserialize(context: ReadContext): Any {
    return context.reader.dateValue()
  }
}

internal inline fun write(hostObject: Any, accessor: MutableAccessor, context: WriteContext, write: ValueWriter.(value: Any) -> Unit) {
  val value = accessor.read(hostObject)
  if (context.filter.isSkipped(value)) {
    return
  }

  val writer = context.writer
  writer.setFieldName(accessor.name)
  if (value == null) {
    writer.writeNull()
  }
  else {
    writer.write(value)
  }
}

internal inline fun read(hostObject: Any, property: MutableAccessor, context: ReadContext, read: ValueReader.() -> Any) {
  val type = context.reader.type
  if (type == IonType.NULL) {
    property.set(hostObject, null)
  }
  else {
    property.set(hostObject, context.reader.read())
  }
}

private class ByteArrayBinding : RootBinding {
  override fun serialize(obj: Any, context: WriteContext) {
    context.writer.writeBlob(obj as ByteArray)
  }

  override fun deserialize(context: ReadContext): Any {
    return context.reader.newBytes()
  }
}

internal fun createElementBindingByType(type: Type, context: BindingInitializationContext): RootBinding {
  return context.bindingProducer.getRootBinding(ClassUtil.typeToClass(type), type)
}