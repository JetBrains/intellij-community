// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.rhizomedb

//import kotlin.internal.OnlyInputTypes
import com.jetbrains.rhizomedb.impl.*
import fleet.util.singleOrNullOrThrowWithMessage
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1

/**
 * Database will maintain index of all values for this property so you can find all entities with given value.
 *
 * ```
 * interface Foo : LegacyEntity {
 *   @Indexed var foo : String
 * }
 *
 * val foo1 = new(Foo::class) { foo = "foo" }
 * val foo2 = new(Foo::class) { foo = "foo" }
 * assertEquals(setOf(foo1, foo2), lookup(Foo::foo, "foo"))
 * ```
 * `lookup` on property not marked with @Indexed will throw runtime exception.
 * References to other entities are always indexed and should not be marked.
 * Properties marked with [Unique] are also indexed.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY)
annotation class Indexed

/**
 * Asserts that values of this property are unique across whole db.
 * ```
 * interface Foo {
 *   @Unique var unique: String
 * }
 *
 * new(Foo::class) { unique = "pretty unique" }
 * assertFailsWith<Throwable> {
 *   new(Foo::class) { unique = "pretty unique" }
 * }
 * ```
 *
 * [Unique] implies [Indexed]
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY)
annotation class Unique

/**
 * Valid only for properties of type Set<T> (or MutableSet).
 * Properties marked with [Many] are always optional and can be left uninitialized.
 *
 * ### @Many for data properties
 *
 * Instead of storing set of values as single collection object, database will store one record per value.
 * ```
 * interface Foo : LegacyEntity {
 *   @Many var values: Set<String> }
 *
 * new(Foo::class) {
 *   values = setOf("foo", "bar", "baz")
 * }
 * ```
 * Actual data stored looks like this `[[1, "Foo/foo", "foo"], [1, "Foo/foo", "bar"], [1, "Foo/foo", "baz"]]`
 * This works well for plain data values if you want to index them with [Indexed] annotation.
 * ```
 * interface Bar : LegacyEntity {
 *   @Indexed @Many
 *   var values: Set<String>
 * }
 *
 * val bar = new(Bar::class) {
 *   values = setOf("foo", "bar")
 * }
 * assertEquals(setOf(foo), lookup(Bar::values, "foo"))
 * assertEquals(setOf(foo), lookup(Bar::values, "bar"))
 * ```
 *
 * ### @Many for relations
 *
 * For "has many" relations between entities this annotation is _required_, you will receive runtime exception without it.
 * ```
 * interface Ref : LegacyEntity {}
 * interface Container : LegacyEntity {
 *   @Many
 *   var refs : MutableSet<Ref>
 * }
 *
 * val r1 = new(Ref::class) {}
 * val r2 = new(Ref::class) {}
 * val c = new(Container::class) {
 *   refs = hashSetOf(r1, r2)
 * }
 * r2.delete()
 * assertEquals(c.refs, hashSetOf(r1))
 *```
 * Under the hood rhizome will store two facts about such relation i.e. `[[c_id, "Container/refs", r1_id], [c_id, "Container/refs", r2_id]]`.
 * This way db can track each relation and clean up Container::refs when `r2` is deleted.
 *
 * Like any relation, @Many attributes are indexed and can be queried with [datomsForMask] and [lookup]
 * ```
 * assertEquals(c, r1.lookupOne(Container::refs))
 * ```
 *
 * */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY)
annotation class Many

/**
 * Overrides default name for database attribute used to store this property.
 * Default name is calculated as `declaringClass.name + "/" + prop.name`.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(/*AnnotationTarget.PROPERTY,*/ AnnotationTarget.CLASS)
annotation class Ident(val ident: String)

/**
 * Entity types with differing Versions are considered to be different.
 * Entities with different types may co-exist in a database and allow various migration policies.
 * New version of a code declaring an entity with a new version will not directly see entities of the past versions via the Entity API.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(/*AnnotationTarget.PROPERTY,*/ AnnotationTarget.CLASS)
annotation class Version(val version: String)

