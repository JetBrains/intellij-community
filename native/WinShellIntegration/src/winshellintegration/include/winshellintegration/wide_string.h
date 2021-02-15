// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

#ifndef WINSHELLINTEGRATION_WIDE_STRING_H
#define WINSHELLINTEGRATION_WIDE_STRING_H

#include "winapi.h"     // WCHAR
#include <string>       // std::basic_string
#include <string_view>  // std::basic_string_view


namespace intellij::ui::win
{

    // Null-terminated
    using WideString = std::basic_string<WCHAR>;
    using WideStringView = std::basic_string_view<WideString::value_type>;

} // namespace intellij::ui::win

#endif // ndef WINSHELLINTEGRATION_WIDE_STRING_H
