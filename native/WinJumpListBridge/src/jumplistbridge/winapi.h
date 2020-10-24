// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/**
 * Encapsulation of all WinAPI headers used by the project.
 *
 * @author Nikita Provotorov
 */

// TODO: make all defines in CMake

#ifndef WINJUMPLISTBRIDGE_WINAPI_H
#define WINJUMPLISTBRIDGE_WINAPI_H

// Force Unicode version of WinAPI.
// It is necessary for:
//  * fixing of WinAPI SHARDAPPIDINFOLINK structure.
#ifndef UNICODE
    #define UNICODE
#endif // ndef UNICODE
#ifndef _UNICODE
    #define _UNICODE
#endif // ndef _UNICODE

// Exclude rarely-used stuff from Windows headers
#define WIN32_LEAN_AND_MEAN

// Make the project supports Windows 8 and later.
// See https://docs.microsoft.com/ru-ru/cpp/porting/modifying-winver-and-win32-winnt for more info.
#include <WinSDKVer.h>
#undef WINVER
#define WINVER          0x0602  // 0x0602 is Windows 8
#undef _WIN32_WINNT
#define _WIN32_WINNT    0x0602  // 0x0602 is Windows 8
#include <sdkddkver.h>

// Used WinAPI headers
#include <Windows.h>
#include <Shobjidl.h>       // COM
#include <Shlobj.h>         // SHAddToRecentDocs, SHCreateItemFromParsingName ; to link with Shell32.lib
#include <propsys.h>        // IPropertyStore
#include <Propidl.h>        // PROPVARIANT
#include <propkey.h>        // PKEY_Title
#include <propvarutil.h>    // InitPropVariantFromString

#endif // ndef WINJUMPLISTBRIDGE_WINAPI_H
