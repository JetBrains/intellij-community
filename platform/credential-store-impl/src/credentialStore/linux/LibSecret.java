// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.credentialStore.linux;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * The synchronous password API of {@code libsecret} through downcalls. Linux only: the first call loads {@code libsecret-1.so.0},
 * and a missing library surfaces as {@link UnsatisfiedLinkError}, the same as the JNA loader reported it.
 * <p>
 * The password functions are variadic: attribute name and value pairs end with a {@code NULL}. Every call shape this class supports,
 * one or two attributes, has its own downcall handle, because a variadic downcall is bound to one argument list.
 * A password copy in native memory is zeroed before its arena closes.
 */
@ApiStatus.Internal
public final class LibSecret {
  private LibSecret() { }

  /** {@code SECRET_SCHEMA_DONT_MATCH_NAME} */
  public static final int SECRET_SCHEMA_DONT_MATCH_NAME = 2;
  /** {@code SECRET_SCHEMA_ATTRIBUTE_STRING} */
  public static final int SECRET_SCHEMA_ATTRIBUTE_STRING = 0;

  /** {@code GError { GQuark domain; gint code; gchar *message; }}, read and freed after a failed call */
  public record GError(int domain, int code, @Nullable String message) { }

  /** The {@code GError **} out parameter of one call. */
  public static final class ErrorOut {
    public @Nullable GError error;
  }

  public static int dbusErrorQuark() {
    try {
      return (int)Handles.G_DBUS_ERROR_QUARK.invokeExact();
    }
    catch (Throwable t) {
      throw rethrow(t);
    }
  }

  public static int secretErrorQuark() {
    try {
      return (int)Handles.SECRET_ERROR_GET_QUARK.invokeExact();
    }
    catch (Throwable t) {
      throw rethrow(t);
    }
  }

  /**
   * {@code secret_schema_new(name, flags, attributeName, SECRET_SCHEMA_ATTRIBUTE_STRING, ..., NULL)}. The library copies the strings.
   *
   * @param attributeNames one or two string attributes
   * @return a schema to release with {@link #schemaUnref}
   */
  public static @NotNull MemorySegment schemaNew(@NotNull String name, int flags, @NotNull String @NotNull ... attributeNames) {
    if (attributeNames.length != 1 && attributeNames.length != 2) {
      throw new IllegalArgumentException("one or two attributes are supported, got " + attributeNames.length);
    }
    try (Arena arena = Arena.ofConfined()) {
      List<Object> arguments = new ArrayList<>();
      arguments.add(arena.allocateFrom(name));
      arguments.add(flags);
      for (String attributeName : attributeNames) {
        arguments.add(arena.allocateFrom(attributeName));
        arguments.add(SECRET_SCHEMA_ATTRIBUTE_STRING);
      }
      arguments.add(MemorySegment.NULL);
      return (MemorySegment)Handles.schemaNew(attributeNames.length).invokeWithArguments(arguments);
    }
    catch (Throwable t) {
      throw rethrow(t);
    }
  }

  public static void schemaUnref(@NotNull MemorySegment schema) {
    try {
      Handles.SECRET_SCHEMA_UNREF.invokeExact(schema);
    }
    catch (Throwable t) {
      throw rethrow(t);
    }
  }

