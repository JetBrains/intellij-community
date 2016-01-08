package org.jetbrains.io.jsonRpc

import com.google.gson.*
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.util.ArrayUtil
import com.intellij.util.ArrayUtilRt
import com.intellij.util.Consumer
import com.intellij.util.SmartList
import com.intellij.util.text.CharSequenceBackedByArray
import gnu.trove.THashMap
import gnu.trove.TIntArrayList
import gnu.trove.TIntProcedure
import io.netty.buffer.*
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.io.JsonReaderEx
import org.jetbrains.io.JsonUtil

import java.io.IOException
import java.lang.reflect.Method
import java.lang.reflect.Type
import java.util.Arrays
import java.util.concurrent.atomic.AtomicInteger

class JsonRpcServer(private val clientManager: ClientManager) : MessageServer {

  private val messageIdCounter = AtomicInteger()
  private val gson: Gson

  private val domains = THashMap<String, NotNullLazyValue<Any>>()

  init {
    gson = GsonBuilder().registerTypeAdapter(CharSequenceBackedByArray::class.java, JsonSerializer<com.intellij.util.text.CharSequenceBackedByArray> { src, typeOfSrc, context -> JsonPrimitive(src.toString()) }).registerTypeAdapterFactory(INT_LIST_TYPE_ADAPTER_FACTORY).disableHtmlEscaping().create()
  }

  @JvmOverloads fun registerDomain(name: String, commands: NotNullLazyValue<Any>, overridable: Boolean = false) {
    if (domains.containsKey(name)) {
      if (overridable) {
        return
      }
      else {
        throw IllegalArgumentException(name + " is already registered")
      }
    }

    domains.put(name, commands)
  }

  @Throws(IOException::class)
  override fun messageReceived(client: Client, message: CharSequence) {
    if (LOG.isDebugEnabled) {
      LOG.debug("IN " + message)
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

    val domainHolder = domains[domainName]
    if (domainHolder == null) {
      val error = "Cannot find domain " + domainName
      try {
        LOG.error(error)
      }
      finally {
        if (messageId != -1) {
          sendErrorResponse(messageId, client, message)
        }
      }
      return
    }

    val domain = domainHolder.value
    val command = reader.nextString()
    if (domain is JsonServiceInvocator) {
      domain.invoke(command, client, reader, messageId, message)
      return
    }

    val parameters: Array<Any>
    if (reader.hasNext()) {
      val list = SmartList<Any>()
      JsonUtil.readListBody(reader, list)
      parameters = ArrayUtil.toObjectArray(list)
    }
    else {
      parameters = ArrayUtilRt.EMPTY_OBJECT_ARRAY
    }

    try {
      val isStatic = domain is Class<Any>
      val methods: Array<Method>
      if (isStatic) {
        methods = (domain as Class<Any>).declaredMethods
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
              var success = false
              try {
                client.send(encodeMessage(client.byteBufAllocator, messageId, null, null, result, null))
                success = true
              }
              finally {
                if (!success) {
                  result.release()
                }
              }
            }
            else {
              client.send(encodeMessage(client.byteBufAllocator, messageId, null, null, null, arrayOf(result)))
            }
          }
          return
        }
      }

