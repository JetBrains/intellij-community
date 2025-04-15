// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.rhizomedb

import com.jetbrains.rhizomedb.impl.*
import fleet.util.singleOrNullOrThrow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlin.jvm.JvmName

/**
 * Represents an attribute of an [Entity].
 * [EntityAttribute] is itself an [Entity].
 * It's [EntityType] is [EntityAttribute.Companion]
 * [EntityAttribute] is an [Entity.EntityObject] of [EntityAttribute] entity.
 * */
sealed class EntityAttribute<E : Entity, T : Any>(
  val ident: String,
  val attr: Attribute<*>,
  internal val serializerLazy: Lazy<KSerializer<T>>?,
  /**
   * Attribute may have a default value.
   * It is useful when defining [Mixin]s, as there is not explicit constructor for them.
   * */
  val defaultValue: DefaultValue<*>?
) : Entity, Presentable {

  /**
   * [EntityType] of [EntityAttribute] entity.
   * */
  companion object : EntityType<EntityAttribute<*, *>>("rhizomedb.Attribute", "rhizome", { eid ->
    TODO("should not be called")
  }) {
    /**
     * Unique Ident of the attribute
     * */
    val Ident = requiredValue("ident", String.serializer(), Indexing.UNIQUE)
  }

  override val eid: EID
    get() = attr.eid

  override fun equals(other: Any?): Boolean =
    other is EntityAttribute<*, *> && other.attr == attr

  override fun hashCode(): Int = attr.eid

  override fun toString(): String = attr.toString()

  final override val presentableText: String
    get() = "Attribute($ident, $eid)"

  /**
   * [KSerializer] associated with the value attributes
   * @see Attributes.requiredValue
   * @see Attributes.optionalValue
   * @see Attributes.manyValues
   * */
  val serializer: KSerializer<T>? get() = serializerLazy?.value
}

/**
 * Column query.
 * Returns a set of pairs of [Entity] and the corresponding attribute's value.
 * */
fun <E : Entity, T : Any> EntityAttribute<E, T>.all(): Set<Pair<E, T>> = DbContext.threadBound.all(this)

/**
 * Column query.
 * Returns a set of all values of the given attribute
 * */
fun <E : Entity, T : Any> EntityAttribute<E, T>.values(): Set<T> = DbContext.threadBound.values(this)

/**
 * Returns a single pair of [Entity] and [T] if exactly one [Entity] has this [EntityAttribute].
 * Otherwise raises an error.
 * */
fun <E : Entity, T : Any> EntityAttribute<E, T>.single(): Pair<E, T> = all().single()

/**
 * Returns a single pair of [Entity] and [T] if exactly one [Entity] has this [EntityAttribute].
 * If there is none, returns null.
 * If there is more than one raises an error.
 * */
fun <E : Entity, T : Any> EntityAttribute<E, T>.singleOrNull(): Pair<E, T>? = all().singleOrNullOrThrow()

/**
 * Finds all the [Entity]'s which has attribute's value set to (or containing) [value]
 * It could be used for references or attributes with [Indexing.INDEXED] or [Indexing.UNIQUE] set.
 * */
fun <E : Entity, T : Any> entities(entityAttribute: EntityAttribute<E, T>, value: T): Set<E> = entityAttribute.let { attribute ->
  DbContext.threadBound.lookup(attribute, value)
}

/**
 * Returns an [Entity] which has the attribute's value set to (or containing) a given [value]
 * Works for [EntityAttribute]s with [Indexing.UNIQUE] or [RefFlags.UNIQUE]
 * */
fun <E : Entity, T : Any> entity(entityAttribute: EntityAttribute<E, T>, value: T): E? =
  DbContext.threadBound.entity(entityAttribute, value)

/**
 * Returns an [Entity] which has the attribute's value set to (or containing) a given [value]
 * Works for [EntityAttribute]s with [Indexing.UNIQUE] or [RefFlags.UNIQUE]
 * */
fun <E : Entity, T : Any> DbContext<Q>.entity(entityAttribute: EntityAttribute<E, T>, value: T): E? =
  impl.entity(entityAttribute, value)

@Deprecated(
  "use entity() on UNIQUE attribute or entities().singleOrNullOrThrow() instead",
  ReplaceWith("entities(entityAttribute, value).singleOrNullOrThrow()", "fleet.util.singleOrNullOrThrow")
)
fun <E : Entity, T : Any> entityOnNonUniqueAttribute(entityAttribute: EntityAttribute<E, T>, value: T): E? =
  entities(entityAttribute, value).singleOrNullOrThrow()

