// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/**
 * All WinAPI headers used by the project.
 *
 * @author Nikita Provotorov
 */

#ifndef WINSHELLINTEGRATION_WINAPI_H
#define WINSHELLINTEGRATION_WINAPI_H

// Force Unicode version of WinAPI.
// It is necessary for:
//  * fix WinAPI SHARDAPPIDINFOLINK structure.
#ifndef UNICODE
    #define UNICODE
#endif // ndef UNICODE
#ifndef _UNICODE
    #define _UNICODE
#endif // ndef _UNICODE


// Exclude rarely-used stuff from Windows headers
#ifndef WIN32_LEAN_AND_MEAN
    #define WIN32_LEAN_AND_MEAN
#endif // ndef WIN32_LEAN_AND_MEAN


// Make the project supports Windows 8 and later.
// See https://docs.microsoft.com/ru-ru/cpp/porting/modifying-winver-and-win32-winnt for more info.
#ifndef WINSHELLINTEGRATION_WINVER
    #define WINSHELLINTEGRATION_WINVER 0x0602   // 0x0602 is the identifier of Windows 8
#endif // ndef WINSHELLINTEGRATION_WINVER

#include <WinSDKVer.h>
#ifndef WINVER
    #define WINVER WINSHELLINTEGRATION_WINVER
#endif // ndef WINVER
#if (WINVER < WINSHELLINTEGRATION_WINVER)
    #error "WINVER define is too small. Only 0x0602 (Windows 8) or greater are supported"
#endif // (WINVER < WINSHELLINTEGRATION_WINVER)

#ifndef _WIN32_WINNT
    #define _WIN32_WINNT WINSHELLINTEGRATION_WINVER
#endif // ndef _WIN32_WINNT
#if (_WIN32_WINNT < WINSHELLINTEGRATION_WINVER)
    #error "_WIN32_WINNT define is too small. Only 0x0602 (Windows 8) or greater are supported"
#endif // (_WIN32_WINNT < WINSHELLINTEGRATION_WINVER)
#include <sdkddkver.h>


// Used WinAPI headers
#include <Windows.h>
#include <Shobjidl.h>       // COM
#include <Shlobj.h>         // SHAddToRecentDocs, SHCreateItemFromParsingName ; to link with Shell32.lib
#include <propsys.h>        // IPropertyStore
#include <Propidl.h>        // PROPVARIANT
#include <propkey.h>        // PKEY_Title
#include <propvarutil.h>    // InitPropVariantFromString
#include <atlbase.h>        // CComPtr
#include <winerror.h>       // HRESULT_FROM_WIN32, ERROR_* WinAPI defines

#endif // ndef WINSHELLINTEGRATION_WINAPI_H
