package com.jetbrains.fleet.rpc.plugin

import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

val RPC_FQN = FqName.fromSegments(listOf("fleet", "rpc"))

val RPC_ANNOTATION_FQN = RPC_FQN.child(Name.identifier("Rpc"))

val REMOTE_API_DESCRIPTOR_FUNCTION_FQN = RPC_FQN.child(Name.identifier("remoteApiDescriptor"))
val RPC_SIGNATURE_CLASS_ID = ClassId.topLevel(RPC_FQN.child(Name.identifier("RpcSignature")))
val PARAMETER_DESCRIPTOR_CLASS_ID = ClassId.topLevel(RPC_FQN.child(Name.identifier("ParameterDescriptor")))

val REMOTE_KIND_FQN = RPC_FQN.child(Name.identifier("RemoteKind"))
val REMOTE_KIND_CLASS_ID = ClassId.topLevel(REMOTE_KIND_FQN)
val REMOTE_KIND_DATA_CLASS_ID = ClassId(RPC_FQN, FqName.fromSegments(listOf("RemoteKind", "Data")), false)
val REMOTE_KIND_REMOTE_OBJECT_CLASS_ID = ClassId(RPC_FQN, FqName.fromSegments(listOf("RemoteKind", "RemoteObject")), false)
val REMOTE_KIND_RESOURCE_CLASS_ID = ClassId(RPC_FQN, FqName.fromSegments(listOf("RemoteKind", "Resource")), false)
val REMOTE_KIND_FLOW_CLASS_ID = ClassId(RPC_FQN, FqName.fromSegments(listOf("RemoteKind", "Flow")), false)
val REMOTE_KIND_RECEIVE_CHANNEL_CLASS_ID = ClassId(RPC_FQN, FqName.fromSegments(listOf("RemoteKind", "ReceiveChannel")), false)
val REMOTE_KIND_SEND_CHANNEL_CLASS_ID = ClassId(RPC_FQN, FqName.fromSegments(listOf("RemoteKind", "SendChannel")), false)
val REMOTE_KIND_DEFERRED_CLASS_ID = ClassId(RPC_FQN, FqName.fromSegments(listOf("RemoteKind", "Deferred")), false)

val REMOTE_API_DESCRIPTOR_INTERFACE_FQN = RPC_FQN.child(Name.identifier("RemoteApiDescriptor"))
val REMOTE_API_DESCRIPTOR_INTERFACE_CLASS_ID = ClassId.topLevel(REMOTE_API_DESCRIPTOR_INTERFACE_FQN)

val THROWING_SERIALIZER_CLASS_ID = ClassId.topLevel(RPC_FQN.child(Name.identifier("core")).child(Name.identifier("ThrowingSerializer")))

val RPC_CORE_FQN: FqName = RPC_FQN.child(Name.identifier("core"))

val REMOTE_RESOURCE_FQN: FqName = RPC_CORE_FQN.child(Name.identifier("RemoteResource"))
val REMOTE_OBJECT_FQN = RPC_CORE_FQN.child(Name.identifier("RemoteObject"))

val remoteApiDescriptorImplClassName = Name.identifier("_generated_RemoteApiDescriptor")