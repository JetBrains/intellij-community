// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl

import com.intellij.platform.eel.EelConnectionError.*

data object PermissionDenied : SocketAllocationError("Permission denied")

data object NoAvailableDescriptors : SocketAllocationError("No available descriptors")

data object NotEnoughMemory : SocketAllocationError("Not enough memory")

data object UnsupportedAddressFamily : SocketAllocationError("Unsupported address family")

data object UnreachableHost : ResolveFailure("Unreachable host")

data object UnresolvableAddress : ResolveFailure("Address could not be resolved")

data object ConnectionRefused : ConnectionProblem("Connection refused")

data object ConnectionTimeout : ConnectionProblem("Connection timeout")

data object AddressAlreadyInUse : ConnectionProblem("Address is already in use")

data object NetworkDown : ConnectionProblem("Network is down")