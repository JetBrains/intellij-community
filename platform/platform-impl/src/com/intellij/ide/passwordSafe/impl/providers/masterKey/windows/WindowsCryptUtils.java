/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.passwordSafe.impl.providers.masterKey.windows;

import com.intellij.ide.passwordSafe.MasterPasswordUnavailableException;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.examples.win32.W32API;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIFunctionMapper;
import com.sun.jna.win32.W32APITypeMapper;

import java.util.HashMap;
import java.util.Map;

import static com.sun.jna.Library.OPTION_FUNCTION_MAPPER;
import static com.sun.jna.Library.OPTION_TYPE_MAPPER;

/**
 * Windows Utilities for the Password Safe
 */
public class WindowsCryptUtils {
  /**
   * Unicode options for the libraries
   */
  static final Map<String, Object> UNICODE_OPTIONS = new HashMap<String, Object>();

  static {
    UNICODE_OPTIONS.put(OPTION_TYPE_MAPPER, W32APITypeMapper.UNICODE);
    UNICODE_OPTIONS.put(OPTION_FUNCTION_MAPPER, W32APIFunctionMapper.UNICODE);
  }

  /**
   * The private constructor
   */
  private WindowsCryptUtils() {
  }

  /**
   * Protect the specified byte range
   *
   * @param data the data to protect
   * @return the the protected form the data
   */
  public static byte[] protect(byte[] data) throws MasterPasswordUnavailableException {
    Memory input = new Memory(data.length);
    input.write(0, data, 0, data.length);
    Crypt32.DATA_BLOB in = new Crypt32.DATA_BLOB();
    in.cbData = new W32API.DWORD(data.length);
    in.pbData = input;
    Crypt32.DATA_BLOB out = new Crypt32.DATA_BLOB();
    out.pbData = Pointer.NULL;
    Crypt32 crypt = Crypt32.INSTANCE;
    Kernel32 kernel = Kernel32.INSTANCE;
    boolean rc = crypt.CryptProtectData(in, "Master Key", Pointer.NULL, Pointer.NULL, Pointer.NULL, new W32API.DWORD(0), out);
    if (!rc) {
      W32API.DWORD drc = kernel.GetLastError();
      throw new MasterPasswordUnavailableException("CryptProtectData failed: " + drc.intValue());
    }
    else {
      byte[] output = new byte[out.cbData.intValue()];
      out.pbData.read(0, output, 0, output.length);
      kernel.LocalFree(out.pbData);
      return output;
    }
  }

  /**
   * Unprotect the specified byte range
   *
   * @param data the data to protect
   * @return the the protected form the data
   */
  public static byte[] unprotect(byte[] data) throws MasterPasswordUnavailableException {
    Memory input = new Memory(data.length);
    input.write(0, data, 0, data.length);
    Crypt32.DATA_BLOB in = new Crypt32.DATA_BLOB();
    in.cbData = new W32API.DWORD(data.length);
    in.pbData = input;
    Crypt32.DATA_BLOB out = new Crypt32.DATA_BLOB();
    out.pbData = Pointer.NULL;
    Crypt32 crypt = Crypt32.INSTANCE;
    Kernel32 kernel = Kernel32.INSTANCE;
    boolean rc = crypt.CryptUnprotectData(in, Pointer.NULL, Pointer.NULL, Pointer.NULL, Pointer.NULL, new W32API.DWORD(0), out);
    if (!rc) {
      W32API.DWORD drc = kernel.GetLastError();
      throw new MasterPasswordUnavailableException("CryptProtectData failed: " + drc.intValue());
    }
    else {
      byte[] output = new byte[out.cbData.intValue()];
      out.pbData.read(0, output, 0, output.length);
      kernel.LocalFree(out.pbData);
      return output;
    }
  }

  /**
   * Windows cryptography functions
   */
  public interface Crypt32 extends StdCallLibrary {
    Crypt32 INSTANCE = (Crypt32)Native.loadLibrary("Crypt32", Crypt32.class, UNICODE_OPTIONS);

    /*
  BOOL WINAPI CryptProtectData(
    __in      DATA_BLOB *pDataIn,
    __in      LPCWSTR szDataDescr,
    __in      DATA_BLOB *pOptionalEntropy,
    __in      PVOID pvReserved,
    __in_opt  CRYPTPROTECT_PROMPTSTRUCT *pPromptStruct,
    __in      DWORD dwFlags,
    __out     DATA_BLOB *pDataOut
  );
     */


    boolean CryptProtectData(DATA_BLOB pDataIn,
                             String szDataDescr,
                             Pointer pOptionalEntropy,
                             Pointer pvReserved,
                             Pointer pPromptStruct,
                             W32API.DWORD dwFlags,
                             DATA_BLOB pDataOut);


    /*
  BOOL WINAPI CryptUnprotectData(
    __in       DATA_BLOB *pDataIn,
    __out_opt  LPWSTR *ppszDataDescr,
    __in_opt   DATA_BLOB *pOptionalEntropy,
    __in       PVOID pvReserved,
    __in_opt   CRYPTPROTECT_PROMPTSTRUCT *pPromptStruct,
    __in       DWORD dwFlags,
    __out      DATA_BLOB *pDataOut
  );

     */

    boolean CryptUnprotectData(DATA_BLOB pDataIn,
                               Pointer ppszDataDescr,
                               Pointer pOptionalEntropy,
                               Pointer pvReserved,
                               Pointer pPromptStruct,
                               W32API.DWORD dwFlags,
                               DATA_BLOB pDataOut);


    /**
     * Data holder
     */
    class DATA_BLOB extends Structure implements Structure.ByReference {
      public W32API.DWORD cbData;
      public Pointer pbData;
    }

  }

  /**
   * Wrapper for windows kernel functions
   */
  public interface Kernel32 extends StdCallLibrary {
    /**
     * The loaded library instance
     */
    Kernel32 INSTANCE = (Kernel32)Native.loadLibrary("Kernel32", Kernel32.class, UNICODE_OPTIONS);

    /*
    HLOCAL WINAPI LocalFree(
      HLOCAL hMem
    );*/

    Pointer LocalFree(Pointer hMem);
    /*

  DWORD WINAPI GetLastError(void);*/

    W32API.DWORD GetLastError();
  }
}
