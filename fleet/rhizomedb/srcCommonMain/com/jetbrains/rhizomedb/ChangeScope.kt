// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.rhizomedb

import com.jetbrains.rhizomedb.add
import com.jetbrains.rhizomedb.impl.entity
import com.jetbrains.rhizomedb.impl.generateSeed
import fleet.util.openmap.Key
import fleet.util.openmap.MutableOpenMap
import com.jetbrains.rhizomedb.add as add_cs
import com.jetbrains.rhizomedb.clear as clear_cs
import com.jetbrains.rhizomedb.delete as delete_cs
import com.jetbrains.rhizomedb.new as new_cs
import com.jetbrains.rhizomedb.remove as remove_cs
import com.jetbrains.rhizomedb.set as set_cs
import com.jetbrains.rhizomedb.update as update_cs
import com.jetbrains.rhizomedb.upsert as upsert_cs

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

  fun mutate(instruction: Instruction): Unit = com.jetbrains.rhizomedb.mutate(instruction)
  fun registerMixin(mixin: Mixin<*>): Unit = com.jetbrains.rhizomedb.registerMixin(mixin)
  fun register(entityType: EntityType<*>): Unit = com.jetbrains.rhizomedb.register(entityType)
  fun register(vararg entityType: EntityType<*>): Unit = com.jetbrains.rhizomedb.register(*entityType)
  operator fun <E : Entity, V : Any> E.set(attribute: Attributes<E>.Required<V>, value: V): Unit = set_cs(attribute, value)
  operator fun <E : Entity, V : Any> E.set(attribute: Attributes<E>.Optional<V>, value: V?): Unit = set_cs(attribute, value)
  operator fun <E : Entity, V : Any> E.set(attribute: Attributes<E>.Many<V>, values: Set<V>): Unit = set_cs(attribute, values)
  fun <E : Entity, V : Any> E.add(attribute: Attributes<E>.Many<V>, value: V): Unit = add_cs(attribute, value)
  fun <E : Entity, V : Any> E.remove(attribute: Attributes<E>.Many<V>, value: V): Unit = remove_cs(attribute, value)
  fun <E : Entity, V : Any> E.clear(attribute: Attributes<E>.Many<V>): Unit = clear_cs(attribute)
  fun <E : Entity> E.update(builder: EntityBuilder<E>): Unit = update_cs(builder)
  fun Entity.delete(): Unit = delete_cs()
  fun <T> withPartOf(entity: Entity, body: ChangeScope.() -> T): T = com.jetbrains.rhizomedb.withPartOf(entity, body)
  fun <T> withDefaultPart(partition: Int, body: ChangeScope.() -> T): T = com.jetbrains.rhizomedb.withDefaultPart(partition, body)
  fun effect(f: ChangeScope.() -> Unit): Unit = com.jetbrains.rhizomedb.effect(f)
  fun <E : Entity> EntityType<E>.new(builder: EntityBuilder<E> = EntityBuilder {}): E = new_cs(builder)
  fun <E : Entity, V : Any> EntityType<E>.upsert(attribute: EntityAttribute<E, V>, value: V, builder: EntityBuilder<E>): E = upsert_cs(attribute, value, builder)
}

/**
 * Low-level api to apply [Instruction] to the mutable db.
 * Used for custom [Instruction]s.
 * */
context(cs: ChangeScope)
fun mutate(instruction: Instruction) {
  cs.context.mutate(instruction)
}

/**
 * Adds [Mixin] to the database.
 * Informatin about it's attributes will be made available for queries.
 * */
context(cs: ChangeScope)
fun registerMixin(mixin: Mixin<*>) {
  registerAttributes(mixin)
}

/**
 * Adds [EntityType] to the database.
 * Information about [EntityType] and it's attributes will be made available for queries.
 * All the entities having this [EntityType] will be reified,
 * meaning that [Entity.EntityObject]s will be constructed and stored.
 * */
