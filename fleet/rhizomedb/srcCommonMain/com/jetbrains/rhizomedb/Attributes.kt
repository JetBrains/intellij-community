// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.rhizomedb

import com.jetbrains.rhizomedb.impl.EidGen
import com.jetbrains.rhizomedb.impl.generateSeed
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlin.reflect.KProperty

fun attr(ident: String, schema: Schema): Attribute<*> =
  Attribute.fromEID<Any>(EidGen.memoizedEID(SchemaPart, ident), schema)

/**
 * Represents a set of attributes in a given [namespace].
 * Only possible subclasses are [EntityType] and [Mixin].
 * There are three kinds of [EntityAttribute]s, classified by their cardinality:
 * - [Required]
 * - [Optional]
 * - [Many]
 */
sealed class Attributes<E : Entity>(
  val namespace: String,
  val module: String,
  initial: Map<String, EntityAttribute<in E, *>>
) {

  private val mutableAttrInfos: MutableMap<String, EntityAttribute<in E, *>> = HashMap(initial)

  /**
   * [EntityAttribute]s defined by this [Attributes] by their ident: namespace/name
   * */
  internal val entityAttributes: Map<String, EntityAttribute<in E, *>> get() = mutableAttrInfos

  val attrs: List<Attribute<*>>
    get() = entityAttributes.values.map { it.attr }

  private fun <A : EntityAttribute<E, *>> addAttr(entityAttribute: A): A = run {
    require(this@Attributes.mutableAttrInfos.put(entityAttribute.ident, entityAttribute) == null) {
      "duplicate attr for ident ${entityAttribute.ident} in $namespace"
    }
    entityAttribute
  }

  inner class Required<V : Any> internal constructor(
    ident: String,
    attr: Attribute<*>,
    serializer: Lazy<KSerializer<V>>?,
    defaultValue: DefaultValue<V>?
  ) : EntityAttribute<E, V>(ident, attr, serializer, defaultValue) {
    operator fun invoke(entity: E): V = entity[this]
    operator fun getValue(entity: E, property: KProperty<*>): V = entity[this]

    // TODO: to be removed after migration
    operator fun setValue(entity: E, property: KProperty<*>, value: V): Unit =
      let { attribute ->
        requireChangeScope { entity[attribute] = value }
      }
  }

  inner class Optional<V : Any> internal constructor(
    ident: String,
    attr: Attribute<*>,
    serializer: Lazy<KSerializer<V>>?,
    defaultValue: DefaultValue<V>?
  ) : EntityAttribute<E, V>(ident, attr, serializer, defaultValue) {
    operator fun invoke(entity: E): V? = entity[this]
    operator fun getValue(entity: E, property: KProperty<*>): V? = entity[this]

    // TODO: to be removed after migration
    operator fun setValue(entity: E, property: KProperty<*>, value: V?): Unit =
      let { attribute ->
        requireChangeScope { entity[attribute] = value }
      }
  }

  inner class Many<V : Any> internal constructor(
    ident: String,
    attr: Attribute<*>,
    serializer: Lazy<KSerializer<V>>?,
    defaultValue: DefaultValue<V>?
  ) : EntityAttribute<E, V>(ident, attr, serializer, defaultValue) {
    operator fun invoke(entity: E): Set<V> = entity[this]
    operator fun getValue(entity: E, property: KProperty<*>): Set<V> = entity[this]
  }

  /**
   * Constructs [Required] attribute containing a simple value.
   * Being a value here means to be immutable and serializable.
   * Being [Required] implies that no entity of this [EntityType] or [Mixin] is allowed
   * to be constructed without this attribute.
   * This attribute can't be retracted without retractin of the whole entity or without being replaced by other value.
   * */
  protected fun <T : Any> requiredValue(
    name: String,
    serializer: KSerializer<T>,
    valueFlags: Indexing = Indexing.NOT_INDEXED,
    defaultValueProvider: DefaultValue<T>? = null
  ): Required<T> =
    addAttr(Required(
      ident = "$namespace/$name",
      serializer = lazyOf(serializer),
      attr = attr("$namespace/$name", Schema(
        cardinality = Cardinality.One,
        isRef = false,
        indexed = Indexing.INDEXED == valueFlags,
        unique = Indexing.UNIQUE == valueFlags,
        cascadeDelete = false,
        cascadeDeleteBy = false,
        required = true
      )),
      defaultValue = defaultValueProvider
    ))

  /**
   * Constructs [Optional] attribute containing a simple value.
   * Being a value here means to be immutable and serializable.
   * [Optional] means, that entity of this [EntityType] or [Mixin] may not have the value associated.
   * Keep in mind that database does not store nulls.
   * Absense of a value is not represented by any [Datom].
   * */
  protected fun <T : Any> optionalValue(
    name: String,
    serializer: KSerializer<T>,
    valueFlags: Indexing = Indexing.NOT_INDEXED,
    defaultValueProvider: DefaultValue<T>? = null
  ): Optional<T> =
    addAttr(Optional(
      ident = "$namespace/$name",
      attr = attr("$namespace/$name", Schema(
        cardinality = Cardinality.One,
        isRef = false,
        indexed = Indexing.INDEXED == valueFlags,
        unique = Indexing.UNIQUE == valueFlags,
        cascadeDelete = false,
        cascadeDeleteBy = false,
        required = false
      )),
      serializer = lazyOf(serializer),
      defaultValue = defaultValueProvider
    ))

  /**
   * Constructs a multi-valued attribute.
   * Being a value here means to be immutable and serializable.
   * Internally represented as a set of [Datom]s with the same eid and attr, but different values.
   * Values collection has a set semantics.
   * The same value can't be stored more than once.
   * The order of iteration over the set is not specified.
   * */
  protected fun <T : Any> manyValues(
    name: String,
    serializer: KSerializer<T>,
    valueFlags: Indexing = Indexing.NOT_INDEXED,
  ): Many<T> =
    addAttr(Many(
      ident = "$namespace/$name",
      attr("$namespace/$name", schema = Schema(
        cardinality = Cardinality.Many,
        isRef = false,
        indexed = Indexing.INDEXED == valueFlags,
        unique = Indexing.UNIQUE == valueFlags,
        cascadeDelete = false,
        cascadeDeleteBy = false,
        required = false
      )),
      serializer = lazyOf(serializer),
      defaultValue = null
    ))

  /**
   * Constructs [Required] attribute containing arbitrary object.
   * While it is possible to store mutable value as transient, concurrency control is left to the user.
   * Mutable value stored by transient attribute must have reference semantics.
   * Mutable equality/hashCode will cause SEVERE problems accessing it, values will get lost.
   * Which means transient attributes are not suitable to storing mutable collections of any kind, please use persistent ones.
   * */
  protected fun <T : Any> requiredTransient(
    name: String,
    valueFlags: Indexing = Indexing.NOT_INDEXED,
    defaultValueProvider: DefaultValue<T>? = null
  ): Required<T> =
    addAttr(Required(
      ident = "$namespace/$name",
      attr = attr("$namespace/$name", schema = Schema(
        cardinality = Cardinality.One,
        isRef = false,
        indexed = Indexing.INDEXED == valueFlags,
        unique = Indexing.UNIQUE == valueFlags,
        cascadeDelete = false,
        cascadeDeleteBy = false,
        required = true
      )),
      serializer = null,
      defaultValue = defaultValueProvider
    ))

  /**
   * Constructs [Optional] attribute containing arbitrary object.
   * While it is possible to store mutable value as transient, concurrency control is left to the user.
   * Mutable value stored by transient attribute must have reference semantics.
   * Mutable equality/hashCode will cause SEVERE problems accessing it, values will get lost.
   * Which means transient attributes are not suitable to storing mutable collections of any kind, please use persistent ones.
   * */
  protected fun <T : Any> optionalTransient(
    name: String,
    valueFlags: Indexing = Indexing.NOT_INDEXED,
    defaultValueProvider: DefaultValue<T>? = null
  ): Optional<T> =
    addAttr(Optional(
      ident = "$namespace/$name",
      attr("$namespace/$name", schema = Schema(
        cardinality = Cardinality.One,
        isRef = false,
        indexed = Indexing.INDEXED == valueFlags,
        unique = Indexing.UNIQUE == valueFlags,
        cascadeDelete = false,
        cascadeDeleteBy = false,
        required = false
      )),
      serializer = null,
      defaultValue = defaultValueProvider
    ))

  /**
   * Constructs [Many] attribute containing arbitrary object.
   * While it is possible to store mutable value as transient, concurrency control is left to the user.
   * Mutable value stored by transient attribute must have reference semantics.
   * Mutable equality/hashCode will cause SEVERE problems accessing it, values will get lost.
   * Which means transient attributes are not suitable to storing mutable collections of any kind, please use persistent ones.
   * Internally represented as a set of [Datom]s with the same eid and attr, but different values.
   * Values collection has a set semantics.
   * The same value can't be stored more than once.
   * The order of iteration over the set is not specified.
   * */
  protected fun <T : Any> manyTransient(
    name: String,
    valueFlags: Indexing = Indexing.NOT_INDEXED,
  ): Many<T> =
    addAttr(Many(
      ident = "$namespace/$name",
      attr = attr("$namespace/$name", schema = Schema(
        cardinality = Cardinality.Many,
        isRef = false,
        indexed = Indexing.INDEXED == valueFlags,
        unique = Indexing.UNIQUE == valueFlags,
        cascadeDelete = false,
        cascadeDeleteBy = false,
        required = false
      )),
      serializer = null,
      defaultValue = null
    ))

  /**
   * Constructs [Required] attribute, representing a reference to other entity.
   * Internally represented as [EID] in a place of a value of [Datom]: [eid1, attr, eid2]
   * When referenced entity is retracted, all entities referring to it with [Required] attributes are also retracted.
   * In other words, it is always [RefFlags.CASCADE_DELETE_BY].
   * */
  protected fun <T : Entity> requiredRef(
    name: String,
    vararg refFlags: RefFlags
  ): Required<T> =
    addAttr(Required(
      ident = "$namespace/$name",
      attr = attr("$namespace/$name", schema = Schema(
        cardinality = Cardinality.One,
        isRef = true,
        indexed = false,
        unique = RefFlags.UNIQUE in refFlags,
        cascadeDelete = RefFlags.CASCADE_DELETE in refFlags,
        // required ref is always cascade-delete-by, if reference is retracted, this is the only way to maintain the invariant:
        cascadeDeleteBy = true,
        required = true
      )),
      serializer = null,
      defaultValue = null
    ))

  /**
   * Constructs [Optional] attribute, representing a reference to other entity.
   * Internally represented as [EID] in a place of a value of [Datom]: [eid1, attr, eid2]
   * When referenced entity is retracted, all datoms referring to it are also retracted.
   * */
  protected fun <T : Entity> optionalRef(
    name: String,
    vararg refFlags: RefFlags
  ): Optional<T> =
    addAttr(Optional(
      ident = "$namespace/$name",
      attr = attr("$namespace/$name", schema = Schema(
        cardinality = Cardinality.One,
        isRef = true,
        indexed = false,
        unique = RefFlags.UNIQUE in refFlags,
        cascadeDelete = RefFlags.CASCADE_DELETE in refFlags,
        cascadeDeleteBy = RefFlags.CASCADE_DELETE_BY in refFlags,
        required = false
      )),
      serializer = null,
      defaultValue = null
    ))

  /**
   * Constructs [Many] attribute, representing a reference to other entity.
   * Internally represented as [EID] in a place of a value of [Datom]: [eid1, attr, eid2]
   * When referenced entity is retracted, all datoms referring to it are also retracted.
   * */
  protected fun <T : Entity> manyRef(
    name: String,
    vararg refFlags: RefFlags
  ): Many<T> =
    addAttr(Many(
      ident = "$namespace/$name",
      attr = attr("$namespace/$name", schema = Schema(
        cardinality = Cardinality.Many,
        isRef = true,
        indexed = false,
        unique = RefFlags.UNIQUE in refFlags,
        cascadeDelete = RefFlags.CASCADE_DELETE in refFlags,
        cascadeDeleteBy = RefFlags.CASCADE_DELETE_BY in refFlags,
        required = false
      )),
      serializer = null,
      defaultValue = null
    ))
}

