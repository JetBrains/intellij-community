// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.rhizomedb

import com.jetbrains.rhizomedb.impl.entity
import com.jetbrains.rhizomedb.impl.generateSeed
import fleet.util.openmap.MutableOpenMap
import kotlin.reflect.KClass

/**
 * Asserts that current thread-bound [DbContext] contains mutable db, constructs [ChangeScope] for it, and runs body with it
 * */
fun <T> requireChangeScope(body: ChangeScope.() -> T): T =
  DbContext.threadBound.ensureMutable {
    withChangeScope(body)
  }

/**
 * Given a mutable [DbContext] will construct [ChangeScope] for it and run [body] with it
 * */
fun <T> DbContext<Mut>.withChangeScope(body: ChangeScope.() -> T): T =
  ChangeScope.fromContext(this).body()

/**
 * Represents a current transaction.
 * Meant to be used as a context receiver for any code that is meant to run within a transaction,
 * enablding write operations on it.
 * All writes are immediately observable on the current thread and never seen by anyone else until published
 * in a form of a new [DB] snapshot.
 * Despite being an interface it is not meant to be implemented other than by delegation.
 * */
interface ChangeScope {
  /**
   * [DbContext] for which [ChangeScope] is constructed.
   * Use it if you need to access a lower-level apis.
   * */
  val context: DbContext<Mut>

  /**
   * Partition used to construct new entities in, when no explicit partition is given to [build].
   * */
  val defaultPart: Part get() = context.impl.defaultPart

  /**
   * Mutable context map for current transaction.
   * When transaction finished and [Change] is constructed by [change] fn, [meta] will become [Change.meta].
   * So it can be used to associates data with the resulting change
   * @see [Change.meta]
   **/
  val meta: MutableOpenMap<ChangeScope> get() = context.impl.meta

  /**
   * [DB] snapshot for which the transaction was started.
   * */
  val dbBefore: DB get() = context.impl.dbBefore

  companion object {
    /**
     * Constructs [ChangeScope] for a given [DbContext].
     * */
    fun fromContext(context: DbContext<Mut>): ChangeScope =
      object : ChangeScope {
        override val context get() = context
      }
  }

  /**
   * Low-level api to apply [Instruction] to the mutable db.
   * Used for custom [Instruction]s.
   * */
  fun mutate(instruction: Instruction) {
    context.mutate(instruction)
  }

  /**
   * Adds [Mixin] to the database.
   * Informatin about it's attributes will be made available for queries.
   * */
  fun registerMixin(mixin: Mixin<*>) {
    registerAttributes(mixin)
  }

  /**
   * Adds [EntityType] to the database.
   * Information about [EntityType] and it's attributes will be made available for queries.
   * All the entities having this [EntityType] will be reified,
   * meaning that [Entity.EntityObject]s will be constructed and stored.
   * */
  fun register(entityType: EntityType<*>): Unit =
    context.run {
      if (entity(entityType.eid) == null) {
        registerAttributes(entityType)
        mutate(CreateEntity(
          eid = entityType.eid,
          entityTypeEid = EntityType.eid,
          attributes = buildList {
            @Suppress("UNCHECKED_CAST")
            add(EntityType.Ident.attr as Attribute<String> to entityType.entityTypeIdent)
            add(Entity.EntityObject.attr to entityType)
            add(Entity.Module.attr to entityType.module)
            add(EntityType.PossibleAttributes.attr to Entity.Type.attr.eid)
            add(EntityType.PossibleAttributes.attr to Entity.EntityObject.attr.eid)
            entityType.entityAttributes.values.forEach { entityAttribute ->
              @Suppress("UNCHECKED_CAST")
              add(EntityType.PossibleAttributes.attr as Attribute<EID> to entityAttribute.eid)
            }
          },
          seed = generateSeed()
        ))
        if (entityType != EntityType) {
          mutate(ReifyEntities(entityType.eid, generateSeed()))
        }
      }
    }


  /**
   * Sets a value to the attribute of a given entity.
   * If the value is already stored in the db, will not emit any novelty and will not cause any visible effects.
   * */
  operator fun <E : Entity, V : Any> E.set(
    attribute: Attributes<E>.Required<V>,
    value: V,
  ): Unit = context.run {
    @Suppress("UNCHECKED_CAST")
    add(eid, attribute.attr as Attribute<Any>, attribute.toIndexValue(value))
  }