/**
 * Valid only for reference properties.
 * Referenced entity will be deleted along with the one holding this property.
 * ```
 * interface Foo : LegacyEntity {}
 * interface Bar : LegacyEntity {
 *   @CascadeDelete var foo : Foo
 * }
 *
 * val foo = new(Foo::class)
 * val bar = new(Bar::class) {
 *   this.foo = foo
 * }
 * bar.delete()
 * assertFalse(foo.exists())
 * ```
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY)
annotation class CascadeDelete

/**
 * Valid only for reference properties.
 * If referenced entity is deleted, this one will be deleted too.
 * ```
 * interface Foo : LegacyEntity {}
 * interface Bar : LegacyEntity {
 *   @CascadeDeleteBy var foo : Foo
 * }
 *
 * val foo = new(Foo::class) {}
 * val bar = new(Bar::class) {
 *   this.foo = foo
 * }
 *
 * foo.delete()
 * assertFalse(bar.exists())
 * ```
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY)
annotation class CascadeDeleteBy

fun <T : Entity> T.getEntityClass(): KClass<T> {
  return entityClass as KClass<T>
}

interface Presentable {
  val presentableText: String
}

@JvmName("attribute1")
fun attribute(prop: KProperty1<out LegacyEntity, *>): Attribute<*>? {
  return prop.attribute()
}

fun KProperty1<out LegacyEntity, *>.attribute(): Attribute<*>? {
  return DbContext.threadBound.impl.attributeForProperty(this)
}

internal fun KClass<out Entity>.entityTypeIdent(): String {
  val kclass = this
  return with(DbContext.threadBound) {
    entityTypeForClass(kclass)?.let { entityTypeEID ->
      entityTypeIdent(entityTypeEID) ?: error("entity type doesn't have ident $kclass")
    } ?: error("entity type not found for $kclass")
  }
}

fun Entity.entityTypeIdent(): String {
  return with(DbContext.threadBound) {
    entityType(eid)?.let { entityTypeEID ->
      entityTypeIdent(entityTypeEID) ?: error("")
    } ?: error("")
  }
}

fun KClass<out LegacyEntity>.entityType(): EID? {
  return DbContext.threadBound.impl.entityTypeForClass(this)
}

/**
 * Verify that this entity exists in context database.
 *
 * Entity object is just a pointer that retrieves its properties from context db,
 * as such it is possible that there is no knowledge of this entity in current db:
 * perhaps it was already deleted or this entity object comes from alternative db version.
 *
 * Always returns true for bound entities
 */
fun Entity.exists(): Boolean = DbContext.threadBound.impl.entityExists(eid)

fun Q.entityExists(eid: EID): Boolean =
  queryIndex(IndexQuery.GetOne(eid, Entity.Type.attr as Attribute<EID>)) != null

fun <T : Entity> T?.takeIfExists(): T? = if (this?.exists() == true) this else null

/**
 * Finds all entities with property [property] having value [value]
 *
 * see [Indexed]
 */

//inline fun <reified T : Entity, /*@OnlyInputTypes*/ R : Any> lookup(property: KMutableProperty1<T, R?>, value: R): Set<T> {
//  return DbContext.threadBound.impl.lookupImpl(property, value, T::class).toHashSet(
//}

inline fun <reified T : LegacyEntity, /*@OnlyInputTypes*/ R : Any> lookup(property: KMutableProperty1<T, in R>, value: R): Set<T> {
  return DbContext.threadBound.impl.lookupImpl(property, value, T::class)
}

/**
 * same as [lookup] but for [Many] properties
 */
@JvmName("lookupInCollection")
inline fun <reified T : LegacyEntity, /*@OnlyInputTypes*/ R : Any> lookup(prop: KProperty1<T, MutableCollection<R>>, value: R): Set<T> {
  return DbContext.threadBound.impl.lookupImpl(prop, value, T::class)
}

@JvmName("lookupInImmutableCollection")
inline fun <reified T : LegacyEntity, /*@OnlyInputTypes*/ R : Any> lookup(prop: KMutableProperty1<T, out Collection<R>>, value: R): Set<T> {
  return DbContext.threadBound.impl.lookupImpl(prop, value, T::class)
}

@JvmName("lookupInCollection2")
inline fun <reified T : LegacyEntity, /*@OnlyInputTypes*/ R : Any> lookup(prop: KMutableProperty1<T, out MutableCollection<R>>,
                                                                    value: R): Set<T> {
  return DbContext.threadBound.impl.lookupImpl(prop, value, T::class)
}

@JvmName("propertyValuesMany")
fun <T : LegacyEntity, R> KProperty1<T, Set<R>>.allValues(): Set<R> =
  DbContext.threadBound.allValuesImpl(this) as Set<R>

