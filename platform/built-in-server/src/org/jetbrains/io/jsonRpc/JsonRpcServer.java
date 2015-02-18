package org.jetbrains.io.jsonRpc;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Consumer;
import com.intellij.util.SmartList;
import com.intellij.util.text.CharSequenceBackedByArray;
import gnu.trove.THashMap;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntProcedure;
import io.netty.buffer.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.io.JsonReaderEx;
import org.jetbrains.io.JsonUtil;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class JsonRpcServer implements MessageServer {
  protected static final Logger LOG = Logger.getInstance(JsonRpcServer.class);

  private static final TypeAdapterFactory INT_LIST_TYPE_ADAPTER_FACTORY = new TypeAdapterFactory() {
    private IntArrayListTypeAdapter<TIntArrayList> typeAdapter;

    @Nullable
    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
      if (type.getType() != TIntArrayList.class) {
        return null;
      }
      if (typeAdapter == null) {
        typeAdapter = new IntArrayListTypeAdapter<TIntArrayList>();
      }
      //noinspection unchecked
      return (TypeAdapter<T>)typeAdapter;
    }
  };

  private final AtomicInteger messageIdCounter = new AtomicInteger();
  private final ClientManager clientManager;
  private final Gson gson;

  private final Map<String, NotNullLazyValue> domains = new THashMap<String, NotNullLazyValue>();

  public JsonRpcServer(@NotNull ClientManager clientManager) {
    this.clientManager = clientManager;
    gson = new GsonBuilder().registerTypeAdapter(CharSequenceBackedByArray.class, new JsonSerializer<CharSequenceBackedByArray>() {
      @Override
      public JsonElement serialize(CharSequenceBackedByArray src, Type typeOfSrc, JsonSerializationContext context) {
        return new JsonPrimitive(src.toString());
      }
    }).registerTypeAdapterFactory(INT_LIST_TYPE_ADAPTER_FACTORY).disableHtmlEscaping().create();
  }

  public void registerDomain(@NotNull String name, @NotNull NotNullLazyValue commands) {
    registerDomain(name, commands, false);
  }

  public void registerDomain(@NotNull String name, @NotNull NotNullLazyValue commands, boolean overridable) {
    if (domains.containsKey(name)) {
      if (overridable) {
        return;
      }
      else {
        throw new IllegalArgumentException(name + " is already registered");
      }
    }

    domains.put(name, commands);
  }

  @Override
  public void messageReceived(@NotNull Client client, @NotNull CharSequence message, boolean isBinary) throws IOException {
    if (LOG.isDebugEnabled()) {
      LOG.debug("IN " + message);
    }

    JsonReaderEx reader = new JsonReaderEx(message);
    if (!isBinary) {
      reader.beginArray();
    }

    int messageId = reader.peek() == JsonToken.NUMBER ? reader.nextInt() : -1;
    String domainName = reader.nextString();
    if (domainName.length() == 1) {
      AsyncPromise<Object> promise = client.messageCallbackMap.remove(messageId);
      if (domainName.charAt(0) == 'r') {
        if (promise == null) {
          LOG.error("Response with id " + messageId + " was already processed");
          return;
        }
        promise.setResult(JsonUtil.nextAny(reader));
      }
      else {
        promise.setError(Promise.createError("error"));
      }
      return;
    }

    NotNullLazyValue domainHolder = domains.get(domainName);
    if (domainHolder == null) {
      LOG.error("Cannot find domain " + domainName);
      return;
    }

    Object domain = domainHolder.getValue();
    String command = reader.nextString();
    if (domain instanceof JsonServiceInvocator) {
      ((JsonServiceInvocator)domain).invoke(command, client, reader, messageId);
      return;
    }

    Object[] parameters;
    if (reader.hasNext()) {
      List<Object> list = new SmartList<Object>();
      JsonUtil.readListBody(reader, list);
      parameters = ArrayUtil.toObjectArray(list);
    }
    else {
      parameters = ArrayUtilRt.EMPTY_OBJECT_ARRAY;
    }

    if (!isBinary) {
      reader.endArray();
    }

    try {
      boolean isStatic = domain instanceof Class;
      Method[] methods;
      if (isStatic) {
        methods = ((Class)domain).getDeclaredMethods();
      }
      else {
        methods = domain.getClass().getMethods();
      }
      for (Method method : methods) {
        if (method.getName().equals(command)) {
          method.setAccessible(true);
          Object result = method.invoke(isStatic ? null : domain, parameters);
          if (messageId != -1) {
            client.send(encodeMessage(client.getByteBufAllocator(), messageId, null, null, null, new Object[]{result}));
          }
          return;
        }
      }

      throw new NoSuchMethodException(command);
    }
    catch (Throwable e) {
      throw new IOException(e);
    }
  }

  public void sendResponse(int messageId, @NotNull Client client, @Nullable ByteBuf rawMessage) {
    client.send(encodeMessage(client.getByteBufAllocator(), messageId, null, null, rawMessage, ArrayUtil.EMPTY_OBJECT_ARRAY));
  }

  public void sendErrorResponse(int messageId, @NotNull Client client, @Nullable CharSequence rawMessage) {
    client.send(encodeMessage(client.getByteBufAllocator(), messageId, "e", null, null, new Object[]{rawMessage}));
  }

  @SuppressWarnings("unused")
  public void sendToClients(@NotNull String domain, @NotNull String name) {
    sendToClients(domain, name, null);
  }

  public <T> void sendToClients(@NotNull String domain, @NotNull String command, @Nullable List<AsyncPromise<Pair<Client, T>>> results, Object... params) {
    if (clientManager.hasClients()) {
      sendToClients(results == null ? -1 : messageIdCounter.getAndIncrement(), domain, command, results, params);
    }
  }

  private <T> void sendToClients(int messageId, @Nullable String domain, @Nullable String command, @Nullable List<AsyncPromise<Pair<Client, T>>> results, Object[] params) {
    clientManager.send(messageId, encodeMessage(ByteBufAllocator.DEFAULT, messageId, domain, command, null, params), results);
  }

  public boolean sendWithRawPart(@NotNull Client client, @NotNull String domain, @NotNull String command, @Nullable ByteBuf rawMessage, Object... params) {
    client.send(encodeMessage(client.getByteBufAllocator(), -1, domain, command, rawMessage, params));
    return true;
  }

  public void send(@NotNull Client client, @NotNull String domain, @NotNull String command, Object... params) {
    sendWithRawPart(client, domain, command, null, params);
  }

  @NotNull
  public <T> Promise<T> call(@NotNull Client client, @NotNull String domain, @NotNull String command, Object... params) {
    int messageId = messageIdCounter.getAndIncrement();
    ByteBuf message = encodeMessage(client.getByteBufAllocator(), messageId, domain, command, null, params);
    AsyncPromise<T> result = client.send(messageId, message);
    LOG.assertTrue(result != null);
    return result;
  }

  @NotNull
  private ByteBuf encodeMessage(@NotNull ByteBufAllocator byteBufAllocator,
                                int messageId,
                                @Nullable String domain,
                                @Nullable String command,
                                @Nullable ByteBuf rawData,
                                @Nullable Object[] params) {
    ByteBuf buffer = byteBufAllocator.ioBuffer();
    boolean success = false;
    try {
      Object[] notNullParams = params == null ? ArrayUtil.EMPTY_OBJECT_ARRAY : params;
      buffer = doEncodeMessage(byteBufAllocator, buffer, messageId, domain, command, notNullParams, rawData);
      if (LOG.isDebugEnabled()) {
        LOG.debug("OUT " + domain + '.' + command + (notNullParams.length == 0 ? "" : " " + Arrays.toString(params)) + (rawData == null ? "" : " " + rawData.toString(CharsetToolkit.UTF8_CHARSET)));
      }
      success = true;
      return buffer;
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    finally {
      if (!success) {
        buffer.release();
      }
    }
  }

  @NotNull
  private ByteBuf doEncodeMessage(@NotNull ByteBufAllocator byteBufAllocator,
                                  @NotNull ByteBuf buffer,
                                  int id,
                                  @Nullable String domain,
                                  @Nullable String command,
                                  @NotNull Object[] params,
                                  @Nullable ByteBuf rawData) throws IOException {
    buffer.writeByte('[');
    ByteBuf effectiveBuffer = buffer;
    boolean hasPrev = false;
    StringBuilder sb = null;
    if (id != -1) {
      sb = new StringBuilder();
      ByteBufUtil.writeAscii(buffer, sb.append(id));
      sb.setLength(0);
      hasPrev = true;
    }

    if (domain != null) {
      if (hasPrev) {
        buffer.writeByte(',');
      }
      buffer.writeByte('"');
      ByteBufUtil.writeAscii(buffer, domain);
      buffer.writeByte('"').writeByte(',').writeByte('"');
      if (command == null) {
        if (rawData != null) {
          effectiveBuffer = byteBufAllocator.compositeBuffer().addComponent(buffer).addComponent(rawData);
          buffer = byteBufAllocator.ioBuffer();
        }
        buffer.writeByte('"');
        return addBuffer(effectiveBuffer, buffer);
      }
      else {
        ByteBufUtil.writeAscii(buffer, command);
        buffer.writeByte('"');
      }
    }

    encodeParameters(buffer, params, rawData, sb);
    if (rawData != null) {
      if (params.length > 0) {
        buffer.writeByte(',');
      }
      effectiveBuffer = byteBufAllocator.compositeBuffer().addComponent(buffer).addComponent(rawData);
      buffer = byteBufAllocator.ioBuffer();
    }
    buffer.writeByte(']');

    buffer.writeByte(']');
    return addBuffer(effectiveBuffer, buffer);
  }

  @NotNull
  // addComponent always add sliced component, so, we must add last buffer only after all writes
  private static ByteBuf addBuffer(@NotNull ByteBuf buffer, @NotNull ByteBuf lastComponent) {
    if (buffer != lastComponent) {
      ((CompositeByteBuf)buffer).addComponent(lastComponent);
      buffer.writerIndex(buffer.capacity());
    }
    return buffer;
  }

  private void encodeParameters(@NotNull ByteBuf buffer, @NotNull Object[] params, @Nullable ByteBuf rawData, @Nullable StringBuilder sb) throws IOException {
    JsonWriter writer = null;
    buffer.writeByte(',').writeByte('[');
    boolean hasPrev = false;
    for (Object param : params) {
      if (hasPrev) {
        buffer.writeByte(',');
      }
      else {
        hasPrev = true;
      }

      // gson - SOE if param has type class com.intellij.openapi.editor.impl.DocumentImpl$MyCharArray, so, use hack
      if (param instanceof CharSequence) {
        JsonUtil.escape(((CharSequence)param), buffer);
      }
      else if (param == null) {
        ByteBufUtil.writeAscii(buffer, "null");
      }
      else if (param instanceof Boolean) {
        ByteBufUtil.writeAscii(buffer, param.toString());
      }
      else if (param instanceof Number) {
        if (sb == null) {
          sb = new StringBuilder();
        }
        if (param instanceof Integer) {
          sb.append(((Integer)param).intValue());
        }
        else if (param instanceof Long) {
          sb.append(((Long)param).longValue());
        }
        else if (param instanceof Float) {
          sb.append(((Float)param).floatValue());
        }
        else if (param instanceof Double) {
          sb.append(((Double)param).doubleValue());
        }
        else {
          sb.append(param.toString());
        }
        ByteBufUtil.writeAscii(buffer, sb);
        sb.setLength(0);
      }
      else if (param instanceof Consumer) {
        if (sb == null) {
          sb = new StringBuilder();
        }
        //noinspection unchecked
        ((Consumer<StringBuilder>)param).consume(sb);
        ByteBufUtilEx.writeUtf8(buffer, sb);
        sb.setLength(0);
      }
      else {
        if (writer == null) {
          writer = new JsonWriter(new ByteBufUtf8Writer(buffer));
        }
        //noinspection unchecked
        ((TypeAdapter<Object>)gson.getAdapter(param.getClass())).write(writer, param);
      }
    }
  }

  private static class IntArrayListTypeAdapter<T> extends TypeAdapter<T> {
    @Override
    public void write(final JsonWriter out, T value) throws IOException {
      final Ref<IOException> error = new Ref<IOException>();
      out.beginArray();
      ((TIntArrayList)value).forEach(new TIntProcedure() {
        @Override
        public boolean execute(int value) {
          try {
            out.value(value);
          }
          catch (IOException e) {
            error.set(e);
          }
          return error.isNull();
        }
      });

      if (!error.isNull()) {
        throw error.get();
      }

      out.endArray();
    }

    @Override
    public T read(com.google.gson.stream.JsonReader in) throws IOException {
      throw new UnsupportedOperationException();
    }
  }
}
