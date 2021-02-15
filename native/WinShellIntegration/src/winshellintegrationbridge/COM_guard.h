// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/**
 * RAII wrapper around CoInitializeEx, CoUninitialize.
 *
 * @author Nikita Provotorov
 */

#ifndef WINSHELLINTEGRATIONBRIDGE_COM_GUARD_H
#define WINSHELLINTEGRATIONBRIDGE_COM_GUARD_H

#ifndef WIN32_LEAN_AND_MEAN
    #define WIN32_LEAN_AND_MEAN
#endif // ndef WIN32_LEAN_AND_MEAN
#include <Windows.h>    // DWORD


namespace intellij::ui::win::jni
{

    /// RAII wrapper around CoInitializeEx, CoUninitialize
    class COMGuard
    {
    public: // ctors/dtor
        /// Initializes the COM library via CoInitializeEx function.
        ///
        /// @param[in] initFlags - see possible values at CoInitializeEx's MSDN
        ///                         (https://docs.microsoft.com/en-us/windows/win32/api/combaseapi/nf-combaseapi-coinitializeex)
        ///
        /// @exception std::system_error - if CoInitializeEx failed
        /// @exception other exceptions from std namespace - in case of some internal errors
        explicit COMGuard(DWORD initFlags) noexcept(false);

        COMGuard(const COMGuard&) = delete;
        COMGuard(COMGuard&&) = delete;

        ~COMGuard() noexcept;

    public: // assignments
        COMGuard& operator=(const COMGuard&) = delete;
        COMGuard& operator=(COMGuard&&) = delete;

    private:
        const DWORD initFlags_;
    };

} // namespace intellij::ui::win::jni

#endif // ndef WINSHELLINTEGRATIONBRIDGE_COM_GUARD_H
