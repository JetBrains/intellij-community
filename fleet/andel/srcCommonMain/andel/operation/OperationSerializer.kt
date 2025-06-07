package andel.operation

import fleet.util.serialization.DataSerializer
import kotlinx.serialization.Serializable

/**
 * Old format of the Operation class. Used to preserve backward compatibility.
 */
@Serializable
internal data class OldOperation(val ops: List<Op>)

internal class OperationSerializer: DataSerializer<Operation, OldOperation>(OldOperation.serializer()) {
  override fun fromData(data: OldOperation): Operation {
    return Operation(data.ops)
  }

  override fun toData(value: Operation): OldOperation {
    return OldOperation(value.ops.toList())
  }
}