/**
 *  Problematic case that this method solves temporarily:
 *  ```kotlin
 *    interface A: Entity { var i: Boolean }
 *    interface B: A
 *    interface C: A
 *
 *    // ...
 *    new(B) { i = true }
 *    new(C) { i = true }
 *    lookupOne(B::i, true) // worked, but entity(A.iAttr, true) as? B would fail (multiple result)
 *  ```
 *
 *  Replace either with:
 *  - if there's at most one on base class + attribute is Unique: `entity() as? YourTarget`
 *  - otherwise: inline content `entities(...).filterIsInstance<YourTarget>().singleOrNullOrThrow()`
 */
@Deprecated(
  "new API doesn't allow to call entity() on a attribute of a base class while expecting only a subtype to be returned",
  ReplaceWith("entities(entityAttribute, value).filterIsInstance<C>().singleOrNullOrThrow()", "fleet.util.singleOrNullOrThrow")
)
inline fun <E : Entity, T : Any, reified C: E> entityCasted(entityAttribute: EntityAttribute<E, T>, value: T): C? =
  entities(entityAttribute, value).filterIsInstance<C>().singleOrNullOrThrow()

@Deprecated(
  "new API doesn't allow to call entity() on a attribute of a base class while expecting only a subtype to be returned",
  ReplaceWith("entities(entityAttribute, value).filterIsInstance<C>()")
)
inline fun <E : Entity, T : Any, reified C: E> entitiesCasted(entityAttribute: EntityAttribute<E, T>, value: T): Set<C> =
  entities(entityAttribute, value).filterIsInstance<C>().toSet()

/**
 * Returns an [Entity] which has the attribute's value set to (or containing) a given [value]
 * Works for [EntityAttribute]s with [Indexing.UNIQUE] or [RefFlags.UNIQUE]
 * */
fun <E : Entity, T : Any> Q.entity(entityAttribute: EntityAttribute<E, T>, value: T): E? =
  entityAttribute.let { attribute ->
    require(attribute.attr.schema.unique) {
      "attribute $attribute is not unique"
    }
    @Suppress("UNCHECKED_CAST")
    queryIndex(IndexQuery.LookupUnique(
      attribute.attr as Attribute<Any>,
      attribute.toIndexValue(value)
    ))?.let { v ->
      this@entity.entity(v.eid) as E?
    }
  }

/**
 * Simple function providing a default value for an attribute.
 * It can be used to implement id generation or auto-increments.
 * */
fun interface DefaultValue<T> {
  fun provide(): T
}

/**
 * Gets a value of the attribute of the given [Entity] from the thread-bound [DbContext]
 * */
@JvmName("getRequired")
operator fun <E : Entity, V : Any> E.get(attribute: Attributes<E>.Required<V>): V = let { entity ->
  DbContext.threadBound.get(entity, attribute)
}

/**
 * Gets a value of the attribute of the given [Entity] from the thread-bound [DbContext]
 * */
fun <E : Entity, V : Any> DbContext<Q>.get(
  entity: E,
  attribute: Attributes<E>.Required<V>
): V =
  impl.get(entity, attribute)

/**
 * Gets a value of the attribute of the given [Entity] from the thread-bound [DbContext]
 * */
fun <E : Entity, V : Any> Q.get(
  entity: E,
  attribute: Attributes<E>.Required<V>
): V = run {
  val value = requireNotNull(queryIndex(IndexQuery.GetOne(entity.eid, attribute.attr, true))) {
    "required attribute ${displayAttribute(attribute.attr)} is absent in entity ${displayEntity(entity.eid)}"
  }
  fromIndexValue(attribute, value.x)
}

/**
 * Gets a value of the attribute of the given [Entity] from the thread-bound [DbContext]
 * */
@JvmName("getOptional")
operator fun <E : Entity, V : Any> E.get(attribute: Attributes<E>.Optional<V>): V? = let { entity ->
  DbContext.threadBound.get(entity, attribute)
}

/**
 * Gets a value of the attribute of the given [Entity] from the thread-bound [DbContext]
 * */
fun <E : Entity, V : Any> DbContext<Q>.get(
  entity: E,
  attribute: Attributes<E>.Optional<V>
): V? =
  impl.get(entity, attribute)

/**
 * Gets a value of the attribute of the given [Entity] from the thread-bound [DbContext]
 * */
fun <E : Entity, V : Any> Q.get(
  entity: E,
  attribute: Attributes<E>.Optional<V>
): V? =
  queryIndex(IndexQuery.GetOne(entity.eid, attribute.attr, true))?.let { value ->
    fromIndexValue(attribute, value.x)
  }

/**
 * Gets all values of the multi-valued attribute of the given [Entity] from the thread-bound [DbContext]
 * */
@JvmName("getMany")
operator fun <E : Entity, V : Any> E.get(attribute: Attributes<E>.Many<V>): Set<V> = let { entity ->
  DbContext.threadBound.get(entity, attribute)
}

/**
 * Gets all values of the multi-valued attribute of the given [Entity] from the thread-bound [DbContext]
 * */
