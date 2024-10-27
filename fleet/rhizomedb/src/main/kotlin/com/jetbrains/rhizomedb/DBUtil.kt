// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.rhizomedb

import com.jetbrains.rhizomedb.impl.EidGen
import com.jetbrains.rhizomedb.impl.entityTypePossibleAttributes
import com.jetbrains.rhizomedb.impl.generateSeed
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet

fun DbContext<Q>.displayAttribute(attribute: Attribute<*>): String = impl.displayAttribute(attribute)
fun Q.displayAttribute(attribute: Attribute<*>): String = 
  attributeIdent(attribute)!!

fun entityType(entityEID: EID): EID? = DbContext.threadBound.entityType(entityEID)
fun DbContext<Q>.entityType(entityEID: EID): EID? = impl.entityType(entityEID)
fun Q.entityType(entityEID: EID): EID? = getOne(entityEID, Entity.Type.attr as Attribute<EID>)

internal fun DbContext<Q>.displayEntityType(typeEID: EID): String = impl.displayEntityType(typeEID)
internal fun Q.displayEntityType(typeEID: EID): String = entityTypeIdent(typeEID) ?: "unknown"

internal fun displayEntity(eid: EID): String = DbContext.threadBound.displayEntity(eid)
fun DbContext<Q>.displayEntity(eid: EID): String = impl.displayEntity(eid)
internal fun Q.displayEntity(eid: EID): String = "${entityType(eid)?.let { typeEID -> displayEntityType(typeEID) }}[$eid]"

internal fun Q.displayDatoms(datoms: Iterable<Datom>): String =
  datoms.joinToString(prefix = "[",
                      separator = "\n",
                      postfix = "]") { d -> displayDatom(d) }

internal fun displayDatoms(datoms: Iterable<Datom>): String =
  DbContext.threadBound.displayDatoms(datoms)

internal fun DbContext<Q>.displayDatoms(datoms: Iterable<Datom>): String =
  impl.displayDatoms(datoms)

internal fun displayDatom(datom: Datom): String = DbContext.threadBound.displayDatom(datom)
fun DbContext<Q>.displayDatom(datom: Datom): String = impl.displayDatom(datom)
internal fun Q.displayDatom(datom: Datom): String =
  "Datom[${displayEntity(datom.eid)}, ${displayAttribute(datom.attr)}, ${datom.value}, ${datom.tx}, ${datom.added}]"

internal fun Q.displayQuery(indexQuery: IndexQuery<*>): String =
  when (indexQuery) {
    is IndexQuery.All -> "All"
    is IndexQuery.Column<*> -> "(? ${displayAttribute(indexQuery.attribute)} ?)"
    is IndexQuery.Contains<*> -> "(${displayEntity(indexQuery.eid)} ${displayAttribute(indexQuery.attribute)} ${indexQuery.value}"
    is IndexQuery.Entity -> "(${displayEntity(indexQuery.eid)})"
    is IndexQuery.GetMany<*> -> "(${displayEntity(indexQuery.eid)} ${displayAttribute(indexQuery.attribute)} ?)"
    is IndexQuery.GetOne<*> -> "(${displayEntity(indexQuery.eid)} ${displayAttribute(indexQuery.attribute)} ?)"
    is IndexQuery.LookupMany<*> -> "(? ${displayAttribute(indexQuery.attribute)} ${indexQuery.value})"
    is IndexQuery.LookupUnique<*> -> "(? ${displayAttribute(indexQuery.attribute)} ${indexQuery.value})"
    is IndexQuery.RefsTo -> "(? ? ${displayEntity(indexQuery.eid)}"
  }

fun <T : Any> DbContext<Q>.getOne(eid: EID, attr: Attribute<T>, throwIfNoEntity: Boolean = false): T? = impl.getOne(eid, attr,
                                                                                                                    throwIfNoEntity)

internal fun <T : Any> Q.getOne(eid: EID, attr: Attribute<T>, throwIfNoEntity: Boolean = false): T? =
  queryIndex(IndexQuery.GetOne(eid, attr, throwIfNoEntity))?.x

fun <T : Any> DbContext<Q>.getMany(eid: EID, attr: Attribute<T>): List<T> = impl.getMany(eid, attr)
internal fun <T : Any> Q.getMany(eid: EID, attr: Attribute<T>): List<T> =
  queryIndex(IndexQuery.GetMany(eid, attr)).map { it.value as T }

fun <T : Any> DbContext<Q>.lookupSingle(a: Attribute<T>, v: T): EID = impl.lookupSingle(a, v)
internal fun <T : Any> Q.lookupSingle(a: Attribute<T>, v: T): EID =
  requireNotNull(lookupOne(a, v)) { "lookupSingle($a, $v) didn't find anything" }

