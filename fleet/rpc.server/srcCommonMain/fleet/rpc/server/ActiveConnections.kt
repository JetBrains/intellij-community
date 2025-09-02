package fleet.rpc.server

import fleet.util.UID
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update

class ActiveConnections : ConnectionListener {
  data class EndpointKey(
    val socketId: UID,
    val kind: EndpointKind,
  )

  data class Endpoint(
    val route: UID,
    val presentableName: String?,
  )

  private val socket2endpoint = MutableStateFlow<PersistentMap<EndpointKey, Endpoint>>(persistentHashMapOf())

  val state: StateFlow<PersistentMap<EndpointKey, Endpoint>> = socket2endpoint.asStateFlow()

  val updates: Flow<Update> = flow {
    var old = persistentHashMapOf<EndpointKey, Endpoint>()
    state.collect { new ->
      // EndpointKey is unique, endpoint cannot change its value
      for (deleted in old.keys - new.keys) {
        val endpoint = old[deleted]!!
        emit(
          Update.Disconnected(
            kind = deleted.kind,
            route = endpoint.route,
            socketId = deleted.socketId,
          )
        )
      }
      for (added in new.keys - old.keys) {
        val endpoint = new[added]!!
        emit(
          Update.Connected(
            kind = added.kind,
            route = endpoint.route,
            socketId = added.socketId,
            presentableName = endpoint.presentableName,
          )
        )
      }
      old = new
    }
  }

  sealed interface Update {
    data class Connected(
      val kind: EndpointKind,
      val route: UID,
      val socketId: UID,
      val presentableName: String?,
    ) : Update

    data class Disconnected(
      val kind: EndpointKind,
      val route: UID,
      val socketId: UID,
    ) : Update
  }

  override fun onConnect(kind: EndpointKind, route: UID, socketId: UID, presentableName: String?) {
    val key = EndpointKey(
      socketId = socketId,
      kind = kind,
    )
    val endpoint = Endpoint(
      route = route,
      presentableName = presentableName,
    )
    socket2endpoint.update {
      check(!it.containsKey(key)) { "key $key is not unique" }
      it.put(key, endpoint)
    }
  }

  override fun onDisconnect(kind: EndpointKind, route: UID, socketId: UID) {
    val key = EndpointKey(
      socketId = socketId,
      kind = kind,
    )
    socket2endpoint.update { it.remove(key) }
  }
}
