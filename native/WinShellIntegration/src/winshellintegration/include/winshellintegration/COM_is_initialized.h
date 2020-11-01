// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/**
 * intellij::ui::win::COMIsInitializedInThisThreadTag struct implementation and
 * intellij::ui::win::COM_IS_INITIALIZED_IN_THIS_THREAD variable definition.
 *
 * See below for the documentation.
 *
 * @author Nikita Provotorov
 */

#ifndef WINSHELLINTEGRATION_COM_IS_INITIALIZED_H
#define WINSHELLINTEGRATION_COM_IS_INITIALIZED_H


namespace intellij::ui::win
{

    /// Tag to explicitly indicating the dependence of functions/classes on the COM library.
    struct COMIsInitializedInThisThreadTag
    {
        explicit constexpr COMIsInitializedInThisThreadTag(int) {}
    };

    inline constexpr COMIsInitializedInThisThreadTag COM_IS_INITIALIZED_IN_THIS_THREAD{42};

} // namespace intellij::ui::win

#endif // ndef WINSHELLINTEGRATION_COM_IS_INITIALIZED_H
