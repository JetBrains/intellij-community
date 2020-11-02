// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

#include "COM_guard.h"
#include "winshellintegration/COM_errors.h" // errors::throwCOMException
#include <Objbase.h>                        // CoInitializeEx, CoUninitialize

namespace intellij::ui::win::jni
{
    // ================================================================================================================
    //  COMGuard::Impl
    // ================================================================================================================

    struct COMGuard::Impl final
    {
        explicit Impl(DWORD initFlags) noexcept(false)
        {
            if (const auto comResult = CoInitializeEx(nullptr, initFlags); comResult != S_OK)
            {
                // S_FALSE means the COM library is already initialized on this thread
                if (comResult != S_FALSE)
                    errors::throwCOMException(
                        comResult,
                        "CoInitializeEx failed",
                        __func__,
                        "intellij::ui::win::COMGuard::Impl"
                    );
            }
        }

        ~Impl() noexcept
        {
            CoUninitialize();
        }
    };


    // ================================================================================================================
    //  COMGuard
    // ================================================================================================================

    COMGuard::COMGuard(DWORD initFlags) noexcept(false)
        : impl_(std::make_shared<Impl>(initFlags))
    {}


    COMGuard::COMGuard(const COMGuard&) noexcept = default;

    COMGuard::COMGuard(COMGuard&& other) noexcept
        : COMGuard(static_cast<const COMGuard&>(other)) // avoid "empty" state of some instance of COMGuard
    {}


    COMGuard& COMGuard::operator=(const COMGuard&) noexcept = default;

    COMGuard& COMGuard::operator=(COMGuard&& rhs) noexcept
    {
        *this = static_cast<const COMGuard&>(rhs);      // avoid "empty" state of some instance of COMGuard
        return *this;
    }


    COMGuard::~COMGuard() noexcept = default;

} // namespace intellij::ui::win::jni
