// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/**
 * DLL entry point
 *
 * @author Nikita Provotorov
 */

#include <Windows.h>


BOOL WINAPI DllMain(
    [[maybe_unused]] HINSTANCE dllHandle,   // handle to DLL module
    [[maybe_unused]] DWORD entryReason,     // reason for calling function
    LPVOID                                  // reserved
)
{
    switch (entryReason)
    {
        case DLL_PROCESS_ATTACH:
            break;
        case DLL_THREAD_ATTACH:
            break;
        case DLL_THREAD_DETACH:
            break;
        case DLL_PROCESS_DETACH:
            break;
        default:
            break;
    }

    return TRUE;
}
