// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel.rebase

import fleet.kernel.DurableDbValue
import fleet.util.UID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class SharedCreateEntity(
  val entityId: UID,
  val entityTypeIdent: String,
  val attributes: List<AttrValue>,
  val seed: Long,
) {
  @Serializable
  data class AttrValue(
    val attr: String,
    val schema: Int,
    val value: DurableDbValue,
  )
}

@Serializable
data class SharedRetractEntity(
  val entityId: UID,
  val seed: Long, 
)

@Serializable
data class SharedAtomicComposite(
  val instructions: List<SharedInstruction>,
  val seed: Long, 
)

@Serializable
data class SharedAdd(
  val entityId: UID,
  val attribute: String,
  val schema: Int,
  val value: DurableDbValue,
  val seed: Long, 
)

@Serializable
data class SharedRetractAttribute(
  val entityId: UID,
  val attribute: String,
  val seed: Long, 
)

@Serializable
data class SharedRemove(
  val entityId: UID,
  val attribute: String,
  val value: DurableDbValue,
  val seed: Long, 
)

@Serializable
data class SharedUpdateListElem(
  val entityId: UID,
  val attribute: String,
  val index: Long,
  val value: JsonElement,
  val seed: Long, 
)