fun <T : Any> DbContext<Q>.lookupOne(a: Attribute<T>, v: T): EID? = impl.lookupOne(a, v)
fun <T : Any> Q.lookupOne(a: Attribute<T>, v: T): EID? =
  queryIndex(IndexQuery.LookupUnique(a, v))?.eid

fun <T : Any> DbContext<Q>.lookup(a: Attribute<T>, v: T): Reducible<EID> = impl.lookup(a, v)
fun <T : Any> Q.lookup(a: Attribute<T>, v: T): Reducible<EID> =
  queryIndex(IndexQuery.LookupMany(a, v)).map { d -> d.eid }.reducible()

fun DbContext<Q>.attributeIdent(attr: Attribute<*>): String? = impl.attributeIdent(attr)
internal fun Q.attributeIdent(attr: Attribute<*>): String? =
  getOne(attr.eid, EntityAttribute.Ident.attr as Attribute<String>)


fun DbContext<Q>.attributeByIdent(ident: String): Attribute<*>? = impl.attributeByIdent(ident)
fun Q.attributeByIdent(ident: String): Attribute<*>? =
  lookupOne(EntityAttribute.Ident.attr as Attribute<String>, ident)?.let { eid -> Attribute<Any>(eid) }

fun DbContext<Q>.entityTypeIdent(typeEID: EID): String? = impl.entityTypeIdent(typeEID)
fun Q.entityTypeIdent(typeEID: EID): String? = getOne(typeEID, EntityType.Ident.attr as Attribute<String>)

fun DbContext<Q>.entityTypesByName(name: String): Set<EID> = impl.entityTypesByName(name)
internal fun Q.entityTypesByName(name: String): Set<EID> =
  queryIndex(IndexQuery.LookupMany(EntityType.Name.attr as Attribute<String>, name)).mapTo(HashSet()) { datom -> datom.eid }

fun DbContext<Q>.entityTypeByIdent(ident: String): EID? = impl.entityTypeByIdent(ident)
internal fun Q.entityTypeByIdent(ident: String): EID? = lookupOne(EntityType.Ident.attr as Attribute<String>, ident)

fun DbContext<Q>.contains(datom: Datom): Boolean = impl.contains(datom)
fun Q.contains(datom: Datom): Boolean =
  queryIndex(IndexQuery.Contains(datom.eid, datom.attr as Attribute<Any>, datom.value)) != null

fun Q.entitiesToRetract(eid: EID): IntSet {
  val entitiesToRetract = IntOpenHashSet()
  entitiesToRetract.add(eid)
  val retractedEntities = IntOpenHashSet()
  val stack = IntArrayList()
  stack.add(eid)
  while (stack.isNotEmpty()) {
    val nextEID = stack.removeInt(stack.size - 1)
    if (retractedEntities.add(nextEID)) {
      queryIndex(IndexQuery.Entity(nextEID)).forEach { datom ->
        if (datom.attr.schema.cascadeDelete) {
          val value = datom.value as EID
          stack.add(value)
          if (partition(nextEID) != partition(value)) {
            entitiesToRetract.add(value)
          }
        }
      }
      queryIndex(IndexQuery.RefsTo(nextEID)).forEach { datom ->
        when {
          datom.attr.schema.cascadeDeleteBy || datom.attr.schema.required -> {
            stack.add(datom.eid)
            if (partition(nextEID) != partition(datom.eid)) {
              entitiesToRetract.add(datom.eid)
            }
          }
        }
      }
    }
  }
  return entitiesToRetract
}

fun DbContext<Mut>.retractEntity(eid: EID) {
  val seed = generateSeed()
  mutate(AtomicComposite(
    instructions = impl.original.entitiesToRetract(eid).map {
      RetractEntityInPartition(it, seed)
    },
    seed = seed
  ))
}

internal fun DbContext<Mut>.retractAttribute(eid: EID, attribute: Attribute<*>) {
  mutate(RetractAttribute(eid, attribute, generateSeed()))
}

fun DbContext<Q>.assertEntityExists(eid: EID, accessedAttribute: Attribute<*>): Nothing? =
  impl.assertEntityExists(eid = eid,
                          accessedAttribute = accessedAttribute,
                          referenceAttribute = null)

internal fun DbContext<Q>.assertReferenceExists(eid: EID, referenceAttribute: Attribute<*>): Nothing? =
  impl.assertEntityExists(eid = eid,
                          accessedAttribute = null,
                          referenceAttribute = referenceAttribute)

