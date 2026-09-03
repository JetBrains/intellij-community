// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.credentialStore.mac;

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
import java.nio.charset.StandardCharsets;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * The generic password items of the login keychain through {@code SecKeychain*} downcalls into the Security framework.
 * macOS only: the first call loads the framework.
 * <p>
 * An item reference travels as a {@code long} and must go back through {@link #release}. A password copy that the framework
 * hands out is freed with {@code SecKeychainItemFreeContent} before the call returns; a password copy this class allocates is
 * zeroed before its arena closes.
 */
@ApiStatus.Internal
public final class Keychain {
  private Keychain() { }

  public static final int errSecSuccess = 0;
  public static final int errSecItemNotFound = -25300;
  public static final int errSecInvalidRecord = -67701;
  /** Also reported when the user clicks Deny in the access dialog. */
  public static final int errUserNameNotCorrect = -25293;
  /** The user canceled the operation, one of the Security framework result codes. */
  public static final int errSecUserCanceled = -128;
  /** The specified item already exists in the keychain. */
  public static final int errSecDuplicateItem = -25299;

  /** {@code kSecAccountItemAttr}, the four-character code {@code 'acct'} */
  private static final int kSecAccountItemAttr = ('a' << 24) | ('c' << 16) | ('c' << 8) | 't';
  private static final int kSecFormatUnknown = 0;
  private static final long MESSAGE_BUFFER_SIZE = 512;
  private static final int KCF_STRING_ENCODING_UTF8 = 0x08000100;

  /**
   * The result of {@link #findGenericPassword}.
   *
   * @param status   the {@code OSStatus}
   * @param password a copy of the password, when it was requested and found
   * @param itemRef  the item, or 0; release it with {@link #release}
   */
  public record Found(int status, byte @Nullable [] password, long itemRef) { }

  /** The result of {@link #copyAccountAttribute}. */
  public record Account(int status, @Nullable String value) { }

  /**
   * {@code SecKeychainFindGenericPassword}. With {@code readPassword} false, only the item reference is returned.
   */
  public static @NotNull Found findGenericPassword(byte @NotNull [] service, byte @Nullable [] account, boolean readPassword) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment passwordLength = readPassword ? arena.allocate(JAVA_INT) : MemorySegment.NULL;
      MemorySegment passwordData = readPassword ? arena.allocate(ADDRESS) : MemorySegment.NULL;
      MemorySegment itemRef = arena.allocate(ADDRESS);
      // a conditional expression passed to a signature-polymorphic call is typed as Object, so it needs a local
      MemorySegment accountSegment = account != null ? arena.allocateFrom(JAVA_BYTE, account) : MemorySegment.NULL;
      int status = (int)Handles.FIND_GENERIC_PASSWORD.invokeExact(
        MemorySegment.NULL, service.length, arena.allocateFrom(JAVA_BYTE, service),
        account != null ? account.length : 0, accountSegment,
        passwordLength, passwordData, itemRef);
      byte[] password = null;
      if (readPassword) {
        MemorySegment data = passwordData.get(ADDRESS, 0);
        if (data.address() != 0) {
          int length = passwordLength.get(JAVA_INT, 0);
          password = data.reinterpret(length).toArray(JAVA_BYTE);
          int ignored = (int)Handles.ITEM_FREE_CONTENT.invokeExact(MemorySegment.NULL, data);
        }
      }
      return new Found(status, password, itemRef.get(ADDRESS, 0).address());
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  /** {@code SecKeychainAddGenericPassword} into the default keychain. The native password copy is zeroed after the call. */
  public static int addGenericPassword(byte @NotNull [] service, byte @Nullable [] account, byte @Nullable [] password) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment passwordSegment = password != null ? arena.allocateFrom(JAVA_BYTE, password) : MemorySegment.NULL;
      MemorySegment accountSegment = account != null ? arena.allocateFrom(JAVA_BYTE, account) : MemorySegment.NULL;
      try {
        return (int)Handles.ADD_GENERIC_PASSWORD.invokeExact(
          MemorySegment.NULL, service.length, arena.allocateFrom(JAVA_BYTE, service),
          account != null ? account.length : 0, accountSegment,
          password != null ? password.length : 0, passwordSegment, MemorySegment.NULL);
      }
      finally {
        if (password != null) passwordSegment.fill((byte)0);
      }
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  /**
   * {@code SecKeychainItemModifyContent}: sets the account attribute and replaces the password data.
   * A {@code null} password stores empty data, as the JNA code did.
   */
  public static int modifyContent(long itemRef, byte @Nullable [] account, byte @Nullable [] password) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment attribute = arena.allocate(Handles.SEC_KEYCHAIN_ATTRIBUTE);
      attribute.set(JAVA_INT, 0, kSecAccountItemAttr);
      attribute.set(JAVA_INT, 4, account != null ? account.length : 0);
      attribute.set(ADDRESS, 8, account != null && account.length > 0 ? arena.allocateFrom(JAVA_BYTE, account) : MemorySegment.NULL);
      MemorySegment attributeList = arena.allocate(Handles.SEC_KEYCHAIN_ATTRIBUTE_LIST);
      attributeList.set(JAVA_INT, 0, 1);
      attributeList.set(ADDRESS, 8, attribute);
      MemorySegment passwordSegment = password != null ? arena.allocateFrom(JAVA_BYTE, password) : arena.allocate(1);
      try {
        return (int)Handles.ITEM_MODIFY_CONTENT.invokeExact(
          MemorySegment.ofAddress(itemRef), attributeList, password != null ? password.length : 0, passwordSegment);
      }
      finally {
        passwordSegment.fill((byte)0);
      }
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  /** {@code SecKeychainItemCopyAttributesAndData} for the account attribute alone. */
  public static @NotNull Account copyAccountAttribute(long itemRef) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment tags = arena.allocateFrom(JAVA_INT, kSecAccountItemAttr);
      MemorySegment formats = arena.allocateFrom(JAVA_INT, kSecFormatUnknown);
      MemorySegment info = arena.allocate(Handles.SEC_KEYCHAIN_ATTRIBUTE_INFO);
      info.set(JAVA_INT, 0, 1);
      info.set(ADDRESS, 8, tags);
      info.set(ADDRESS, 16, formats);
      MemorySegment attributeListRef = arena.allocate(ADDRESS);
      int status = (int)Handles.ITEM_COPY_ATTRIBUTES_AND_DATA.invokeExact(
        MemorySegment.ofAddress(itemRef), info, MemorySegment.NULL, attributeListRef, MemorySegment.NULL, MemorySegment.NULL);
      MemorySegment attributeList = attributeListRef.get(ADDRESS, 0);
      if (status != errSecSuccess || attributeList.address() == 0) {
        return new Account(status, null);
      }
      try {
        attributeList = attributeList.reinterpret(Handles.SEC_KEYCHAIN_ATTRIBUTE_LIST.byteSize());
        int count = attributeList.get(JAVA_INT, 0);
        MemorySegment attributes = attributeList.get(ADDRESS, 8).reinterpret(count * Handles.SEC_KEYCHAIN_ATTRIBUTE.byteSize());
        for (int i = 0; i < count; i++) {
          long offset = i * Handles.SEC_KEYCHAIN_ATTRIBUTE.byteSize();
          if (attributes.get(JAVA_INT, offset) != kSecAccountItemAttr) continue;
          int length = attributes.get(JAVA_INT, offset + 4);
          MemorySegment data = attributes.get(ADDRESS, offset + 8);
          if (data.address() == 0) continue;
          return new Account(status, new String(data.reinterpret(length).toArray(JAVA_BYTE), StandardCharsets.UTF_8));
        }
        return new Account(status, null);
      }
      finally {
        int ignored = (int)Handles.ITEM_FREE_ATTRIBUTES_AND_DATA.invokeExact(attributeList, MemorySegment.NULL);
      }
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  /** {@code SecKeychainItemDelete} */
  public static int deleteItem(long itemRef) {
    try {
      return (int)Handles.ITEM_DELETE.invokeExact(MemorySegment.ofAddress(itemRef));
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  /** {@code CFRelease} of an item reference */
  public static void release(long itemRef) {
    try {
      Handles.CF_RELEASE.invokeExact(MemorySegment.ofAddress(itemRef));
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  /** @return the text of {@code SecCopyErrorMessageString} for the status, or {@code null} */
  public static @Nullable String errorMessage(int status) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment message = (MemorySegment)Handles.COPY_ERROR_MESSAGE_STRING.invokeExact(status, MemorySegment.NULL);
      if (message.address() == 0) {
        return null;
      }
      try {
        MemorySegment buffer = arena.allocate(MESSAGE_BUFFER_SIZE);
        byte copied = (byte)Handles.CF_STRING_GET_C_STRING.invokeExact(message, buffer, MESSAGE_BUFFER_SIZE, KCF_STRING_ENCODING_UTF8);
        return copied != 0 ? buffer.getString(0) : null;
      }
      finally {
        Handles.CF_RELEASE.invokeExact(message);
      }
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  /** {@code OSStatus} and {@code UInt32} are {@code int}; every reference and pointer is an address. The frameworks live in the dyld shared cache. */
  private static final class Handles {
    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup SECURITY = SymbolLookup.libraryLookup("/System/Library/Frameworks/Security.framework/Security", Arena.global());
    private static final SymbolLookup CORE_FOUNDATION =
      SymbolLookup.libraryLookup("/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation", Arena.global());

    /** {@code SecKeychainAttribute { SecKeychainAttrType tag; UInt32 length; void *data; }}, 16 bytes */
    static final StructLayout SEC_KEYCHAIN_ATTRIBUTE = MemoryLayout.structLayout(JAVA_INT.withName("tag"), JAVA_INT.withName("length"), ADDRESS.withName("data"));
    /** {@code SecKeychainAttributeList { UInt32 count; SecKeychainAttribute *attr; }}, 16 bytes with padding */
    static final StructLayout SEC_KEYCHAIN_ATTRIBUTE_LIST = MemoryLayout.structLayout(JAVA_INT.withName("count"), MemoryLayout.paddingLayout(4), ADDRESS.withName("attr"));
    /** {@code SecKeychainAttributeInfo { UInt32 count; UInt32 *tag; UInt32 *format; }}, 24 bytes with padding */
    static final StructLayout SEC_KEYCHAIN_ATTRIBUTE_INFO =
      MemoryLayout.structLayout(JAVA_INT.withName("count"), MemoryLayout.paddingLayout(4), ADDRESS.withName("tag"), ADDRESS.withName("format"));

    /** {@code OSStatus SecKeychainFindGenericPassword(CFTypeRef keychain, UInt32, const char *service, UInt32, const char *account, UInt32 *passwordLength, void **passwordData, SecKeychainItemRef *item)} */
    static final MethodHandle FIND_GENERIC_PASSWORD = security("SecKeychainFindGenericPassword",
      FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
    /** {@code OSStatus SecKeychainAddGenericPassword(SecKeychainRef, UInt32, const char *service, UInt32, const char *account, UInt32 passwordLength, const void *password, SecKeychainItemRef *item)} */
    static final MethodHandle ADD_GENERIC_PASSWORD = security("SecKeychainAddGenericPassword",
      FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, ADDRESS));
    /** {@code OSStatus SecKeychainItemModifyContent(SecKeychainItemRef, const SecKeychainAttributeList *, UInt32 length, const void *data)} */
    static final MethodHandle ITEM_MODIFY_CONTENT = security("SecKeychainItemModifyContent", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS));
    /** {@code OSStatus SecKeychainItemCopyAttributesAndData(SecKeychainItemRef, SecKeychainAttributeInfo *, SecItemClass *, SecKeychainAttributeList **, UInt32 *length, void **data)} */
    static final MethodHandle ITEM_COPY_ATTRIBUTES_AND_DATA = security("SecKeychainItemCopyAttributesAndData",
      FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
    /** {@code OSStatus SecKeychainItemFreeAttributesAndData(SecKeychainAttributeList *, void *data)} */
    static final MethodHandle ITEM_FREE_ATTRIBUTES_AND_DATA = security("SecKeychainItemFreeAttributesAndData", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
    /** {@code OSStatus SecKeychainItemFreeContent(SecKeychainAttributeList *, void *data)} */
    static final MethodHandle ITEM_FREE_CONTENT = security("SecKeychainItemFreeContent", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
    /** {@code OSStatus SecKeychainItemDelete(SecKeychainItemRef)} */
    static final MethodHandle ITEM_DELETE = security("SecKeychainItemDelete", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    /** {@code CFStringRef SecCopyErrorMessageString(OSStatus, void *reserved)} */
    static final MethodHandle COPY_ERROR_MESSAGE_STRING = security("SecCopyErrorMessageString", FunctionDescriptor.of(ADDRESS, JAVA_INT, ADDRESS));
    /** {@code Boolean CFStringGetCString(CFStringRef, char *buffer, CFIndex size, CFStringEncoding)} */
    static final MethodHandle CF_STRING_GET_C_STRING =
      LINKER.downcallHandle(CORE_FOUNDATION.findOrThrow("CFStringGetCString"), FunctionDescriptor.of(JAVA_BYTE, ADDRESS, ADDRESS, JAVA_LONG, JAVA_INT));
    /** {@code void CFRelease(CFTypeRef)} */
    static final MethodHandle CF_RELEASE = LINKER.downcallHandle(CORE_FOUNDATION.findOrThrow("CFRelease"), FunctionDescriptor.ofVoid(ADDRESS));

    private static MethodHandle security(String name, FunctionDescriptor descriptor) {
      return LINKER.downcallHandle(SECURITY.findOrThrow(name), descriptor);
    }
  }
}