  /**
   * {@code secret_password_lookup_sync(schema, NULL, &error, name, value, ..., NULL)}.
   *
   * @param attributes one or two name and value pairs
   * @return the stored password, or {@code null} when there is none or the call failed
   */
  public static @Nullable String passwordLookupSync(@NotNull MemorySegment schema, @NotNull ErrorOut error, @NotNull String @NotNull ... attributes) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment errorSlot = arena.allocate(ADDRESS);
      List<Object> arguments = new ArrayList<>();
      arguments.add(schema);
      arguments.add(MemorySegment.NULL);
      arguments.add(errorSlot);
      addAttributes(arena, arguments, attributes);
      MemorySegment password = (MemorySegment)Handles.passwordLookupSync(attributes.length / 2).invokeWithArguments(arguments);
      error.error = readError(errorSlot);
      if (password.address() == 0) {
        return null;
      }
      try {
        return password.reinterpret(Long.MAX_VALUE).getString(0);
      }
      finally {
        Handles.SECRET_PASSWORD_FREE.invokeExact(password);
      }
    }
    catch (Throwable t) {
      throw rethrow(t);
    }
  }

  /**
   * {@code secret_password_store_sync(schema, NULL, label, password, NULL, &error, name, value, ..., NULL)} into the default collection.
   *
   * @param password UTF-8 bytes without a terminator; the native copy is zeroed after the call
   */
  public static void passwordStoreSync(@NotNull MemorySegment schema, @NotNull String label, byte @NotNull [] password, @NotNull ErrorOut error, @NotNull String @NotNull ... attributes) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment passwordSegment = arena.allocate(password.length + 1L);
      MemorySegment.copy(password, 0, passwordSegment, JAVA_BYTE, 0, password.length);
      try {
        MemorySegment errorSlot = arena.allocate(ADDRESS);
        List<Object> arguments = new ArrayList<>();
        arguments.add(schema);
        arguments.add(MemorySegment.NULL);
        arguments.add(arena.allocateFrom(label));
        arguments.add(passwordSegment);
        arguments.add(MemorySegment.NULL);
        arguments.add(errorSlot);
        addAttributes(arena, arguments, attributes);
        int ignored = (int)Handles.passwordStoreSync(attributes.length / 2).invokeWithArguments(arguments);
        error.error = readError(errorSlot);
      }
      finally {
        passwordSegment.fill((byte)0);
      }
    }
    catch (Throwable t) {
      throw rethrow(t);
    }
  }

  /** {@code secret_password_clear_sync(schema, NULL, &error, name, value, ..., NULL)} */
  public static void passwordClearSync(@NotNull MemorySegment schema, @NotNull ErrorOut error, @NotNull String @NotNull ... attributes) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment errorSlot = arena.allocate(ADDRESS);
      List<Object> arguments = new ArrayList<>();
      arguments.add(schema);
      arguments.add(MemorySegment.NULL);
      arguments.add(errorSlot);
      addAttributes(arena, arguments, attributes);
      int ignored = (int)Handles.passwordClearSync(attributes.length / 2).invokeWithArguments(arguments);
      error.error = readError(errorSlot);
    }
    catch (Throwable t) {
      throw rethrow(t);
    }
  }

  /** An {@link Error}, such as the {@link UnsatisfiedLinkError} of a missing library, leaves as is; the caller falls back to KWallet on it. */
  private static IllegalStateException rethrow(Throwable t) {
    if (t instanceof Error error) {
      throw error;
    }
    return new IllegalStateException(t);
  }

  private static void addAttributes(Arena arena, List<Object> arguments, String[] attributes) {
    if (attributes.length != 2 && attributes.length != 4) {
      throw new IllegalArgumentException("one or two attribute pairs are supported, got " + attributes.length + " strings");
    }
    for (String attribute : attributes) {
      arguments.add(arena.allocateFrom(attribute));
    }
    arguments.add(MemorySegment.NULL);
  }

  private static @Nullable GError readError(MemorySegment errorSlot) throws Throwable {
    MemorySegment error = errorSlot.get(ADDRESS, 0);
    if (error.address() == 0) {
      return null;
    }
    error = error.reinterpret(Handles.G_ERROR.byteSize());
    MemorySegment message = error.get(ADDRESS, Handles.G_ERROR.byteOffset(MemoryLayout.PathElement.groupElement("message")));
    GError result = new GError(
      error.get(JAVA_INT, 0),
      error.get(JAVA_INT, Handles.G_ERROR.byteOffset(MemoryLayout.PathElement.groupElement("code"))),
      message.address() != 0 ? message.reinterpret(Long.MAX_VALUE).getString(0) : null);
    Handles.G_ERROR_FREE.invokeExact(error);
    return result;
  }

  private static final class Handles {
    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup SECRET = load();

    private static SymbolLookup load() {
      try {
        return SymbolLookup.libraryLookup("libsecret-1.so.0", Arena.global());
      }
      catch (IllegalArgumentException e) {
        // an Error leaves a static initializer as is, so the caller sees the same failure as with the JNA loader
        throw new UnsatisfiedLinkError("libsecret-1.so.0: " + e.getMessage());
      }
    }

    /** {@code GError { guint32 domain; gint code; gchar *message; }} */
    static final StructLayout G_ERROR = MemoryLayout.structLayout(
      JAVA_INT.withName("domain"), JAVA_INT.withName("code"), ADDRESS.withName("message"));

    /** {@code GQuark g_dbus_error_quark(void)}, reached through the GIO dependency of libsecret */
    static final MethodHandle G_DBUS_ERROR_QUARK = downcall("g_dbus_error_quark", FunctionDescriptor.of(JAVA_INT));
    /** {@code GQuark secret_error_get_quark(void)} */
    static final MethodHandle SECRET_ERROR_GET_QUARK = downcall("secret_error_get_quark", FunctionDescriptor.of(JAVA_INT));
    /** {@code void g_error_free(GError *)} */
    static final MethodHandle G_ERROR_FREE = downcall("g_error_free", FunctionDescriptor.ofVoid(ADDRESS));
    /** {@code void secret_schema_unref(SecretSchema *)} */
    static final MethodHandle SECRET_SCHEMA_UNREF = downcall("secret_schema_unref", FunctionDescriptor.ofVoid(ADDRESS));
    /** {@code void secret_password_free(gchar *)}, which also wipes the text */
    static final MethodHandle SECRET_PASSWORD_FREE = downcall("secret_password_free", FunctionDescriptor.ofVoid(ADDRESS));

    private static final MethodHandle[] SCHEMA_NEW = {
      variadic("secret_schema_new", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT), ADDRESS, JAVA_INT, ADDRESS),
      variadic("secret_schema_new", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT), ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS),
    };
    private static final MethodHandle[] PASSWORD_LOOKUP_SYNC = {
      variadic("secret_password_lookup_sync", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS), ADDRESS, ADDRESS, ADDRESS),
      variadic("secret_password_lookup_sync", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS), ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS),
    };
    private static final MethodHandle[] PASSWORD_STORE_SYNC = {
      variadic("secret_password_store_sync", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS), ADDRESS, ADDRESS, ADDRESS),
      variadic("secret_password_store_sync", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS), ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS),
    };
    private static final MethodHandle[] PASSWORD_CLEAR_SYNC = {
      variadic("secret_password_clear_sync", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS), ADDRESS, ADDRESS, ADDRESS),
      variadic("secret_password_clear_sync", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS), ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS),
    };

    /** {@code SecretSchema *secret_schema_new(const gchar *name, SecretSchemaFlags flags, ...)} for one or two attributes */
    static MethodHandle schemaNew(int attributeCount) {
      return SCHEMA_NEW[attributeCount - 1];
    }

    /** {@code gchar *secret_password_lookup_sync(const SecretSchema *, GCancellable *, GError **, ...)} for one or two attribute pairs */
    static MethodHandle passwordLookupSync(int pairCount) {
      return PASSWORD_LOOKUP_SYNC[pairCount - 1];
    }

    /** {@code gboolean secret_password_store_sync(const SecretSchema *, const gchar *collection, const gchar *label, const gchar *password, GCancellable *, GError **, ...)} */
    static MethodHandle passwordStoreSync(int pairCount) {
      return PASSWORD_STORE_SYNC[pairCount - 1];
    }

    /** {@code gboolean secret_password_clear_sync(const SecretSchema *, GCancellable *, GError **, ...)} */
    static MethodHandle passwordClearSync(int pairCount) {
      return PASSWORD_CLEAR_SYNC[pairCount - 1];
    }

    private static MethodHandle downcall(String name, FunctionDescriptor descriptor) {
      return LINKER.downcallHandle(SECRET.findOrThrow(name), descriptor);
    }

    /** A variadic downcall: the fixed arguments come from {@code base}, and {@code variadic} lists the trailing layouts including the {@code NULL}. */
    private static MethodHandle variadic(String name, FunctionDescriptor base, MemoryLayout... variadic) {
      int firstVariadicArgument = base.argumentLayouts().size();
      return LINKER.downcallHandle(SECRET.findOrThrow(name), base.appendArgumentLayouts(variadic), Linker.Option.firstVariadicArg(firstVariadicArgument));
    }
  }
}