internal fun Q.throwEntityDoesNotExist(eid: EID, accessedAttribute: Attribute<*>?, referenceAttribute: Attribute<*>?): Nothing {
  throw EntityDoesNotExistException(
    when {
      accessedAttribute != null -> "Access to attribute ${displayAttribute(accessedAttribute)} of non-existing entity $eid"
      referenceAttribute != null -> "Trying to set reference ${displayAttribute(referenceAttribute)} to non-existing entity $eid"
      else -> "Entity does not exist $eid"
    })
}

data class MissingRequiredAttribute(val eid: EID, val attr: Attribute<*>)

fun DbContext<Q>.message(missingRequiredAttribute: MissingRequiredAttribute): String =
  "attribute ${displayAttribute(missingRequiredAttribute.attr)} is required for entity ${
    displayEntity(missingRequiredAttribute.eid)
  } but was not initialized"

internal fun DbContext<Q>.assertRequiredAttrs(entityEID: EID, entityTypeEID: EID): Nothing? {
  val missing = missingRequiredAttrs(entityEID, entityTypeEID)
  return when {
    missing.isNotEmpty() -> throw TxValidationException(missing.map { m -> message(m) }.joinToString(separator = "\n"))
    else -> null
  }
}

fun DbContext<Q>.missingRequiredAttrs(entityEID: EID, entityTypeEID: EID): List<MissingRequiredAttribute> =
  impl.missingRequiredAttrs(entityEID, entityTypeEID)

internal fun Q.missingRequiredAttrs(entityEID: EID, entityTypeEID: EID): List<MissingRequiredAttribute> =
  (entityTypePossibleAttributes(entityTypeEID))
    .mapNotNull { attr ->
      when {
        attr.schema.required && queryIndex(IndexQuery.GetOne(entityEID, attr)) == null ->
          MissingRequiredAttribute(entityEID, attr)
        else -> null
      }
    }

fun <T> DbContext<Q>.queryIndex(indexQuery: IndexQuery<T>): T =
  impl.queryIndex(indexQuery)

internal fun IndexQuery<*>.patternHash(): Pattern =
  when (this) {
    is IndexQuery.Column<*> -> Pattern.pattern(null, attribute, null)
    is IndexQuery.Entity -> Pattern.pattern(eid, null, null)
    is IndexQuery.GetMany<*> -> Pattern.pattern(eid, attribute, null)
    is IndexQuery.GetOne<*> -> Pattern.pattern(eid, attribute, null)
    is IndexQuery.LookupMany<*> -> Pattern.pattern(null, attribute, value)
    is IndexQuery.LookupUnique<*> -> Pattern.pattern(null, attribute, value)
    is IndexQuery.RefsTo -> Pattern.pattern(eid, null, null)
    is IndexQuery.All -> Pattern.pattern(null, null, null)
    is IndexQuery.Contains<*> ->
      when (attribute.schema.cardinality) {
        Cardinality.Many -> Pattern.pattern(eid, attribute, value)
        else -> Pattern.pattern(eid, attribute, null)
      }
  }

fun DbContext<Q>.q(eid: EID?, attribute: Attribute<*>?, value: Any?): Reducible<Datom> =
  impl.q(eid, attribute, value)

fun Q.q(eid: EID?, attribute: Attribute<*>?, value: Any?): Reducible<Datom> =
  when {
    eid != null && attribute != null && value != null ->
      queryIndex(IndexQuery.Contains(eid, attribute as Attribute<Any>, value))?.let { tx ->
        reducibleOf(Datom(eid, attribute, value, tx))
      } ?: emptyReducible()
    eid != null && attribute == null && value == null -> queryIndex(IndexQuery.Entity(eid)).reducible()
    eid != null && attribute != null -> {
      when (attribute.schema.cardinality) {
        Cardinality.One ->
          queryIndex(IndexQuery.GetOne(eid, attribute))
            ?.let { versioned ->
              reducibleOf(Datom(eid, attribute, versioned.x, versioned.tx))
            } ?: emptyReducible()
        Cardinality.Many ->
          queryIndex(IndexQuery.GetMany(eid, attribute)).reducible()
      }
    }
    eid == null && attribute != null && value != null -> {
      when {
        attribute.schema.unique ->
          queryIndex(IndexQuery.LookupUnique(attribute as Attribute<Any>, value))
            ?.let { versioned ->
              reducibleOf(Datom(versioned.eid, attribute, value, versioned.tx))
            } ?: emptyReducible()
        else ->
          queryIndex(IndexQuery.LookupMany(attribute as Attribute<Any>, value)).reducible()
      }
    }
    eid == null && attribute == null && value != null -> {
      require(value is EID) { "*, *, v queries are supported only for references, got $value" }
      queryIndex(IndexQuery.RefsTo(value)).reducible()
    }
    eid == null && attribute != null && value == null ->
      queryIndex(IndexQuery.Column(attribute)).reducible()
    eid == null && attribute == null && value == null ->
      queryIndex(IndexQuery.All()).asIterable().reducible()
    else ->
      error("query $eid $attribute $value is not supported")
  }