context(cs: ChangeScope)
fun register(entityType: EntityType<*>): Unit =
  cs.context.run {
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

context(cs: ChangeScope)
fun register(vararg entityTypes: EntityType<*>) {
  entityTypes.forEach { register(it) }
}

/**
 * Sets a value to the attribute of a given entity.
 * If the value is already stored in the db, will not emit any novelty and will not cause any visible effects.
 * */
context(cs: ChangeScope)
operator fun <E : Entity, V : Any> E.set(
  attribute: Attributes<E>.Required<V>,
  value: V,
): Unit = cs.context.run {
  @Suppress("UNCHECKED_CAST")
  add(eid, attribute.attr as Attribute<Any>, attribute.toIndexValue(value))
}

/**
 * Sets a value to the attribute of a given entity.
 * When null is given as a value, it means retraction of the attribute.
 * Database does not store nulls.
 * If the value is already stored in the db, will not emit any novelty and will not cause any visible effects.
 * */
context(cs: ChangeScope)
operator fun <E : Entity, V : Any> E.set(
  attribute: Attributes<E>.Optional<V>,
  value: V?,
): Unit = cs.context.run {
  when {
    value == null ->
      mutate(RetractAttribute(eid, attribute.attr, generateSeed()))
    else ->
      @Suppress("UNCHECKED_CAST")
      add(eid, attribute.attr as Attribute<Any>, attribute.toIndexValue(value))
  }
}

context(cs: ChangeScope)
operator fun <E : Entity, V : Any> E.set(attribute: Attributes<E>.Many<V>, values: Set<V>): Unit =
  let { entity ->
    entity.clear_cs(attribute)
    for (value in values) {
      entity.add(attribute, value)
    }
  }

/**
 * Adds a new value to the multi-valued attribute.
 * If the value is already stored in the db, will not emit any novelty and will not cause any visible effects.
 * */
context(cs: ChangeScope)
fun <E : Entity, V : Any> E.add(attribute: Attributes<E>.Many<V>, value: V): Unit =
  cs.context.run {
    @Suppress("UNCHECKED_CAST")
    add(eid, attribute.attr as Attribute<Any>, attribute.toIndexValue(value))
  }

/**
 * Removes a value from the multi-valued attribute.
 * If value is not stored there, will have not visible effects and no novelty is produced.
 * */
context(cs: ChangeScope)
fun <E : Entity, V : Any> E.remove(attribute: Attributes<E>.Many<V>, value: V): Unit =
  cs.context.run {
    @Suppress("UNCHECKED_CAST")
    remove_cs(eid, attribute.attr as Attribute<Any>, attribute.toIndexValue(value))
  }

/**
 * Retracts a given multi-valued attribute from the entity completely, leaving no datoms left.
 * */
context(cs: ChangeScope)
fun <E : Entity, V : Any> E.clear(attribute: Attributes<E>.Many<V>): Unit =
  cs.context.run {
    mutate(RetractAttribute(eid, attribute.attr, generateSeed()))
  }

context(cs: ChangeScope)
fun <E : Entity> E.update(builder: EntityBuilder<E>): Unit =
  let { entity ->
    cs.context.run {
      builder.build(object : EntityBuilder.Target<E> {
        override fun <V : Any> set(attribute: Attributes<in E>.Required<V>, value: V) = entity.set_cs(attribute, value)
        override fun <V : Any> set(attribute: Attributes<in E>.Optional<V>, value: V?) = entity.set_cs(attribute, value)
        override fun <V : Any> set(attribute: Attributes<in E>.Many<V>, values: Set<V>) = entity.set_cs(attribute, values)
      })
    }
  }

/**
 * Retracts entity.
 * Handles cascade deletes and referenced cleanup automatically.
 * */
context(cs: ChangeScope)
fun Entity.delete(): Unit =
  cs.context.retractEntity(eid)

/**
 * Sets the partition of a given entity as [defaultPart].
 * */
context(_: ChangeScope)
fun <T> withPartOf(entity: Entity, body: ChangeScope.() -> T): T =
  withDefaultPart(partition(entity.eid), body)

/**
 * Sets [defaultPart]
 * */
context(cs: ChangeScope)
fun <T> withDefaultPart(partition: Int, body: ChangeScope.() -> T): T =
  cs.context.alter(cs.context.impl.withDefaultPart(partition)) { with(cs) { body() } }

/**
 * Registers effect.
 * When and how effects are executed is a defined by the current [Mut] impl.
 * Generally it should be used to run any side-effects of a given transaction.
 * */
context(_: ChangeScope)
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
context(cs: ChangeScope)
fun <E : Entity> EntityType<E>.new(builder: EntityBuilder<E> = EntityBuilder {}): E = let { entityType ->
  require(entity(entityType.eid) != null) {
    "Entity type '${entityType.entityTypeIdent}' is not registered.\nDid you export package to rhizomedb?\nIt should be registered automatically, report and use ChangeScope.register as mitigation"
  }
  val eid = cs.context.impl.createEntity(pipeline = cs.context,
                                      entityTypeEid = entityType.eid,
                                      initials = entityType.buildAttributes(builder))
  @Suppress("UNCHECKED_CAST")
  cs.context.impl.entity(eid) as E
}

context(cs: ChangeScope)
fun <E : Entity, V : Any> EntityType<E>.upsert(attribute: EntityAttribute<E, V>, value: V, builder: EntityBuilder<E>): E =
  let { entityType ->
    entity(attribute, value)?.apply { update_cs(builder) } ?: entityType.new {
      when (attribute) {
        is Attributes<E>.Many<V> -> it[attribute] = setOf(value)
        is Attributes<E>.Optional<V> -> it[attribute] = value
        is Attributes<E>.Required<V> -> it[attribute] = value
      }
      builder.build(it)
    }
  }

/**
 * Key for data associated with a particular change
 */
interface ChangeScopeKey<V : Any> : Key<V, ChangeScope>