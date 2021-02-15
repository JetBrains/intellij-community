// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

#include "COM_guard.h"
#include "winshellintegration/COM_errors.h" // errors::throwCOMException
#include <Objbase.h>                        // CoInitializeEx, CoUninitialize

namespace intellij::ui::win::jni
{
    // ================================================================================================================
    //  COMGuard
    // ================================================================================================================

    COMGuard::COMGuard(DWORD initFlags) noexcept(false)
        : initFlags_(initFlags)
    {
        if (const auto comResult = CoInitializeEx(nullptr, initFlags_); comResult != S_OK)
        {
            // S_FALSE means the COM library is already initialized on this thread
            if (comResult != S_FALSE)
                errors::throwCOMException(
                    comResult,
                    "CoInitializeEx failed",
                    __func__,
                    "intellij::ui::win::COMGuard"
                );
        }
    }

    COMGuard::~COMGuard() noexcept
    {
        CoUninitialize();
    }

} // namespace intellij::ui::win::jni
