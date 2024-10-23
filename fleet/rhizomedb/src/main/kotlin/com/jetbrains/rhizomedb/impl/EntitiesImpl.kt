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
  val initialized = (entity as? BaseEntity)?.initialized != false
  when (attr.schema.cardinality) {
    Cardinality.One -> {
      val res = when {
        attr.schema.isRef -> (getOne(eid, attr, initialized) as EID?)?.let { refEID -> entity(refEID) }
        else -> getOne(eid, attr, initialized)
      }
      if (res == null && attr.schema.required) {
        throw EntityAttributeIsNotInitialized(displayEntity(eid), displayAttribute(attr))
      }
      else {
        res
      }
    }
    Cardinality.Many -> {
      if (initialized) {
        assertEntityExists(eid, attr)
      }
      DBSet<Any>(eid, attr as Attribute<Any>)
    }
  }
}

@Suppress("unused")
internal fun RTset(entity: Entity, attrL: Long, v: Any?) {
  val attr = Attribute<Any>(attrL.toInt())
  val eid = entity.eid
  DbContext.threadBound.ensureMutable {
    if ((entity as BaseEntity).initialized) {
      assertEntityExists(eid, attr)
    }
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
                          var initialized: Boolean) : Entity {

  override fun toString(): String {
    return "${entityClass.simpleName}#${eid}"
  }

  override fun equals(other: Any?): Boolean {
    return other is Entity && other.eid == eid
  }

  override fun hashCode(): Int {
    return eid.hashCode() + 1
  }
}

internal class AnEntity(eid: EID, entityClass: KClass<out Entity>, initialized: Boolean) : BaseEntity(eid, entityClass, initialized) {
  companion object : EntityFactory {
    override fun create(eid: EID, initialized: Boolean): Entity {
      return AnEntity(eid, Entity::class, initialized)
    }
  }
}

internal class ALegacyEntity(eid: EID, entityClass: KClass<out LegacyEntity>, initialized: Boolean) : BaseEntity(eid, entityClass, initialized), LegacyEntity {
  companion object : EntityFactory {
    override fun create(eid: EID, initialized: Boolean): Entity {
      return ALegacyEntity(eid, LegacyEntity::class, initialized)
    }
  }
}

fun DbContext<Q>.entityTypeByClass(c: KClass<out Entity>): EID? =
  lookupOne(LegacySchema.EntityType.kClass, c)

fun entityTypeNameForClass(c: KClass<*>): String = entityTypeNameForClass(c.java)

fun entityTypeNameForClass(c: Class<*>): String =
  requireNotNull((c.getAnnotation(Ident::class.java)?.ident) ?: c.name)

fun DbContext<Mut>.addEntityClass(entityTypeDefinition: EntityTypeDefinition): EID {
  val ident = entityTypeDefinition.ident
  val c = entityTypeDefinition.kClass
  val registrar = entityTypeDefinition.schemaRegistrar
  val module = entityTypeDefinition.module
  val entityTypeEID = run {
    val createEntityType = createUnknownEntityType(
      ident = ident,
      name = entityTypeNameForClass(c.java),
      attrs = emptyList(),
      seed = generateSeed()
    )
    mutate(createEntityType)
    createEntityType.eid
  }
  val existingClass = getOne(entityTypeEID, LegacySchema.EntityType.kClass)
  if (existingClass == null) {
    add(entityTypeEID, LegacySchema.EntityType.kClass, c)
    add(entityTypeEID, Entity.Module.attr as Attribute<String>, module)
    add(entityTypeEID, LegacySchema.EntityType.schemaRegistrar, registrar)
  }
  else {
    require(existingClass == c) {
      val existingModule = getOne(entityTypeEID, Entity.Module.attr)
      "entity type $ident already bound to entity class $existingClass(from $existingModule) != $c (from $module)"
    }
  }
  return entityTypeEID
}

fun DbContext<Q>.entityTypeDefinedAttributes(entityTypeEID: EID): List<Attribute<*>> =
  impl.entityTypeDefinedAttributes(entityTypeEID)

internal fun Q.entityTypeDefinedAttributes(entityTypeEID: EID): List<Attribute<*>> =
  queryIndex(IndexQuery.GetMany(entityTypeEID, LegacySchema.EntityType.definedAttributes)).map { (_, _, v) ->
    Attribute<Any>(v as EID)
  }

internal fun DbContext<Q>.entityTypePossibleAttributes(entityTypeEID: EID): List<Attribute<*>> =
  impl.entityTypePossibleAttributes(entityTypeEID)

internal fun Q.entityTypePossibleAttributes(entityTypeEID: EID): List<Attribute<*>> =
  queryIndex(IndexQuery.GetMany(entityTypeEID, EntityType.PossibleAttributes.attr as Attribute<EID>)).map { (_, _, v) ->
    Attribute<Any>(v as EID)
  }

fun DbContext<Mut>.removeEntityClass(c: KClass<out Entity>) {
  entityTypeByClass(c)?.let { entityTypeEID ->
    mutate(RetractAttribute(entityTypeEID, Entity.EntityObject.attr, generateSeed()))
    mutate(Remove(entityTypeEID, LegacySchema.EntityType.kClass, c, generateSeed()))
    mutate(RetractAttribute(entityTypeEID, LegacySchema.EntityType.ancestorKClasses, generateSeed()))
    mutate(RetractAttribute(entityTypeEID, LegacySchema.EntityType.schemaRegistrar, generateSeed()))
    entityTypeDefinedAttributes(entityTypeEID).forEach { attribute ->
      mutate(RetractAttribute(attribute.eid, LegacySchema.Attr.kProperty, generateSeed()))
    }
  }
}

private object Logger {
  val logger = logger<Logger>()
}

fun DbContext<Mut>.initAttributes(entityTypeEID: EID): EntityType<*>? =
  getOne(entityTypeEID, Entity.EntityObject.attr as Attribute<EntityType<*>>)
  ?: getOne(entityTypeEID, EntityType.Ident.attr as Attribute<String>)?.let { entityTypeIdent ->
    getOne(entityTypeEID, LegacySchema.EntityType.kClass)?.let { entityKClass ->
      getOne(entityTypeEID, LegacySchema.EntityType.schemaRegistrar)?.let { schemaRegistrar ->
        val module = getOne(entityTypeEID, Entity.Module.attr as Attribute<String>)!!
        var entityFactory: EntityFactory? = null
        val entityType = object : EntityType<Entity>(entityTypeIdent, module, { eid ->
          entityFactory!!.create(eid, true)
        }) {}
        add(entityTypeEID, LegacySchema.EntityType.ancestorKClasses, entityKClass)
        val schemaBuilder = object : SchemaBuilder {
          override fun superclass(c: KClass<*>) {
            if (c != entityKClass) {
              entityTypeByClass(c as KClass<out Entity>)?.let { superClassEntityTypeEID ->
                initAttributes(superClassEntityTypeEID)
                add(entityTypeEID, LegacySchema.EntityType.ancestorKClasses, c)
                getMany(superClassEntityTypeEID, EntityType.PossibleAttributes.attr as Attribute<EID>).forEach { attrEID ->
                  add(entityTypeEID, EntityType.PossibleAttributes.attr as Attribute<EID>, attrEID)
                }
              }
            }
          }

          override fun findAttribute(property: KProperty<*>): EID? {
            return attributeForProperty(property)?.eid
          }

          override fun addPropertyToAttribute(attribute: EID, property: KProperty<*>) {
            add(attribute, LegacySchema.Attr.kProperty, property)
          }

          override fun declareAttribute(property: KProperty<*>, defaultIdent: String, schema: Schema) {
            val ident = "$entityTypeIdent/${property.name}"
            val attribute = attributeByIdent(ident) ?: run {
              val createAttribute = createUnknownAttribute(ident, schema, generateSeed())
              mutate(createAttribute)
              Attribute<Any>(createAttribute.eid)
            }
            add(entityTypeEID, LegacySchema.EntityType.definedAttributes, attribute.eid)
            add(entityTypeEID, EntityType.PossibleAttributes.attr as Attribute<EID>, attribute.eid)
            add(attribute.eid, LegacySchema.Attr.kProperty, property)
            add(attribute.eid, Entity.Module.attr, module)

            val serializer = impl.meta[SerializationKey]?.let { serialization ->
              val serializer = lazy {
                val propType = property.returnType.let { returnType ->
                  when (schema.cardinality) {
                    Cardinality.One -> returnType.withNullability(false)
                    Cardinality.Many -> requireNotNull(returnType.arguments.single().type)
                  }
                }
                serialization.kSerializer(propType) as KSerializer<Any>
              }

              mutate(MapAttribute(attribute) {
                when {
                  it is JsonElement -> serialization.json.decodeFromJsonElement(serializer.value, it)
                  else -> it
                }
              })
              serializer
            }

            val entityAttribute = when (attribute.schema.cardinality) {
              Cardinality.One ->
                when (attribute.schema.required) {
                  true -> entityType.Required(ident, attribute, serializer, null)
                  false -> entityType.Optional(ident, attribute, serializer, null)
                }
              Cardinality.Many ->
                entityType.Many<Any>(ident, attribute, serializer, null)
            }

            add(attribute.eid, Entity.EntityObject.attr as Attribute<EntityAttribute<*, *>>, entityAttribute)
          }
        }
        entityFactory = with(schemaRegistrar) { schemaBuilder.registerSchema() }
        add(entityTypeEID, Entity.EntityObject.attr, entityType)
        mutate(ReifyEntities(entityTypeEID, generateSeed()))
        entityType
      }
    }
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

fun collectEntityClasses(module: Module): List<EntityTypeDefinition> =
  module.getResourceAsStream(ENTITIES_LIST_PATH)?.metaInfLineSequence()?.mapNotNull { entityClassName ->
    loadEntityClass(module.classLoader, entityClassName, module.name)
  }?.toList() ?: emptyList()

fun collectEntityClasses(classLoader: ClassLoader, module: String): List<EntityTypeDefinition> =
  classLoader.getResourceAsStream(ENTITIES_LIST_PATH)?.metaInfLineSequence()?.mapNotNull { entityClassName ->
    loadEntityClass(classLoader, entityClassName, module)
  }?.toList() ?: emptyList()

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

fun collectEntityClasses(list: List<KClass<out Entity>>, module: String): List<EntityTypeDefinition> =
  list.map { c ->
    loadEntityClass(c.java.classLoader, c.qualifiedName!!, module)!!
  }

fun loadEntityClass(classLoader: ClassLoader, entityClassName: String, module: String): EntityTypeDefinition? =
  try {
    val entityClass = classLoader.loadClass(entityClassName)
    runCatching { classLoader.loadClass("${entityClassName}GeneratedRegistrar") }
      .getOrNull()?.let { registrarClass ->
        val registrar = registrarClass.getDeclaredField("INSTANCE").get(null) as SchemaRegistrar
        val version = versionForDurableEntityClass(entityClass)
        val entityTypeName = entityTypeNameForClass(entityClass)
        val ident = if (version != null) "$entityTypeName:$version" else entityTypeName
        EntityTypeDefinition(kClass = entityClass.kotlin as KClass<out Entity>,
                             schemaRegistrar = registrar,
                             ident = ident,
                             module = module)
      }
  }
  catch (x: Exception) {
    Logger.logger.error(x, "couldn't load entity class $entityClassName")
    null
  }

fun <T : LegacyEntity> Q.lookupImpl(prop: KProperty1<in T, *>, r: Any, klass: KClass<T>): Set<T> =
  attributeForProperty(prop)?.let { attribute ->
    attribute as Attribute<Any>
    val value = when {
      attribute.schema.isRef -> (r as Entity).eid
      else -> r
    }
    when {
      attribute.schema.unique ->
        queryIndex(IndexQuery.LookupUnique(attribute, value))?.eid?.let { eid ->
          val entity = entity(eid)
          if (klass.java.isInstance(entity)) setOf(entity as T) else null
        }

      else ->
        queryIndex(IndexQuery.LookupMany(attribute, value)).mapNotNullTo(HashSet()) { (e, _, _) ->
          val entity = entity(e)
          if (klass.java.isInstance(entity)) entity as T else null
        }
    }
  } ?: emptySet()

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