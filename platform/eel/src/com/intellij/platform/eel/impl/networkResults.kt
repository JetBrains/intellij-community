// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl

import com.intellij.platform.eel.EelConnectionError
import com.intellij.platform.eel.EelConnectionError.*
import com.intellij.platform.eel.EelNetworkError
import com.intellij.platform.eel.EelResult

data class NetworkOk<out T, E : EelNetworkError>(override val value: T) : EelResult.Ok<T, E>
data class NetworkError<out T, E : EelNetworkError>(override val error: E) : EelResult.Error<T, E>


data object PermissionDenied : SocketAllocationError {
  override val message: String = "Permission denied"
}

data object NoAvailableDescriptors : SocketAllocationError {
  override val message: String = "No available descriptors"
}

data object NotEnoughMemory : SocketAllocationError {
  override val message: String = "Not enough memory"
}

data object UnsupportedAddressFamily : SocketAllocationError {
  override val message: String = "Unsupported address family"
}

data object UnreachableHost : ResolveFailure {
  override val message: String = "Unreachable host"
}

data object UnresolvableAddress : ResolveFailure {
  override val message: String = "Address could not be resolved"
}

data object ConnectionRefused : ConnectionProblem {
  override val message: String = "Connection refused"
}

data object ConnectionTimeout : ConnectionProblem {
  override val message: String = "Connection timeout"
}

data object AddressAlreadyInUse : ConnectionProblem {
  override val message: String = "Address is already in use"
}

data object NetworkDown : ConnectionProblem {
  override val message: String = "Network is down"
}

data class UnknownFailure(override val message: String) : EelConnectionError.UnknownFailure