fun DbContext<Mut>.mutate(instruction: Instruction): Novelty = let { pipeline ->
  impl.mutate(pipeline, impl.expand(pipeline, instruction))
}

fun Mut.executingEffects(context: DbContext<Mut>): Mut =
  executingEffects { context.impl }

fun Mut.executingEffects(context: (InstructionEffect) -> Mut): Mut = let { mut ->
  object : Mut by mut {
    override fun mutate(pipeline: DbContext<Mut>, expansion: Expansion): Novelty =
      mut.mutate(pipeline, expansion).mutable().also { mutableNovelty ->
        expansion.effects.forEach { effect ->
          pipeline.alter(
            context(effect).collectingNovelty(mutableNovelty::add)
          ) {
            effect.effect(this)
          }
        }
      }.persistent()
  }
}

fun <T : Any> DbContext<Mut>.add(eid: EID, attribute: Attribute<T>, value: T) {
  mutate(Add(eid, attribute, value, generateSeed()))
}

internal fun <T : Any> DbContext<Mut>.remove(eid: EID, attribute: Attribute<T>, value: T) {
  mutate(Remove(eid, attribute, value, generateSeed()))
}

fun Mut.collectingNovelty(f: (Datom) -> Unit): Mut =
  processingNovelty { novelty, tx ->
    novelty.forEach(f)
  }

fun Mut.withDefaultPart(defaultPart: Part): Mut = let { mut ->
  object : Mut by mut {
    override val defaultPart: Part
      get() = defaultPart
  }
}

fun Mut.collectingInstructions(f: (Instruction) -> Unit): Mut = let { prev ->
  object : Mut by prev {
    override fun expand(pipeline: DbContext<Q>, instruction: Instruction): Expansion {
      f(instruction)
      return prev.expand(pipeline, instruction)
    }
  }
}

fun Mut.processingNovelty(f: DbContext<Q>.(Novelty, TX) -> Unit): Mut = let { m ->
  object : Mut by m {
    override fun mutate(pipeline: DbContext<Mut>, expansion: Expansion): Novelty {
      val novelty = m.mutate(pipeline, expansion)
      pipeline.f(novelty, expansion.tx)
      return novelty
    }
  }
}

fun Mut.delayingEffects(f: (InstructionEffect) -> Unit): Mut = let { m ->
  object : Mut by m {
    override fun expand(pipeline: DbContext<Q>, instruction: Instruction): Expansion = run {
      val expansion = m.expand(pipeline, instruction)
      expansion.effects.forEach(f)
      expansion.copy(effects = emptyList())
    }
  }
}
/**
 * See [Q.cachedQuery]
 */
fun <T> cachedQuery(query: CachedQuery<T>): T = with(DbContext.threadBound) { cachedQuery(query) }

/**
 * See [Q.cachedQuery]
 */
fun <T> DbContext<Q>.cachedQuery(query: CachedQuery<T>): T = with(impl) { cachedQuery(query) }.result

fun DbContext<Q>.requireAttributeByIdent(ident: String): Attribute<Any> =
  requireNotNull(attributeByIdent(ident)) { "attribute $ident is not registered in schema" } as Attribute<Any>

fun createUnknownEntityType(ident: String, name: String? = null, attrs: Iterable<Attribute<*>>, seed: Long): CreateEntity =
  CreateEntity(
    eid = EidGen.memoizedEID(SchemaPart, ident),
    entityTypeEid = EntityType.eid,
    seed = seed,
    attributes = buildList {
      add(EntityType.Ident.attr to ident)
      name?.let { add(EntityType.Name.attr to name) }
      (attrs + Entity.attrs).forEach { attr ->
        add(EntityType.PossibleAttributes.attr to attr.eid)
      }
    }
  )

fun createUnknownAttribute(ident: String, schema: Schema, seed: Long): CreateEntity =
  CreateEntity(
    eid = attr(ident, schema).eid,
    entityTypeEid = EntityAttribute.eid,
    attributes = listOf(EntityAttribute.Ident.attr as Attribute<String> to ident),
    seed = seed
  )