  /**
   * Sets a value to the attribute of a given entity.
   * When null is given as a value, it means retraction of the attribute.
   * Database does not store nulls.
   * If the value is already stored in the db, will not emit any novelty and will not cause any visible effects.
   * */
  operator fun <E : Entity, V : Any> E.set(
    attribute: Attributes<E>.Optional<V>,
    value: V?,
  ): Unit = context.run {
    when {
      value == null ->
        mutate(RetractAttribute(eid, attribute.attr, generateSeed()))
      else ->
        @Suppress("UNCHECKED_CAST")
        add(eid, attribute.attr as Attribute<Any>, attribute.toIndexValue(value))
    }
  }

  operator fun <E : Entity, V : Any> E.set(attribute: Attributes<E>.Many<V>, values: Set<V>): Unit =
    let { entity ->
      entity.clear(attribute)
      for (value in values) {
        entity.add(attribute, value)
      }
    }

  /**
   * Adds a new value to the multi-valued attribute.
   * If the value is already stored in the db, will not emit any novelty and will not cause any visible effects.
   * */
  fun <E : Entity, V : Any> E.add(attribute: Attributes<E>.Many<V>, value: V): Unit =
    context.run {
      @Suppress("UNCHECKED_CAST")
      add(eid, attribute.attr as Attribute<Any>, attribute.toIndexValue(value))
    }

  /**
   * Removes a value from the multi-valued attribute.
   * If value is not stored there, will have not visible effects and no novelty is produced.
   * */
  fun <E : Entity, V : Any> E.remove(attribute: Attributes<E>.Many<V>, value: V): Unit =
    context.run {
      @Suppress("UNCHECKED_CAST")
      remove(eid, attribute.attr as Attribute<Any>, attribute.toIndexValue(value))
    }

  /**
   * Retracts a given multi-valued attribute from the entity completely, leaving no datoms left.
   * */
  fun <E : Entity, V : Any> E.clear(attribute: Attributes<E>.Many<V>): Unit =
    context.run {
      mutate(RetractAttribute(eid, attribute.attr, generateSeed()))
    }

  fun <E : Entity> E.update(builder: EntityBuilder<E>): Unit =
    let { entity ->
      context.run {
        builder.build(object : EntityBuilder.Target<E> {
          override fun <V : Any> set(attribute: Attributes<in E>.Required<V>, value: V) = entity.set(attribute, value)
          override fun <V : Any> set(attribute: Attributes<in E>.Optional<V>, value: V?) = entity.set(attribute, value)
          override fun <V : Any> set(attribute: Attributes<in E>.Many<V>, values: Set<V>) = entity.set(attribute, values)
        })
      }
    }

  /**
   * Retracts entity.
   * Handles cascade deletes and referenced cleanup automatically.
   * */
  fun Entity.delete(): Unit =
    context.retractEntity(eid)

  /**
   * Sets the partition of a given entity as [defaultPart].
   * */
  fun <T> withPartOf(entity: Entity, body: ChangeScope.() -> T): T =
    withDefaultPart(partition(entity.eid), body)

  /**
   * Sets [defaultPart]
   * */
  fun <T> withDefaultPart(partition: Int, body: ChangeScope.() -> T): T =
    context.alter(context.impl.withDefaultPart(partition)) { body() }

  /**
   * Registers effect.
   * When and how effects are executed is a defined by the current [Mut] impl.
   * Generally it should be used to run any side-effects of a given transaction.
   * */
  fun effect(f: ChangeScope.() -> Unit) {
    DbContext.threadBound.ensureMutable {
      mutate(EffectInstruction {
        withChangeScope(f)
      })
    }
  }

  /**
   * Constructs entity of a given [EntityType] in [defaultPart].
   * [builder] must assoc all required attributes.
   * [EntityAttribute.DefaultValue] is used to initialize attributes, which were not initialized by [builder].
   * */
  fun <E : Entity> EntityType<E>.new(builder: EntityBuilder<E> = EntityBuilder {}): E = let { entityType ->
    require(entity(entityType.eid) != null) {
      "Entity type '${entityType.entityTypeIdent}' is not registered. It should be registered automatically, report and use ChangeScope.register as mitigation"
    }
    val eid = context.impl.createEntity(pipeline = context,
                                        entityTypeEid = entityType.eid,
                                        initials = entityType.buildAttributes(builder))
    @Suppress("UNCHECKED_CAST")
    context.impl.entity(eid) as E
  }

  fun <E : Entity, V : Any> EntityType<E>.upsert(attribute: EntityAttribute<E, V>, value: V, builder: EntityBuilder<E>): E =
    let { entityType ->
      entity(attribute, value)?.apply { update(builder) } ?: entityType.new {
        when (attribute) {
          is Attributes<E>.Many<V> -> it[attribute] = setOf(value)
          is Attributes<E>.Optional<V> -> it[attribute] = value
          is Attributes<E>.Required<V> -> it[attribute] = value
        }
        builder.build(it)
      }
    }
}