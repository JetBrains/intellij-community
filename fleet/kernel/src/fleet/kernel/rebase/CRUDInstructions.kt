// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel.rebase

import fleet.kernel.DurableDbValue
import fleet.util.UID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.modules.SerializersModuleBuilder
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

@Serializable
data class SharedCreateEntity(val entityId: UID,
                              val entityTypeIdent: String,
                              val attributes: List<AttrValue>,
                              override val seed: Long) : SharedInstruction {
  @Serializable
  data class AttrValue(val attr: String,
                       val schema: Int,
                       val value: DurableDbValue)
}

@Serializable
data class SharedRetractEntity(val entityId: UID,
                               override val seed: Long) : SharedInstruction

@Serializable
data class SharedAtomicComposite(val instructions: List<SharedInstruction>,
                                 override val seed: Long): SharedInstruction

@Serializable
data class SharedAdd(val entityId: UID,
                     val attribute: String,
                     val schema: Int,
                     val value: DurableDbValue,
                     override val seed: Long) : SharedInstruction

@Serializable
data class SharedRetractAttribute(val entityId: UID,
                                  val attribute: String,
                                  override val seed: Long) : SharedInstruction

@Serializable
data class SharedRemove(val entityId: UID,
                        val attribute: String,
                        val value: DurableDbValue,
                        override val seed: Long) : SharedInstruction

@Serializable
data class SharedUpdateListElem(val entityId: UID,
                                val attribute: String,
                                val index: Long,
                                val value: JsonElement,
                                override val seed: Long) : SharedInstruction

fun SerializersModuleBuilder.registerCRUDInstructions() {
  polymorphic(SharedInstruction::class) {
    subclass(SharedCreateEntity.serializer())
    subclass(SharedRetractEntity.serializer())
    subclass(SharedAdd.serializer())
    subclass(SharedAtomicComposite.serializer())
    subclass(SharedRetractAttribute.serializer())
    subclass(SharedRemove.serializer())
    subclass(SharedUpdateListElem.serializer())
    subclass(SharedValidate.serializer())
  }
}