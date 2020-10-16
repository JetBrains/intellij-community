// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/**
 * RAII wrapper around CoInitializeEx, CoUninitialize.
 *
 * @author Nikita Provotorov
 */

#ifndef WINJUMPLISTBRIDGE_COM_GUARD_H
#define WINJUMPLISTBRIDGE_COM_GUARD_H

#include "winapi.h" // DWORD, CoInitializeEx, CoUninitialize
#include <memory>   // std::shared_ptr


namespace intellij::ui::win
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

        COMGuard(const COMGuard&) noexcept;
        /// @warning COPIES the passed object, DOES NOT MOVE it (for avoiding "empty" COMGuard instances)
        COMGuard(COMGuard&&) noexcept;

        ~COMGuard() noexcept;

    public: // assignments
        COMGuard& operator=(const COMGuard&) noexcept;
        /// @warning COPIES the passed object, DOES NOT MOVE it (for avoiding "empty" COMGuard instances)
        COMGuard& operator=(COMGuard&&) noexcept;

    private:
        struct Impl;

        std::shared_ptr<Impl> impl_;
    };

} // namespace intellij::ui::win


#endif // ndef WINJUMPLISTBRIDGE_COM_GUARD_H
