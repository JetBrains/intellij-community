package com.jetbrains.rhizomedb

import kotlin.reflect.KClass

/**
 * [Mixin] allows to attach same attributes to multiple [EntityType]s
 * Unlike [EntityType], [Mixin] has no identity and is not represeted in the database as [Entity].
 * It's only job is to carry a set of attributes, which can be attached to [EntityType]s.
 * One example of it is [Entity.Companion], which defines attributes universal to all [EntityType]s.
 * */
abstract class Mixin<E : Entity>(
  ident: String,
  module: String,
  vararg mixins: Mixin<in E>
) : Attributes<E>(ident, module, merge(mixins.toList())) {

  /**
   * Tell [Mixin] to use qualified name of the given [KClass] as [namespace]
   * It may be useful to guarantee the uniqueness of the [namespace].
   * But it could backfire for durable entities,
   * if one renames the class, or moves it to other package.
   * */
  constructor(
    ident: KClass<out Entity>,
    vararg mixins: Mixin<in E>
  ) : this(requireNotNull(ident.qualifiedName), entityModule(ident), *mixins)

  constructor(ident: KClass<out Entity>, version: Int?, vararg mixins: Mixin<in E>) : this(
    ident = ident.qualifiedName!! + if (version != null) ":$version" else "",
    module = entityModule(ident),
    mixins = mixins,
  )

  override fun toString(): String = "Mixin($namespace)"
}