fun <E : Entity, V : Any> DbContext<Q>.get(
  entity: E,
  attribute: Attributes<E>.Many<V>
): Set<V> =
  impl.get(entity, attribute)

/**
 * Gets all values of the multi-valued attribute of the given [Entity] from the thread-bound [DbContext]
 * */
fun <E : Entity, V : Any> Q.get(
  entity: E,
  attribute: Attributes<E>.Many<V>
): Set<V> =
  queryIndex(IndexQuery.GetMany(entity.eid, attribute.attr))
    .mapTo(HashSet()) { (_, _, v) -> fromIndexValue(attribute, v) }

/**
 * Finds all the [Entity]'s which has attribute's value set to (or containing) [value]
 * It could be used for references or attributes with [Indexing.INDEXED] or [Indexing.UNIQUE] set.
 * */
fun <E : Entity, V : Any> DbContext<Q>.lookup(
  attribute: EntityAttribute<E, V>,
  value: V
): Set<E> = impl.lookup(attribute, value)

/**
 * Finds all the [Entity]'s which has attribute's value set to (or containing) [value]
 * It could be used for references or attributes with [Indexing.INDEXED] or [Indexing.UNIQUE] set.
 * */
@Suppress("UNCHECKED_CAST")
fun <E : Entity, V : Any> Q.lookup(
  attribute: EntityAttribute<E, V>,
  value: V
): Set<E> =
  queryIndex(IndexQuery.LookupMany(attribute.attr as Attribute<Any>, attribute.toIndexValue(value)))
    .mapNotNullTo(HashSet()) { (eid) ->
      entity(eid) as E?
    }

/**
 * Column query.
 * Returns a set of pairs of [Entity] and the corresponding attribute's value.
 * */
fun <E : Entity, V : Any> DbContext<Q>.all(attribute: EntityAttribute<E, V>): Set<Pair<E, V>> =
  impl.all(attribute)

/**
 * Column query.
 * Returns a set of all values of the given attribute
 * */
fun <E : Entity, V : Any> DbContext<Q>.values(attribute: EntityAttribute<E, V>): Set<V> =
  impl.values(attribute)

/**
 * Column query.
 * Returns a set of pairs of [Entity] and the corresponding attribute's value.
 * */
fun <E : Entity, V : Any> Q.all(attribute: EntityAttribute<E, V>): Set<Pair<E, V>> =
  queryIndex(IndexQuery.Column(attribute.attr))
    .mapNotNullTo(HashSet()) { datom ->
      @Suppress("UNCHECKED_CAST")
      val entity = entity(datom.eid) as E?
      entity?.let { it to fromIndexValue(attribute, datom.value) }
    }

/**
 * Column query.
 * Returns a set of pairs of [Entity] and the corresponding attribute's value.
 * */
fun <E : Entity, V : Any> Q.values(attribute: EntityAttribute<E, V>): Set<V> =
  queryIndex(IndexQuery.Column(attribute.attr))
    .mapTo(HashSet()) { datom ->
      fromIndexValue(attribute, datom.value)
    }

/**
 * Returns a set of [Entity]'s of a given [EntityType]
 * */
@Suppress("UNCHECKED_CAST")
fun <E : Entity> DbContext<Q>.all(entityType: EntityType<E>): Set<E> =
  lookup(Entity.Type, entityType) as Set<E>

/**
 * Converts internal index value representation (as in [Datom]) to a user-space value
 * by converting [EID]'s of reference attributes to [Entity] objects
 * @see toIndexValue
 * */
fun <V : Any> EntityAttribute<*, V>.fromIndexValue(value: Any): V = let { attribute ->
  DbContext.threadBound.fromIndexValue(attribute, value)
}

/**
 * Converts internal index value representation (as in [Datom]) to a user-space value
 * by converting [EID]'s of reference attributes to [Entity] objects
 * @see toIndexValue
 * */
fun <V : Any> DbContext<Q>.fromIndexValue(attribute: EntityAttribute<*, V>, value: Any): V =
  impl.fromIndexValue(attribute, value)

/**
 * Converts internal index value representation (as in [Datom]) to a user-space value
 * by converting [EID]'s of reference attributes to [Entity] objects
 * @see toIndexValue
 * */
@Suppress("UNCHECKED_CAST")
fun <V : Any> Q.fromIndexValue(attribute: EntityAttribute<*, V>, value: Any): V =
  when (attribute.attr.schema.isRef) {
    true -> entity(value as EID)
    false -> value
  } as V

/**
 * Converts [EntityAttribute] value to the internal index value representation (as in [Datom])
 * by converting [Entity]s of reference attributes to [EID]s objects
 * @see fromIndexValue
 * */
fun <V : Any> EntityAttribute<*, V>.toIndexValue(value: V): Any =
  let { attribute ->
    when (attribute.attr.schema.isRef) {
      true -> (value as Entity).eid
      false -> value
    }
  }
