package com.jetbrains.lsp.protocol

import kotlinx.serialization.builtins.serializer

val Shutdown: RequestType<Nothing?, Nothing?, Unit> = RequestType(
    "shutdown",
    NoValueSerializer,
    NoValueSerializer,
    Unit.serializer(),
)