@JvmName("propertyValuesOne")
fun <T : LegacyEntity, R> KProperty1<T, R>.allValues(): Set<R> =
  DbContext.threadBound.allValuesImpl(this) as Set<R>

private fun <T : LegacyEntity> DbContext<Q>.allValuesImpl(prop: KProperty1<T, *>): Set<*> =
  buildSet {
    attributeForProperty(prop)?.let { attr ->
      when {
        attr.schema.isRef -> queryIndex(IndexQuery.Column(attr)).forEach { datom ->
          add(entity(datom.value as EID))
        }
        else -> queryIndex(IndexQuery.Column(attr)).forEach { datom ->
          add(datom.value)
        }
      }
    }
  }

/**
 * Same as [lookup] but asserts there is at most one such entity
 */
//inline fun <reified T : Entity, /*@OnlyInputTypes*/ R : Any> lookupOne(prop: KMutableProperty1<T, R?>, value: R): T? {
//  return DbContext.threadBound.impl.lookupImpl(prop, value, T::class).singleOrNullOrThrowWithMessage { " searched prop $prop and value $value" }
//}

inline fun <reified T : LegacyEntity, /*@OnlyInputTypes*/ R : Any> lookupOne(prop: KMutableProperty1<T, in R>, value: R): T? {
  return DbContext.threadBound.impl.lookupImpl(prop, value, T::class)
    .singleOrNullOrThrowWithMessage { " searched prop $prop and value $value" }
}

/**
 * Same as [lookup] but asserts there is at most one such entity
 */
@JvmName("lookupInCollection")
inline fun <reified T : LegacyEntity, /*@OnlyInputTypes*/ R : Any> lookupOne(prop: KProperty1<T, MutableCollection<R>>, value: R): T? {
  return DbContext.threadBound.impl.lookupImpl(prop, value,
                                               T::class).singleOrNullOrThrowWithMessage { " searched prop $prop and value $value" }
}

@JvmName("lookupInImmutableCollection")
inline fun <reified T : LegacyEntity, /*@OnlyInputTypes*/ R : Any> lookupOne(prop: KMutableProperty1<T, out Collection<R>>, value: R): T? {
  return DbContext.threadBound.impl.lookupImpl(prop, value,
                                               T::class).singleOrNullOrThrowWithMessage { " searched prop $prop and value $value" }
}

@JvmName("lookupInCollection2")
inline fun <reified T : LegacyEntity, /*@OnlyInputTypes*/ R : Any> lookupOne(prop: KMutableProperty1<T, out MutableCollection<R>>, value: R): T? {
  return DbContext.threadBound.impl.lookupImpl(prop, value,
                                               T::class).singleOrNullOrThrowWithMessage { " searched prop $prop and value $value" }
}

/**
 * Find entities that reference [this] by [prop]
 */
inline fun <reified T : LegacyEntity, /*@OnlyInputTypes*/ R : Any> R.lookup(prop: KMutableProperty1<T, in R>): Set<T> {
  return lookup(prop, this)
}

/**
 * Find entities that reference [this] by [prop]
 */
@JvmName("lookupInCollection")
inline fun <reified T : LegacyEntity, /*@OnlyInputTypes*/ R : Any> R.lookup(prop: KProperty1<T, MutableCollection<R>>): Set<T> {
  return lookup(prop, this).toHashSet()
}

@JvmName("lookupInImmutableCollection")
inline fun <reified T : LegacyEntity, /*@OnlyInputTypes*/ R : Any> R.lookup(prop: KMutableProperty1<T, out Collection<R>>): Set<T> {
  return lookup(prop, this).toHashSet()
}

@JvmName("lookupInCollection2")
inline fun <reified T : LegacyEntity, /*@OnlyInputTypes*/ R : Any> R.lookup(prop: KMutableProperty1<T, out MutableCollection<R>>): Set<T> {
  return lookup(prop, this).toHashSet()
}

/**
 * Find entities that reference [this] by [prop] or throw an exception if more than one found
 */
inline fun <reified T : LegacyEntity, /*@OnlyInputTypes*/ R : Any> R.lookupOne(prop: KMutableProperty1<T, in R>): T? {
  return lookupOne(prop, this)
}

/**
 * Find entity that reference [this] by [prop] or throw an exception if more than one found
 */
@JvmName("lookupInCollection")
inline fun <reified T : LegacyEntity, /*@OnlyInputTypes*/ R : Any> R.lookupOne(prop: KProperty1<T, MutableCollection<R>>): T? {
  return lookupOne(prop, this)
}