/**
 * Any of these flags implies that values of this attribute is stored in avet index.
 * Storing values with inefficient hashing strategy in this index will have performance impact.
 * Please avoid using them for anything bigger than simple small scalar values.
 * */
enum class Indexing {
  NOT_INDEXED,

  /**
   * Attributes with this flag are guaranteed to have no more than one entity refering with it, to any given value
   * Implicitly enables [entity] query.
   * */
  UNIQUE,

  /**
   * Implicitly enables [entities] query.
   * */
  INDEXED
}

enum class RefFlags {
  /**
   * Attributes with this flag are guaranteed to have no more than one entity refering with it, to any given entity
   * Implicitly enables [entity] query.
   * */
  UNIQUE,

  /**
   * When entity with this attribute is required, the referenced entity is also retracted.
   * */
  CASCADE_DELETE,

  /**
   * When referenced entity is retracted, this entity also gets retracted.
   * */
  CASCADE_DELETE_BY
}

internal fun<E: Entity> merge(attrs: List<Attributes<in E>>): Map<String, EntityAttribute<in E, *>> =
  buildMap {
    attrs.forEach { m ->
      m.entityAttributes.forEach { (k, v) ->
        val prev = put(k, v)
        require(prev == null || prev == v) {
          "duplicated attribute $k in $attrs"
        }
      }
      putAll(m.entityAttributes)
    }
  }

internal fun ChangeScope.registerAttributes(attributes: Attributes<*>): Unit =
  context.run {
    attributes.entityAttributes.forEach { (attrIdent, entityAttribute) ->
      if (entity(entityAttribute.attr.eid) == null) {
        mutate(CreateEntity(
          eid = entityAttribute.attr.eid,
          entityTypeEid = EntityAttribute.eid,
          attributes = buildList {
            add(Entity.EntityObject.attr to entityAttribute)
            add(Entity.Module.attr to attributes.module)
            @Suppress("UNCHECKED_CAST")
            add(EntityAttribute.Ident.attr as Attribute<String> to attrIdent)
          },
          seed = generateSeed()
        ))
        entityAttribute.serializerLazy?.let { serializer ->
          mutate(MapAttribute(entityAttribute.attr) {
            when {
              it is JsonElement -> DbJson.decodeFromJsonElement(serializer.value as KSerializer<Any>, it)
              else -> it
            }
          })
        }
      }
    }
  }