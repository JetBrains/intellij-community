package com.jetbrains.lsp.implementation

import com.jetbrains.lsp.protocol.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking

fun main() {
    val handler = lspHandlers {
        request(Initialize) { initParams ->
            InitializeResult(
                capabilities = ServerCapabilities(
                    textDocumentSync = TextDocumentSyncKind.Incremental,
                ),
                serverInfo = InitializeResult.ServerInfo(
                    name = "IntelliJ Analyzer",
                    version = "1.0"
                ),
            )
        }
        notification(DocumentSync.DidOpen) { didOpen ->
            println("didOpen: $didOpen")
        }
        notification(DocumentSync.DidChange) { didChange ->
            println("didChange: $didChange")
        }
    }
    runBlocking(Dispatchers.Default) {
        tcpServer(TcpConnectionConfig.Server("127.0.0.1", 9999, isMultiClient = true)) { connection ->
            withBaseProtocolFraming(connection, exitSignal = null) { incoming, outgoing ->
                withLsp(incoming, outgoing, handler) { lsp ->
                    awaitCancellation()
                }
            }
        }
    }
}