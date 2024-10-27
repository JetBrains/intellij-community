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

interface Presentable {
  val presentableText: String
}

fun Entity.entityTypeIdent(): String {
  return with(DbContext.threadBound) {
    entityType(eid)?.let { entityTypeEID ->
      entityTypeIdent(entityTypeEID) ?: error("")
    } ?: error("")
  }
}
