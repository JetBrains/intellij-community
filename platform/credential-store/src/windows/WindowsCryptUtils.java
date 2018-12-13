// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.credentialStore.windows;

import com.intellij.util.containers.ContainerUtil;
import com.sun.jna.*;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIFunctionMapper;
import com.sun.jna.win32.W32APITypeMapper;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.util.Pair.pair;

/**
 * Windows Utilities for the Password Safe
 */
public class WindowsCryptUtils {
  /**
   * Unicode options for the libraries
   */
  private static final Map<String, Object> UNICODE_OPTIONS = ContainerUtil.newHashMap(
    pair(Library.OPTION_TYPE_MAPPER, W32APITypeMapper.UNICODE),
    pair(Library.OPTION_FUNCTION_MAPPER, W32APIFunctionMapper.UNICODE));

  private WindowsCryptUtils() { }

  /**
   * Protect the specified byte range
   *
   * @param data the data to protect
   * @return the the protected form the data
   */
  @NotNull
  public static byte[] protect(@NotNull byte[] data) {
    if(data.length == 0) {
      return data;
    }
    Memory input = new Memory(data.length);
    input.write(0, data, 0, data.length);
    Crypt32.DATA_BLOB in = new Crypt32.DATA_BLOB();
    in.cbData = new WinDef.DWORD(data.length);
    in.pbData = input;
    Crypt32.DATA_BLOB out = new Crypt32.DATA_BLOB();
    out.pbData = Pointer.NULL;
    Crypt32 crypt = Crypt32.INSTANCE;
    Kernel32 kernel = Kernel32.INSTANCE;
    boolean rc = crypt.CryptProtectData(in, "Master Key", Pointer.NULL, Pointer.NULL, Pointer.NULL, new WinDef.DWORD(0), out);
    return getBytes(out, kernel, rc);
  }

  /**
   * Unprotect the specified byte range
   *
   * @param data the data to protect
   * @return the the protected form the data
   */
  @NotNull
  public static byte[] unprotect(byte[] data) {
    if(data.length == 0) {
      return data;
    }
    Memory input = new Memory(data.length);
    input.write(0, data, 0, data.length);
    Crypt32.DATA_BLOB in = new Crypt32.DATA_BLOB();
    in.cbData = new WinDef.DWORD(data.length);
    in.pbData = input;
    Crypt32.DATA_BLOB out = new Crypt32.DATA_BLOB();
    out.pbData = Pointer.NULL;
    Crypt32 crypt = Crypt32.INSTANCE;
    Kernel32 kernel = Kernel32.INSTANCE;
    boolean rc = crypt.CryptUnprotectData(in, Pointer.NULL, Pointer.NULL, Pointer.NULL, Pointer.NULL, new WinDef.DWORD(0), out);
    return getBytes(out, kernel, rc);
  }

  private static byte[] getBytes(Crypt32.DATA_BLOB out, Kernel32 kernel, boolean rc) {
    if (!rc) {
      WinDef.DWORD drc = kernel.GetLastError();
      throw new RuntimeException("CryptProtectData failed: " + drc.intValue());
    }
    else {
      byte[] output = new byte[out.cbData.intValue()];
      out.pbData.read(0, output, 0, output.length);
      kernel.LocalFree(out.pbData);
      return output;
    }
  }

  public interface Crypt32 extends StdCallLibrary {
    Crypt32 INSTANCE = Native.loadLibrary("Crypt32", Crypt32.class, UNICODE_OPTIONS);

    boolean CryptProtectData(DATA_BLOB pDataIn,
                             String szDataDescr,
                             Pointer pOptionalEntropy,
                             Pointer pvReserved,
                             Pointer pPromptStruct,
                             WinDef.DWORD dwFlags,
                             DATA_BLOB pDataOut);

    boolean CryptUnprotectData(DATA_BLOB pDataIn,
                               Pointer ppszDataDescr,
                               Pointer pOptionalEntropy,
                               Pointer pvReserved,
                               Pointer pPromptStruct,
                               WinDef.DWORD dwFlags,
                               DATA_BLOB pDataOut);

    class DATA_BLOB extends Structure implements Structure.ByReference {
      private static final List __FIELDS = Arrays.asList("cbData", "pbData");

      public WinDef.DWORD cbData;
      public Pointer pbData;

      @Override
      protected List getFieldOrder() {
        return __FIELDS;
      }
    }
  }

  public interface Kernel32 extends StdCallLibrary {
    Kernel32 INSTANCE = Native.loadLibrary("Kernel32", Kernel32.class, UNICODE_OPTIONS);

    Pointer LocalFree(Pointer hMem);
    WinDef.DWORD GetLastError();
  }
}