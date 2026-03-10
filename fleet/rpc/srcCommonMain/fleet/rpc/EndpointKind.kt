package fleet.rpc

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
enum class EndpointKind {
  Client,
  Provider
}