      throw NoSuchMethodException(command)
    }
    catch (e: Throwable) {
      throw IOException(e)
    }

  }

  fun sendResponse(messageId: Int, client: Client, rawMessage: ByteBuf?) {
    client.send(encodeMessage(client.byteBufAllocator, messageId, null, null, rawMessage, ArrayUtil.EMPTY_OBJECT_ARRAY))
  }

  fun sendErrorResponse(messageId: Int, client: Client, rawMessage: CharSequence?) {
    client.send(encodeMessage(client.byteBufAllocator, messageId, "e", null, null, arrayOf<Any>(rawMessage)))
  }

  fun <T> sendToClients(domain: String, command: String, results: List<AsyncPromise<Pair<Client, T>>>?, vararg params: Any) {
    if (clientManager.hasClients()) {
      sendToClients(if (results == null) -1 else messageIdCounter.andIncrement, domain, command, results, params)
    }
  }

  private fun <T> sendToClients(messageId: Int, domain: String?, command: String?, results: List<AsyncPromise<Pair<Client, T>>>?, params: Array<Any>) {
    clientManager.send(messageId, encodeMessage(ByteBufAllocator.DEFAULT, messageId, domain, command, null, params), results)
  }

  fun sendWithRawPart(client: Client, domain: String, command: String, rawMessage: ByteBuf?, vararg params: Any): Boolean {
    client.send(encodeMessage(client.byteBufAllocator, -1, domain, command, rawMessage, params))
    return true
  }

  fun send(client: Client, domain: String, command: String, vararg params: Any) {
    sendWithRawPart(client, domain, command, null, *params)
  }

  fun <T> call(client: Client, domain: String, command: String, vararg params: Any): Promise<T> {
    val messageId = messageIdCounter.andIncrement
    val message = encodeMessage(client.byteBufAllocator, messageId, domain, command, null, params)
    val result = client.send<T>(messageId, message)
    LOG.assertTrue(result != null)
    return result
  }

  private fun encodeMessage(byteBufAllocator: ByteBufAllocator,
                            messageId: Int,
                            domain: String?,
                            command: String?,
                            rawData: ByteBuf?,
                            params: Array<Any>?): ByteBuf {
    var buffer = byteBufAllocator.ioBuffer()
    var success = false
    try {
      val notNullParams = params ?: ArrayUtil.EMPTY_OBJECT_ARRAY
      buffer = doEncodeMessage(byteBufAllocator, buffer, messageId, domain, command, notNullParams, rawData)
      if (LOG.isDebugEnabled) {
        LOG.debug("OUT " + domain + '.' + command + if (notNullParams.size == 0) "" else " " + Arrays.toString(params) + if (rawData == null) "" else " " + rawData.toString(CharsetToolkit.UTF8_CHARSET))
      }
      success = true
      return buffer
    }
    catch (e: IOException) {
      throw RuntimeException(e)
    }
    finally {
      if (!success) {
        buffer.release()
      }
    }
  }

  @Throws(IOException::class)
  private fun doEncodeMessage(byteBufAllocator: ByteBufAllocator,
                              buffer: ByteBuf,
                              id: Int,
                              domain: String?,
                              command: String?,
                              params: Array<Any>,
                              rawData: ByteBuf?): ByteBuf {
    var buffer = buffer
    buffer.writeByte('[')
    var effectiveBuffer = buffer
    var hasPrev = false
    var sb: StringBuilder? = null
    if (id != -1) {
      sb = StringBuilder()
      ByteBufUtil.writeAscii(buffer, sb.append(id))
      sb.setLength(0)
      hasPrev = true
    }

    if (domain != null) {
      if (hasPrev) {
        buffer.writeByte(',')
      }
      buffer.writeByte('"')
      ByteBufUtil.writeAscii(buffer, domain)
      buffer.writeByte('"').writeByte(',').writeByte('"')
      if (command == null) {
        if (rawData != null) {
          effectiveBuffer = byteBufAllocator.compositeBuffer().addComponent(buffer).addComponent(rawData)
          buffer = byteBufAllocator.ioBuffer()
        }
        buffer.writeByte('"')
        return addBuffer(effectiveBuffer, buffer)
      }
      else {
        ByteBufUtil.writeAscii(buffer, command)
        buffer.writeByte('"')
      }
    }

    encodeParameters(buffer, params, sb)
    if (rawData != null) {
      if (params.size > 0) {
        buffer.writeByte(',')
      }
      effectiveBuffer = byteBufAllocator.compositeBuffer().addComponent(buffer).addComponent(rawData)
      buffer = byteBufAllocator.ioBuffer()
    }
    buffer.writeByte(']')

    buffer.writeByte(']')
    return addBuffer(effectiveBuffer, buffer)
  }

  @Throws(IOException::class)
  private fun encodeParameters(buffer: ByteBuf, params: Array<Any>, sb: StringBuilder?) {
    var sb = sb
    var writer: JsonWriter? = null
    buffer.writeByte(',').writeByte('[')
    var hasPrev = false
    for (param in params) {
      if (hasPrev) {
        buffer.writeByte(',')
      }
      else {
        hasPrev = true
      }

      // gson - SOE if param has type class com.intellij.openapi.editor.impl.DocumentImpl$MyCharArray, so, use hack
      if (param is CharSequence) {
        JsonUtil.escape(param as CharSequence?, buffer)
      }
      else if (param == null) {
        ByteBufUtil.writeAscii(buffer, "null")
      }
      else if (param is Boolean) {
        ByteBufUtil.writeAscii(buffer, param.toString())
      }
      else if (param is Number) {
        if (sb == null) {
          sb = StringBuilder()
        }
        if (param is Int) {
          sb.append(param.toInt())
        }
        else if (param is Long) {
          sb.append(param.toLong())
        }
        else if (param is Float) {
          sb.append(param.toFloat())
        }
        else if (param is Double) {
          sb.append(param.toDouble())
        }
        else {
          sb.append(param.toString())
        }
        ByteBufUtil.writeAscii(buffer, sb)
        sb.setLength(0)
      }
      else if (param is Consumer<Any>) {
        if (sb == null) {
          sb = StringBuilder()
        }
        //noinspection unchecked
        (param as Consumer<StringBuilder>).consume(sb)
        ByteBufUtilEx.writeUtf8(buffer, sb)
        sb.setLength(0)
      }
      else {
        if (writer == null) {
          writer = JsonWriter(ByteBufUtf8Writer(buffer))
        }
        //noinspection unchecked
        (gson.getAdapter(param.javaClass) as TypeAdapter<Any>).write(writer, param)
      }
    }
  }

  private class IntArrayListTypeAdapter<T> : TypeAdapter<T>() {
    @Throws(IOException::class)
    override fun write(out: JsonWriter, value: T) {
      val error = Ref<IOException>()
      out.beginArray()
      (value as TIntArrayList).forEach { value ->
        try {
          out.value(value.toLong())
        }
        catch (e: IOException) {
          error.set(e)
        }

        error.isNull
      }

      if (!error.isNull) {
        throw error.get()
      }

      out.endArray()
    }

    @Throws(IOException::class)
    override fun read(`in`: com.google.gson.stream.JsonReader): T {
      throw UnsupportedOperationException()
    }
  }

  companion object {
    protected val LOG = Logger.getInstance(JsonRpcServer::class.java)

    private val INT_LIST_TYPE_ADAPTER_FACTORY = object : TypeAdapterFactory {
      private var typeAdapter: IntArrayListTypeAdapter<TIntArrayList>? = null

      override fun <T> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
        if (type.type !== TIntArrayList::class.java) {
          return null
        }
        if (typeAdapter == null) {
          typeAdapter = IntArrayListTypeAdapter<TIntArrayList>()
        }
        //noinspection unchecked
        return typeAdapter as TypeAdapter<T>?
      }
    }

    private // addComponent always add sliced component, so, we must add last buffer only after all writes
    fun addBuffer(buffer: ByteBuf, lastComponent: ByteBuf): ByteBuf {
      if (buffer !== lastComponent) {
        (buffer as CompositeByteBuf).addComponent(lastComponent)
        buffer.writerIndex(buffer.capacity())
      }
      return buffer
    }
  }
}
