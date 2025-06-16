package com.jetbrains.lsp.protocol

import kotlinx.serialization.builtins.serializer

val Shutdown: RequestType<Unit, Unit, Unit> = RequestType(
    "shutdown",
    Unit.serializer(),
    Unit.serializer(),
    Unit.serializer()
)
