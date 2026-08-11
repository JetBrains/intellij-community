package fleet.rpc.core

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class ProtocolVersion(val version: String) {
  companion object {
    val unspecified = ProtocolVersion("0")
    val current = ProtocolVersion("1")
  }
}