@JvmName("lookupInImmutableCollection")
inline fun <reified T : LegacyEntity, /*@OnlyInputTypes*/ R : Any> R.lookupOne(prop: KMutableProperty1<T, out Collection<R>>): T? {
  return lookupOne(prop, this)
}

@JvmName("lookupInCollection2")
inline fun <reified T : LegacyEntity, /*@OnlyInputTypes*/ R : Any> R.lookupOne(prop: KMutableProperty1<T, out MutableCollection<R>>): T? {
  return lookupOne(prop, this)
}

/**
 * Find all entities that implement interface [c] in db [this].
 */
fun <T : LegacyEntity> byEntityType(c: KClass<T>): Set<T> = with(DbContext.threadBound) { byEntityType(c) }

fun <T : LegacyEntity> DbContext<Q>.byEntityType(c: KClass<T>): Set<T> = buildSet {
  queryIndex(IndexQuery.LookupMany(LegacySchema.EntityType.ancestorKClasses, c)).forEach { (entityTypeEID, _, _) ->
    queryIndex(IndexQuery.LookupMany(Entity.Type.attr as Attribute<EID>, entityTypeEID)).forEach { (e, _, _) ->
      add(requireNotNull(entity(e)) {
        "entity was not initialized ${displayEntity(e)}"
      } as T)
    }
  }
}

fun <T : LegacyEntity> singleton(c: KClass<T>): T {
  val res = byEntityType(c)
  return when {
    res.isEmpty() -> error("singleton of type $c not found")
    res.size == 1 -> res.single()
    else -> error("entity of type $c is not single: $res")
  }
}

fun <T : LegacyEntity> singletonOrNull(c: KClass<T>): T? {
  val res = byEntityType(c)
  return when {
    res.isEmpty() -> null
    res.size == 1 -> res.single()
    else -> error("entity of type $c is not single: $res")
  }
}

fun <T : Entity, /*@OnlyInputTypes*/ R> datomsForMask(eid: EID?, prop: KProperty1<T, R>, value: R?): Reducible<Datom> {
  return with(DbContext.threadBound.impl) {
    attributeForProperty(prop)?.let { attr ->
      q(eid, attr, value)
    } ?: emptyReducible<Datom>()
  }
}

fun entity(eid: EID): Entity? = with(DbContext.threadBound) { entity(eid) }

fun <T : LegacyEntity> DbContext<Mut>.new(
  c: KClass<T>,
  builder: T.() -> Unit = {},
): T {
  val entityTypeEID: EID = requireNotNull(entityTypeByClass(c)) {
    val ident = entityTypeNameForClass(c)
    val existingClassLayer = entityTypeByIdent(ident)?.let { entityTypeEID ->
      getOne(entityTypeEID, LegacySchema.EntityType.kClass)?.java?.module?.layer
    }
    val cLayer = c.java.module.layer
    "entity type is not found for class $c, existing classlayer $existingClassLayer, out class layer: $cLayer"
  }
  initAttributes(entityTypeEID)

  val initialsWithDefaults = buildList {
    queryIndex(
      IndexQuery.GetMany(
        entityTypeEID,
        EntityType.PossibleAttributes.attr as Attribute<EID>
      )
    ).forEach { (_, _, attrEid) ->
      val attr = Attribute<Any>(attrEid as EID)
      (entity(attr.eid) as EntityAttribute<*, *>?)?.defaultValue?.provide()?.let { defaultValue ->
        add(attr to defaultValue)
      }
    }
  }

  val entityEID = impl.createEntity(this, entityTypeEID, initialsWithDefaults)
  val t = entity(entityEID) as T
  (t as? BaseEntity)?.initialized = false
  t.builder()
  (t as? BaseEntity)?.initialized = true
  assertRequiredAttrs(entityEID, entityTypeEID)
  return t
}

class OutOfDbContext :
  RuntimeException("Free entities require context db to be used. Open transaction with db.tx or setup db to read with asOf(db)")

class OutOfMutableDbContext :
  RuntimeException("Free entities require mutable context db to be updated. Open transaction with db.tx or setup db to read with asOf(db)")

class EntityDoesNotExistException(message: String) : RuntimeException(message)

class EntityAttributeIsNotInitialized(entityDisplay: String, attributeDisplay: String) :
  RuntimeException("Access to not initialized attribute $attributeDisplay of entity $entityDisplay")
