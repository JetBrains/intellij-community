// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

#include "winshellintegration/COM_errors.h"
#include <string>                           // std::string
#include <array>                            // std::array
#include <charconv>                         // std::to_chars
#include <utility>                          // std::move


namespace intellij::ui::win::errors
{

    const std::error_category& getCOMErrorCategory() noexcept
    {
        struct Category final : std::error_category
        {
            [[nodiscard]] const char* name() const noexcept override { return "COM"; }

            [[nodiscard]] std::string message(int condition) const override
            {
                // TODO: return good string description instead of "HRESULT=..."

                const auto hResult = static_cast<std::make_unsigned<HRESULT>::type>(condition);

                std::array<char, 51> buf;   // NOLINT(cppcoreguidelines-pro-type-member-init)

                const auto [pastTheEnd, errC] = std::to_chars(&buf.front(), &buf.back(), hResult, 16);
                if ( errC != std::errc() )
                    return "HRESULT=<unknown>";

                std::string result = "HRESULT=0x";
                result.append(&buf.front(), pastTheEnd);

                return result;
            }
        };

        static const Category result;
        return result;
    }


    std::error_code make_error_code(COMResultHandle comResultHandle) noexcept
    {
        return { static_cast<int>(comResultHandle.getHandle()), getCOMErrorCategory() };
    }


    void throwCOMException(
        HRESULT comReturned,
        std::string_view description,
        std::string_view funcName) noexcept(false)
    {
        throwCOMException(comReturned, description, funcName, {});
    }

    void throwCOMException(
        HRESULT comReturned,
        std::string_view description,
        std::string_view funcName,
        std::string_view classOrNamespaceName) noexcept(false)
    {
        constexpr std::string_view atStr = " at ";
        constexpr std::string_view nameQualifier = "::";

        const auto reservingLength = description.length()
                                     + atStr.length()
                                     + classOrNamespaceName.length()
                                     + nameQualifier.length()
                                     + funcName.length()
                                     + 1; // '\0'

        std::string what;
        what.reserve(reservingLength);

        what.append(description)
            .append(atStr)
            .append(classOrNamespaceName)
            .append(nameQualifier)
            .append(funcName);

        throw std::system_error{COMResultHandle(comReturned), std::move(what)}; // NOLINT(performance-move-const-arg)
    }

} // namespace intellij::ui::win::errors
