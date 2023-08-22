// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.io.jsonRpc

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import com.intellij.util.ArrayUtil
import com.intellij.util.ArrayUtilRt
import com.intellij.util.Consumer
import com.intellij.util.SmartList
import com.intellij.util.io.releaseIfError
import io.netty.buffer.*
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntList
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import org.jetbrains.concurrency.Promise
import org.jetbrains.io.JsonReaderEx
import org.jetbrains.io.JsonUtil
import java.io.IOException
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicInteger

private val LOG = Logger.getInstance(JsonRpcServer::class.java)

private val INT_LIST_TYPE_ADAPTER_FACTORY = object : TypeAdapterFactory {
  private var typeAdapter: IntArrayListTypeAdapter<IntArrayList>? = null

  override fun <T> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
    if (type.type !== IntArrayList::class.java) {
      return null
    }

    if (typeAdapter == null) {
      typeAdapter = IntArrayListTypeAdapter()
    }
    @Suppress("UNCHECKED_CAST")
    return typeAdapter as TypeAdapter<T>?
  }
}

private val gson by lazy {
  GsonBuilder()
    .registerTypeAdapterFactory(INT_LIST_TYPE_ADAPTER_FACTORY)
    .disableHtmlEscaping()
    .create()
}

class JsonRpcServer(private val clientManager: ClientManager) : MessageServer {

  private val messageIdCounter = AtomicInteger()

  @TestOnly private var testDomain: Pair<String, Any>? = null

  init {
    val beans = mutableMapOf<String, JsonRpcDomainBean>()
    for (bean in JsonRpcDomainBean.EP_NAME.extensionList) {
      val prev = beans.put(bean.name, bean)
      if (prev != null && !prev.overridable) {
        throw IllegalArgumentException("${bean.name} is already registered")
      }
    }
  }

  @TestOnly fun registerTestDomain(name: String, domain: Any, disposable: Disposable) {
    testDomain = Pair(name, domain)
    Disposer.register(disposable, Disposable { testDomain = null })
  }

  override fun messageReceived(client: Client, message: CharSequence) {
    if (LOG.isDebugEnabled) {
      LOG.debug("IN $message")
    }

    val reader = JsonReaderEx(message)
    reader.beginArray()

    val messageId = if (reader.peek() == JsonToken.NUMBER) reader.nextInt() else -1
    val domainName = reader.nextString()
    if (domainName.length == 1) {
      val promise = client.messageCallbackMap.remove(messageId)
      if (domainName[0] == 'r') {
        if (promise == null) {
          LOG.error("Response with id $messageId was already processed")
          return
        }
        promise.setResult(JsonUtil.nextAny(reader))
      }
      else {
        promise!!.setError("error")
      }
      return
    }

    val domain = findDomain(domainName)
    if (domain == null) {
      processClientError(client, "Cannot find domain $domainName", messageId)
      return
    }
    val command = reader.nextString()
    if (domain is JsonServiceInvocator) {
      domain.invoke(command, client, reader, messageId, message)
      return
    }

    val parameters: Array<Any>
    if (reader.hasNext()) {
      val list = SmartList<Any>()
      JsonUtil.readListBody(reader, list)
      parameters = ArrayUtil.toObjectArray(list[0] as List<*>)
    }
    else {
      parameters = ArrayUtilRt.EMPTY_OBJECT_ARRAY
    }

    val isStatic = domain is Class<*>
    val methods: Array<Method>
    if (isStatic) {
      methods = (domain as Class<*>).declaredMethods
    }
    else {
      methods = domain.javaClass.methods
    }
    for (method in methods) {
      if (method.name == command) {
        method.isAccessible = true
        val result = method.invoke(if (isStatic) null else domain, *parameters)
        if (messageId != -1) {
          if (result is ByteBuf) {
            result.releaseIfError {
              client.send(encodeMessage(client.byteBufAllocator, messageId, rawData = result))
            }
          }
          else {
            client.send(encodeMessage(client.byteBufAllocator, messageId, params = if (result == null) ArrayUtilRt.EMPTY_OBJECT_ARRAY else arrayOf(result)))
          }
        }
        return
      }
    }

    processClientError(client, "Cannot find method $domain.$command", messageId)
  }

  private fun findDomain(domainName: String): Any? {
    val testDomain = this.testDomain
    if (testDomain != null && testDomain.first == domainName) return testDomain.second
    return JsonRpcDomainBean.EP_NAME.getByKey(domainName, JsonRpcServer::class.java, JsonRpcDomainBean::name)?.instance
  }

  private fun processClientError(client: Client, @NonNls error: String, messageId: Int) {
    try {
      LOG.error(error)
    }
    finally {
      if (messageId != -1) {
        sendErrorResponse(client, messageId, error)
      }
    }
  }

  fun sendResponse(client: Client, messageId: Int, rawData: ByteBuf? = null) {
    client.send(encodeMessage(client.byteBufAllocator, messageId, rawData = rawData))
  }

  fun sendErrorResponse(client: Client, messageId: Int, message: CharSequence?) {
    client.send(encodeMessage(client.byteBufAllocator, messageId, "e", params = arrayOf(message)))
  }

  fun sendWithRawPart(client: Client, domain: String, command: String, rawData: ByteBuf, params: Array<*>): Boolean {
    return client.send(encodeMessage(client.byteBufAllocator, -1, domain, command, rawData = rawData, params = params)).cause() == null
  }

