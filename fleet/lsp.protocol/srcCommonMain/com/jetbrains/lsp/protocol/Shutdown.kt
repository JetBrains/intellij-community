package com.jetbrains.lsp.protocol

import kotlinx.serialization.builtins.NothingSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer

val Shutdown: RequestType<Nothing?, Nothing?, Unit> = RequestType(
    "shutdown",
    NothingSerializer().nullable,
    NothingSerializer().nullable,
    Unit.serializer(),
)
