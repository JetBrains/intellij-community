// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UNCHECKED_CAST")

package com.jetbrains.rhizomedb.impl

import com.jetbrains.rhizomedb.*
import fleet.util.logging.logger
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import org.jetbrains.annotations.ApiStatus
import java.io.InputStream
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.full.withNullability

private const val ENTITIES_LIST_PATH = "META-INF/listOfEntities.txt"
private const val ENTITY_TYPES_PROVIDERS_LIST_PATH = "META-INF/com.jetbrains.rhizomedb.impl.EntityTypeProvider.txt"

interface SchemaBuilder {
  fun superclass(c: KClass<*>)
  fun findAttribute(property: KProperty<*>): EID?
  fun addPropertyToAttribute(attribute: EID, property: KProperty<*>)
  fun declareAttribute(property: KProperty<*>, defaultIdent: String, schema: Schema)
}

fun interface SchemaRegistrar {
  fun SchemaBuilder.registerSchema(): EntityFactory
}

fun interface EntityFactory {
  fun create(eid: EID, initialized: Boolean): Entity
}

interface EntityTypeProvider {
  val entityTypes: List<EntityType<*>>
}

@Suppress("unused")
internal fun RTsuperclass(schemaBuilder: SchemaBuilder, c: KClass<*>) =
  schemaBuilder.superclass(c)

@Suppress("unused")
internal fun RTfindAttribute(schemaBuilder: SchemaBuilder, property: KProperty<*>): EID? =
  schemaBuilder.findAttribute(property)

@Suppress("unused")
internal fun RTaddPropertyToAttribute(schemaBuilder: SchemaBuilder, attribute: EID, property: KProperty<*>) =
  schemaBuilder.addPropertyToAttribute(attribute, property)

@Suppress("unused")
internal fun RTdeclareAttribute(schemaBuilder: SchemaBuilder, property: KProperty<*>, ident: String, schema: Schema) =
  schemaBuilder.declareAttribute(property, ident, schema)

@Suppress("unused")
internal fun RTSchemaConstructor(isMany: Boolean,
                                 isRef: Boolean,
                                 indexed: Boolean,
                                 unique: Boolean,
                                 cascadeDelete: Boolean,
                                 cascadeDeleteBy: Boolean,
                                 required: Boolean,
                                 computed: Boolean): Schema {
  val cardinality = if (isMany) Cardinality.Many else Cardinality.One
  return Schema(cardinality, isRef, indexed, unique, cascadeDelete, cascadeDeleteBy, required)
}

@Suppress("unused")
internal fun RTget(entity: Entity, attrL: Long): Any? =
  DbContext.threadBound.getAttributeValue(entity, Attribute<Any>(attrL.toInt()))

fun DbContext<Q>.getAttributeValue(entity: Entity, attr: Attribute<*>): Any? = run {
  val eid = entity.eid
  when (attr.schema.cardinality) {
    Cardinality.One -> {
      val res = when {
        attr.schema.isRef -> (getOne(eid, attr, true) as EID?)?.let { refEID -> entity(refEID) }
        else -> getOne(eid, attr, true)
      }
      if (res == null && attr.schema.required) {
        throw EntityAttributeIsNotInitialized(displayEntity(eid), displayAttribute(attr))
      }
      else {
        res
      }
    }
    Cardinality.Many -> {
      assertEntityExists(eid, attr)
      DBSet<Any>(eid, attr as Attribute<Any>)
    }
  }
}

@Suppress("unused")
internal fun RTset(entity: Entity, attrL: Long, v: Any?) {
  val attr = Attribute<Any>(attrL.toInt())
  val eid = entity.eid
  DbContext.threadBound.ensureMutable {
    assertEntityExists(eid, attr)
    if (v == null) {
      retractAttribute(eid, attr)
    }
    else {
      when (attr.schema.cardinality) {
        Cardinality.One -> {
          if (attr.schema.isRef) {
            val referenceEID = (v as Entity).eid
            assertReferenceExists(referenceEID, attr)
            add(eid, attr, referenceEID)
          }
          else {
            add(eid, attr, v)
          }
        }
        Cardinality.Many -> {
          retractAttribute(eid, attr)
          if (attr.schema.isRef) {
            for (x in v as Set<Entity>) {
              assertReferenceExists(x.eid, attr)
              add(eid, attr as Attribute<EID>, x.eid)
            }
          }
          else {
            for (x in v as Set<Any>) {
              add(eid, attr, x)
            }
          }
        }
      }
    }
  }
}
abstract class BaseEntity(override val eid: EID,
                          override val entityClass: KClass<out Entity>,
                          var initialized: Boolean) : Entity

