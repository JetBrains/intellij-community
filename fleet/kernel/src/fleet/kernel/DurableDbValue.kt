// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel

import fleet.util.UID
import fleet.util.serialization.DataSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
sealed class DurableDbValue {
  private class ScalarSerializer : DataSerializer<Scalar, ScalarSerializer.ScalarData>(dataSerializer = ScalarData.serializer()) {
    @SerialName("scalar")
    @Serializable
    data class ScalarData(val json: JsonElement)

    override fun fromData(data: ScalarData): Scalar {
      return Scalar(lazy { data.json })
    }

    override fun toData(value: Scalar): ScalarData {
      return ScalarData(value.json)
    }
  }

  @Serializable
  @SerialName("ref")
  data class EntityRef(val entityId: UID) : DurableDbValue()

  //TODO: data class AttributeRef(val attribute: String): Value()
  @Serializable
  @SerialName("type")
  data class EntityTypeRef(val ident: String) : DurableDbValue()

  @Serializable(with = ScalarSerializer::class)
  @SerialName("scalar")
  data class Scalar(val lazyJson: Lazy<JsonElement>) : DurableDbValue() {
    val json: JsonElement get() = lazyJson.value
    override fun equals(other: Any?): Boolean = other is Scalar && other.json == this.json
    override fun hashCode(): Int = this.json.hashCode()
  }
}