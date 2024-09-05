// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel

import com.jetbrains.rhizomedb.*
import fleet.util.UID
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

/**
 * [StorageKey] specifies the storage that will keep the entity,
 * whenever it's creation is observed by the [withStorage] worker
 * All the transitive closure of direct references must have the same [storageKey] attributes
 * @see fleet.frontend.FrontendStorageKey
 * @see fleet.common.WorkspaceStorageKey
 */
@Serializable
data class StorageKey(val storage: String)

/**
 * Durable entities may be serialized, be put on a disc, or a wire and possibly overlive the class delcaring them.
 * By declaring an entity durable, one guarantees that all values they hold are serializable.
 * The entire transitive closure of direct references of a [DurableEntity] must also be durable.
 * The clients of such entities can assign the [Durable.StorageKeyAttr] attribute to specify the durability policy for them.
 * Consider adding [Version] annotation to it to control the compatibility.
 * When loading the code, entities with an older Versions are retracted from the database.
 * On the other hand, the matching versions will overlive the code successfully, unless the schema is actually changed or serialization error raised,
 * such an event would also lead to the entity retraction together will all other entitis refering them by required attributes.
 */
interface DurableEntity : LegacyEntity {
  @Unique
  var uid: UID
}

/**
 * Base class for all [EntityType]s of stored or shared entity types.
 * [DurableEntityType]s has to be registered in [fleet.kernel.plugins.Plugin.load] function using [ChangeScope.register].
 * Otherwise one will see serialized values in the database or won't see them at all, or will get non-initialized entity errors.
 * It mixes in [Durable] to the list of mixins.
 *
 * If deriving from this class is somehow a limiting factor for your [EntityType], feel free to mix-in [DurableEntityType] by hands.
 * */
abstract class DurableEntityType<E : Entity>(
  ident: String,
  module: String,
  cons: (EID) -> E,
  vararg mixins: Mixin<in E>,
) : EntityType<E>(ident, module, cons, Durable, *mixins) {


  constructor(ident: KClass<*>, version: Int?, cons: (EID) -> E, vararg mixins: Mixin<in E>) : this(
    ident = ident.qualifiedName!! + if (version != null) ":$version" else "",
    module = ident.java.module.name ?: "<unnamed>",
    cons = cons,
    mixins = mixins,
  )

  constructor(ident: KClass<*>, cons: (EID) -> E, vararg mixins: Mixin<in E>) : this(
    ident = ident,
    version = null,
    cons = cons,
    mixins = mixins,
  )
}

/**
 *
 * Attributes in this [Mixin] magically coincide with attribute, created from properties of [DurableEntity] interface,
 * because it's ident is "fleet.kernel.DurableEntity" and names of the attributes coincide with the names of the properies.
 * This hack has to remain in place until we migrate all entities to the new API.
 * */
object Durable : Mixin<Entity>(DurableEntity::class) {
  val Id = requiredValue("uid", UID.serializer(), Indexing.UNIQUE) { UID.random() }
  val StorageKeyAttr = optionalValue("storageKey", StorageKey.serializer(), Indexing.INDEXED)
}

fun KClass<*>.isDurable(): Boolean =
  DurableEntity::class.java.isAssignableFrom(this.java)

fun <T : Entity> byUid(uid: UID): T =
  requireNotNull(byUidOrNull(uid)) {
    "cannot find an entity for uid $uid"
  }

fun <T : Entity> byUidOrNull(uid: UID): T? =
  entity(Durable.Id, uid) as T?

@Serializable
data class DurableRef<T : Entity>(val uid: UID) {
  fun derefOrNull(): T? = byUidOrNull(uid)
  fun deref(): T = byUid(uid)
}

fun <T : Entity> T.ref(): DurableRef<T> = DurableRef(this[Durable.Id])

fun uidAttribute(): Attribute<UID> =
  Durable.Id.attr as Attribute<UID>

@Deprecated("internal, replace with DurableRef when possible")
fun Entity.deprecatedUid() = this[Durable.Id]