  fun send(client: Client, domain: String, command: String, vararg params: Any?) {
    client.send(encodeMessage(client.byteBufAllocator, -1, domain, command, params = params))
  }

  fun <T> call(client: Client, domain: String, command: String, vararg params: Any?): Promise<T> {
    val messageId = messageIdCounter.andIncrement
    val message = encodeMessage(client.byteBufAllocator, messageId, domain, command, params = params)
    return client.send(messageId, message)!!
  }

  fun send(domain: String, command: String, vararg params: Any?) {
    if (clientManager.hasClients()) {
      val messageId = -1
      val message = encodeMessage(ByteBufAllocator.DEFAULT, messageId, domain, command, params = params)
      clientManager.send<Any?>(messageId, message)
    }
  }

  private fun encodeMessage(byteBufAllocator: ByteBufAllocator,
                            messageId: Int = -1,
                            domain: String? = null,
                            command: String? = null,
                            rawData: ByteBuf? = null,
                            params: Array<*> = ArrayUtilRt.EMPTY_OBJECT_ARRAY): ByteBuf {
    val buffer = doEncodeMessage(byteBufAllocator, messageId, domain, command, params, rawData)
    if (LOG.isDebugEnabled) {
      LOG.debug("OUT ${buffer.toString(Charsets.UTF_8)}")
    }
    return buffer
  }

  private fun doEncodeMessage(byteBufAllocator: ByteBufAllocator,
                              id: Int,
                              domain: String?,
                              command: String?,
                              params: Array<*>,
                              rawData: ByteBuf?): ByteBuf {
    var buffer = byteBufAllocator.ioBuffer()
    buffer.writeByte('[')
    var sb: StringBuilder? = null
    if (id != -1) {
      sb = StringBuilder()
      buffer.writeAscii(sb.append(id))
      sb.setLength(0)
    }

    if (domain != null) {
      if (id != -1) {
        buffer.writeByte(',')
      }
      buffer.writeByte('"').writeAscii(domain).writeByte('"')
      if (command != null) {
        buffer.writeByte(',').writeByte('"').writeAscii(command).writeByte('"')
      }
    }

    var effectiveBuffer = buffer
    if (params.isNotEmpty() || rawData != null) {
      buffer.writeByte(',').writeByte('[')
      encodeParameters(buffer, params, sb)
      if (rawData != null) {
        if (params.isNotEmpty()) {
          buffer.writeByte(',')
        }
        effectiveBuffer = byteBufAllocator.compositeBuffer().addComponent(buffer).addComponent(rawData)
        buffer = byteBufAllocator.ioBuffer()
      }
      buffer.writeByte(']')
    }

    buffer.writeByte(']')
    return effectiveBuffer.addBuffer(buffer)
  }

  private fun encodeParameters(buffer: ByteBuf, params: Array<*>, _sb: StringBuilder?) {
    var sb = _sb
    var writer: JsonWriter? = null
    var hasPrev = false
    for (param in params) {
      if (hasPrev) {
        buffer.writeByte(',')
      }
      else {
        hasPrev = true
      }

      // gson - SOE if param has type class com.intellij.openapi.editor.impl.DocumentImpl$MyCharArray, so, use hack
      when (param) {
        is CharSequence -> {
          JsonUtil.escape(param, buffer)
        }
        null -> {
          buffer.writeAscii("null")
        }
        is Boolean -> {
          buffer.writeAscii(param.toString())
        }
        is Number -> {
          if (sb == null) {
            sb = StringBuilder()
          }
          when (param) {
            is Int -> {
              sb.append(param.toInt())
            }
            is Long -> {
              sb.append(param.toLong())
            }
            is Float -> {
              sb.append(param.toFloat())
            }
            is Double -> {
              sb.append(param.toDouble())
            }
            else -> {
              sb.append(param.toString())
            }
          }
          buffer.writeAscii(sb)
          sb.setLength(0)
        }
        is Consumer<*> -> {
          if (sb == null) {
            sb = StringBuilder()
          }
          @Suppress("UNCHECKED_CAST")
          (param as Consumer<StringBuilder>).consume(sb)
          ByteBufUtil.writeUtf8(buffer, sb)
          sb.setLength(0)
        }
        else -> {
          if (writer == null) {
            writer = JsonWriter(ByteBufUtf8Writer(buffer))
          }
          (gson.getAdapter(param.javaClass) as TypeAdapter<Any>).write(writer, param)
        }
      }
    }
  }
}

private fun ByteBuf.writeByte(c: Char) = writeByte(c.code)

private fun ByteBuf.writeAscii(s: CharSequence): ByteBuf {
  ByteBufUtil.writeAscii(this, s)
  return this
}

private class IntArrayListTypeAdapter<T> : TypeAdapter<T>() {
  override fun write(out: JsonWriter, value: T) {
    var error: IOException? = null
    out.beginArray()
    val iterator = (value as IntList).iterator()
    while (iterator.hasNext()) {
      try {
        out.value(iterator.nextInt().toLong())
      }
      catch (e: IOException) {
        error = e
      }
    }

    error?.let { throw it }
    out.endArray()
  }

  override fun read(`in`: com.google.gson.stream.JsonReader) = throw UnsupportedOperationException()
}

// addComponent always add sliced component, so, we must add last buffer only after all writes
private fun ByteBuf.addBuffer(buffer: ByteBuf): ByteBuf {
  if (this !== buffer) {
    (this as CompositeByteBuf).addComponent(buffer)
    writerIndex(capacity())
  }
  return this
}