internal fun DbContext<Q>.entityTypePossibleAttributes(entityTypeEID: EID): List<Attribute<*>> =
  impl.entityTypePossibleAttributes(entityTypeEID)

internal fun Q.entityTypePossibleAttributes(entityTypeEID: EID): List<Attribute<*>> =
  queryIndex(IndexQuery.GetMany(entityTypeEID, EntityType.PossibleAttributes.attr as Attribute<EID>)).map { (_, _, v) ->
    Attribute<Any>(v as EID)
  }

private object Logger {
  val logger = logger<Logger>()
}

fun DbContext<Q>.attributeSerializer(attr: Attribute<*>): KSerializer<Any>? =
  (entity(attr.eid) as EntityAttribute<*, *>?)?.serializerLazy?.value as KSerializer<Any>?

fun DbContext<Q>.byEntityType(entityTypeEID: EID): List<EID> =
  queryIndex(IndexQuery.LookupMany(Entity.Type.attr as Attribute<EID>, entityTypeEID)).map { it.eid }

internal fun versionForDurableEntityClass(c: Class<*>): String? =
  c.getAnnotation(Version::class.java)?.version

data class EntityTypeDefinition(val kClass: KClass<out Entity>,
                                val schemaRegistrar: SchemaRegistrar,
                                val ident: String,
                                val module: String)

fun collectEntityTypeProviders(module: Module): List<EntityTypeProvider> = listOf(
  // TODO: replace with service providers. Hard to do before K2. After K2 have problems with IC (KT-66735), but seems they not interfere
  MetaInfBasedEntityTypeProvider(module.classLoader, module::getResourceAsStream)
)

@Suppress("unused") // API for IJ
fun collectEntityTypeProviders(classLoader: ClassLoader): List<EntityTypeProvider> = listOf(
  MetaInfBasedEntityTypeProvider(classLoader, classLoader::getResourceAsStream)
)

private fun InputStream.metaInfLineSequence() : Sequence<String> =
  bufferedReader()
    .lineSequence()
    .filter(String::isNotBlank)

fun DbContext<Q>.entity(eid: EID): Entity? = impl.entity(eid)

fun Q.entity(eid: EID): Entity? =
  getOne(eid, Entity.EntityObject.attr as Attribute<Entity>)

private class MetaInfBasedEntityTypeProvider(
  val classLoader: ClassLoader,
  val resourceLoader: (String) -> InputStream?,
) : EntityTypeProvider {
  override val entityTypes: List<EntityType<*>> by lazy {
    resourceLoader(ENTITY_TYPES_PROVIDERS_LIST_PATH)?.metaInfLineSequence()?.flatMap { providerClassName ->
      try {
        val providerClass = classLoader.loadClass(providerClassName)
        val provider = providerClass.getField(INSTANCE).get(null) as EntityTypeProvider
        provider.entityTypes
      }
      catch (e : Exception) {
        logger.error(e) { "Couldn't load entity types from $providerClassName" }
        emptyList()
      }
    }?.toList() ?: emptyList()
  }

  private val logger = logger<MetaInfBasedEntityTypeProvider>()
}

/**
 * Register entity type provider for loading.
 * This should not be called manually and is intended for use from compiler plugins in non-JVM contexts.
 * No-op on JVM.
 *
 * @see com.jetbrains.rhizomedb.plugin.EntityTypeRegistrationGenerator
 */
@ApiStatus.Internal
fun registerEntityTypeProvider(provider: EntityTypeProvider): Boolean {
  // Later: this should push entity type provider somewhere on non-JVM platforms
  return true
}

private const val INSTANCE = "INSTANCE"