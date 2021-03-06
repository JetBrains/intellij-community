// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/**
 *  Stuffs for integration COM calls return codes (HRESULT) into std::system_error exception.
 *
 *  Intended use case:
 *      const auto comResult = SomeCOMCall(params...);
 *      if (comResult != S_OK)
 *          errors::throwCOMException(comResult, "SomeCOMCall failed", __func__);
 *
 *  Or more flexible:
 *      const auto comResult = SomeCOMCall(params...);
 *      if (comResult != S_OK)
 *          throw std::runtime_error{ errors::COMResultHandle{comResult}, "my custom description" };
 *
 *  @author Nikita Provotorov
 */

#ifndef WINSHELLINTEGRATION_COM_ERRORS_H
#define WINSHELLINTEGRATION_COM_ERRORS_H

#include "winapi.h"     // HRESULT
#include <system_error> // std::error_code, std::error_category, std::is_error_code_enum
#include <type_traits>  // std::true_type
#include <string_view>  // std::string_view


namespace intellij::ui::win::errors
{
    /// Transparent wrapper around HRESULT
    class COMResultHandle final
    {
    public:
        explicit COMResultHandle(HRESULT hResult) noexcept
            : hResult_(hResult)
        {}

        bool operator==(HRESULT rhs) const noexcept { return (hResult_ == rhs); }
        bool operator!=(HRESULT rhs) const noexcept { return (!(*this == rhs)); }

        [[nodiscard]] HRESULT getHandle() const noexcept { return hResult_; }
        explicit operator HRESULT() const noexcept { return getHandle(); }

    private:
        HRESULT hResult_;
    };

    const std::error_category& getCOMErrorCategory() noexcept;


    // this is for a code like:
    //  const auto hResult = SomeCOMCall(params...);
    //  if (comErrC != S_OK)
    //      throw std::system_error(errors::COMResultHandle{hResult});
    //
    // (it will be found through ADL)
    std::error_code make_error_code(COMResultHandle comResultHandle) noexcept;


    // TODO: docs
    [[noreturn]] void throwCOMException(
        HRESULT comReturned,
        std::string_view description,
        // __func__
        std::string_view pass_func_here) noexcept(false);

    // TODO: docs
    [[noreturn]] void throwCOMException(
        HRESULT comReturned,
        std::string_view description,
        // __func__
        std::string_view pass_func_here,
        std::string_view classOrNamespaceName) noexcept(false);

} // namespace intellij::ui::win::errors

// Enables SFINAE overload intellij::ui::win::errors::make_error_code of std::make_error_code
template<>
struct std::is_error_code_enum<intellij::ui::win::errors::COMResultHandle> : std::true_type {};


#endif // ndef WINSHELLINTEGRATION_COM_ERRORS_H
