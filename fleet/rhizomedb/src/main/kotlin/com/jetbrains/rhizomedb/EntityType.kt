// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.rhizomedb

import com.jetbrains.rhizomedb.impl.EidGen
import com.jetbrains.rhizomedb.impl.EntityFactory
import fleet.util.singleOrNullOrThrow
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import kotlinx.serialization.builtins.serializer
import kotlin.reflect.KClass

/**
 * Base class to define new entity types.
 * Every entity has a single [EntityType] associated with it.
 * [Entity] refers to [EntityType] with [Entity.Type] relation.
 *
 * Usually used as a base class for [Entity] Companion objects:
 *
 * ``` kotlin
 * data class MyEntity(override eid: EID): Entity {
 *   companion object: EntityType(MyEntity::class, ::MyEntity) {
 *     val MyAttr = requiredValue("myAttr", Int.serializer())
 *   }
 *   val myAttr by MyAttr
 * }
 * ```
 *
 * - It defines it's attributes,
 * - has a clear identity [entityTypeIdent] and [eid] in the database
 * - knows how to construct a custom [Entity] object for a given [EID] with [reify] parameter
 * @see [Entity]
 * @see [Attributes]
 * @see [Mixin]
 *
 * [EntityType] is itself an [Entity]
 * It's entity type is [EntityType.Companion]
 * A reference to [EntityType] entity is stored as an attribute [Entity.Type] of every [Entity]
 * [EntityType] is an [Entity.EntityObject] of [EntityType] entity.
 * */
abstract class EntityType<E : Entity>(
  ident: String,
  module: String,
  /**
   * Function invoked for existing entities when the [EntityType] is loaded by [ChangeScope.register], or at the entity creation by [ChangeScope.new]
   * */
  internal val reify: (EID) -> E,
  vararg mixins: Mixin<in E>
) : Attributes<E>(ident, module, merge(mixins.toList())), Entity, Presentable {

  /**
   * Tell [EntityType] to use qualified name of the given [KClass] as [ident]
   * It may be useful to guarantee the uniqueness of the [ident].
   * But it could backfire for durable entities,
   * if one renames the class, or move it to other package.
   * */
  constructor(ident: KClass<E>,
              reify: (EID) -> E,
              vararg mixins: Mixin<in E>) : this(requireNotNull(ident.qualifiedName), entityModule(ident), reify, *mixins)

  override val eid: EID = EidGen.memoizedEID(SchemaPart, ident)

  /**
   * The [EntityType] of the [EntityType]
   * Defines common attributes of all [EntityType]s
   * */
  companion object : EntityType<EntityType<*>>("rhizomedb.EntityType", "rhizome", {
    error("Entity type can't be constructed given just EID. " +
          "It has to be added to the db explicitly. " +
          "Normally it happens when we're registering EntityType in ChangeScope.register and DbContext<Mut>.initAttributes.")
  }) {
    /**
     * Attribute to store unique identifier of an [EntityType]
     * */
    val Ident = requiredValue("ident", String.serializer(), Indexing.UNIQUE)

    /**
     * Attributes to store references to all possible Attributes of a given [EntityType].
     * We need it because rhizomedb store all data in a column-storage: aevt.
     * When we are retracting an entity we need to know which attributes it may have to clean them up in their columns
     * */
    val PossibleAttributes = manyRef<EntityAttribute<*, *>>("possibleAttributes")

    /**
     * A little bit less unique identifier of an [EntityType].
     * Can be used to track schema changes.
     * */
    val Name = requiredValue("name", String.serializer(), Indexing.INDEXED)
  }

  /**
   * Unique identifier of an [EntityType]
   * */
  val entityTypeIdent: String get() = namespace

  override fun equals(other: Any?): Boolean =
    other is EntityType<*> && other.eid == eid

  override fun hashCode(): Int = eid

  override fun toString(): String = "EntityType($namespace, $eid)"

  final override val presentableText: String
    get() = toString()

  /**
   * Constructs a new entity of this type
   *
   * @see ChangeScope.new
   * */
  fun ChangeScope.new(builder: EntityBuilder<E>): E =
    this@EntityType.new(builder)

  /**
   * Returns a set of [Entity]'s of a given [EntityType]
   * */
  fun all(): Set<E> = let { entityType ->
    @Suppress("UNCHECKED_CAST")
    entities(Entity.Type, entityType) as Set<E>
  }

  /**
   * Returns a single [Entity] of this [EntityType].
   * Throws an error if there is not exactly one [Entity] of this [EntityType] in the database.
   * */
  fun single(): E = all().single()

  /**
   * Returns a single [Entity] of this [EntityType] if there is one, otherwise returns null.
   * If there is more than one [Entity] of [EntityType], raises an error.
   * */
  fun singleOrNull(): E? = all().singleOrNullOrThrow()
}

/**
 * generic [EntityType] accessor
 * */
val <E : Entity> E.entityType: EntityType<E>
  @Suppress("UNCHECKED_CAST")
  get() = this[Entity.Type] as EntityType<E>

fun interface EntityBuilder<E : Entity> {
  interface Target<E : Entity> {
    operator fun <V : Any> set(attribute: Attributes<in E>.Required<V>, value: V)
    operator fun <V : Any> set(attribute: Attributes<in E>.Optional<V>, value: V?)
    operator fun <V : Any> set(attribute: Attributes<in E>.Many<V>, values: Set<V>)
  }

  fun build(target: Target<E>)
}

/**
 * Executes [EntityBuilder] and builds a list comprising initialized attributes.
 * [DefaultValue] is used for un-initialized attributes
 * */
internal fun <E : Entity> EntityType<E>.buildAttributes(builder: EntityBuilder<E>): List<Pair<Attribute<*>, Any>> = let { entityType ->
  buildList {
    val initializedAttrs = IntOpenHashSet()
    builder.build(object : EntityBuilder.Target<E> {
      private fun <V : Any> add(attribute: EntityAttribute<in E, V>, value: V) {
        initializedAttrs.add(attribute.attr.eid)
        @Suppress("UNCHECKED_CAST")
        val attr = attribute.attr as Attribute<Any>
        val indexValue = attribute.toIndexValue(value)
        add(attr to indexValue)
      }

      override fun <V : Any> set(attribute: Attributes<in E>.Required<V>, value: V) {
        add(attribute, value)
      }

      override fun <V : Any> set(attribute: Attributes<in E>.Optional<V>, value: V?) {
        value?.let { add(attribute, it) }
      }

      override fun <V : Any> set(attribute: Attributes<in E>.Many<V>, values: Set<V>) {
        for (v in values) {
          add(attribute, v)
        }
      }

    })
    entityType.entityAttributes.forEach { (ident, entityAttribute) ->
      if (entityAttribute.attr.eid !in initializedAttrs) {
        when {
          entityAttribute.defaultValue != null -> {
            val defaultValue = entityAttribute.defaultValue.provide()
            when {
              entityAttribute.attr.schema.required ->
                add(entityAttribute.attr to requireNotNull(defaultValue) {
                  "defaultValue for $ident is null"
                })

              defaultValue != null ->
                add(entityAttribute.attr to defaultValue)
            }
          }
          entityAttribute.attr.schema.required -> throw TxValidationException("required attribute $ident was not initialized")
        }
      }
    }
  }
}

fun entityModule(entityClass: KClass<out Entity>): String = entityClass.java.module.name ?: "<unknown>"
