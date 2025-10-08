package com.jetbrains.lsp.implementation

import com.jetbrains.lsp.protocol.NotificationType
import com.jetbrains.lsp.protocol.RequestType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement

fun main() {
  runBlocking(Dispatchers.Default) {
    val clientToServer = Channel<JsonElement>(Channel.UNLIMITED)
    val serverToClient = Channel<JsonElement>(Channel.UNLIMITED)
    val HelloRequestType = RequestType("hello", String.serializer(), String.serializer(), Unit.serializer())
    val HangRequestType = RequestType("hand", Unit.serializer(), Unit.serializer(), Unit.serializer())
    val PrintHelloNotification = NotificationType("printHello", String.serializer())

    withLsp(
      incoming = clientToServer,
      outgoing = serverToClient,
      handlers = lspHandlers {
        request(HelloRequestType) { str ->
          "Hello, $str"
        }
        notification(PrintHelloNotification) { str ->
          println("server: $str")
        }
        request(HangRequestType) {
          try {
            awaitCancellation()
          } catch (c: CancellationException) {
            println("cancelled by client")
            throw c
          }
        }
      },
    ) { server ->
      withLsp(
        incoming = serverToClient,
        outgoing = clientToServer,
        handlers = lspHandlers {
          notification(PrintHelloNotification) { str ->
            println("client: $str")
          }
        },
      ) { client ->
        println(client.request(HelloRequestType, "World"))
        client.notify(PrintHelloNotification, "Hello World")
        client.notify(PrintHelloNotification, "Hello World")
        val hangingRequestJob = launch {
          client.request(HangRequestType, Unit)
        }
        delay(100)
        hangingRequestJob.cancel()
        println("request cancelled")
        delay(100)
        println("quitting")
      }
    }
  }
}
