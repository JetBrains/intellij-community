// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.rpc.client

import fleet.rpc.core.InstanceId
import fleet.rpc.core.RpcMessage
import fleet.util.UID
import kotlinx.coroutines.CopyableThrowable

/**
 * Base class for all exceptions that will be retried by [durable]
 * */
abstract class RpcClientException(message: String?, cause: Throwable?) : RuntimeException(message, cause)

/**
 * Thrown when the remote service designated by [route] is offline (maybe temporarily).
 *
 * see [RpcClientException]
 * */
class RouteClosedException(val route: UID, message: String, cause: Throwable? = null)
  : RpcClientException(message, cause),
    CopyableThrowable<RouteClosedException> {

  override fun createCopy(): RouteClosedException {
    return RouteClosedException(route, checkNotNull(message), this)
  }
}

/**
 * Thrown when the request has timed out.
 *
 * see [RpcClientException]
 * */
class RpcTimeoutException(val msg: String, cause: Throwable? = null)
  : RpcClientException(msg, cause),
    CopyableThrowable<RpcTimeoutException> {
  override fun createCopy(): RpcTimeoutException {
    return RpcTimeoutException(msg, this)
  }
}

class RpcServiceNotReady(private val req: RpcMessage.CallRequest, cause: Throwable? = null)
  : RpcClientException("Service not ready: ${req.service}$${req.method}", cause),
    CopyableThrowable<RpcServiceNotReady> {
  override fun createCopy(): RpcServiceNotReady {
    return RpcServiceNotReady(req, this)
  }
}

class UnresolvedServiceException(val serviceId: InstanceId, cause: Throwable? = null)
  : RpcClientException("Service not found: ${serviceId}", cause),
    CopyableThrowable<UnresolvedServiceException> {
  override fun createCopy(): UnresolvedServiceException {
    return UnresolvedServiceException(this.serviceId, this)
  }
}

/**
 * Thrown when the client is disconnected from the server.
 *
 * see [RpcClientException]
 * */
class RpcClientDisconnectedException(reason: String?, cause: Throwable?)
  : RpcClientException(reason, cause),
    CopyableThrowable<RpcClientDisconnectedException> {
  override fun createCopy(): RpcClientDisconnectedException {
    return RpcClientDisconnectedException(message, this)
  }
}

class RpcCausalityTimeout(msg: String?, cause: Throwable?)
  : RpcClientException(msg, cause),
    CopyableThrowable<RpcCausalityTimeout> {
  override fun createCopy(): RpcCausalityTimeout {
    return RpcCausalityTimeout(message, this)
  }
}

/**
 * Thrown when the remote producer fails with a CancellationException.
 *
 * see [RpcClientException]
 * */
class ProducerIsCancelledException(msg: String?, cause: Throwable?)
  : RpcClientException(msg, cause),
    CopyableThrowable<ProducerIsCancelledException> {
  override fun createCopy(): ProducerIsCancelledException {
    return ProducerIsCancelledException(message, this)
  }
}