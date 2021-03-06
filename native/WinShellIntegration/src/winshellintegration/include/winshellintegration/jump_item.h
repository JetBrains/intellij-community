// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/**
 * intellij::ui::win::JumpItem class implementation.
 *
 * See below for the documentation.
 *
 * @author Nikita Provotorov
 */

#ifndef WINSHELLINTEGRATION_JUMPITEM_H
#define WINSHELLINTEGRATION_JUMPITEM_H

#include "winapi.h"                 // IShellItemW, CComPtr
#include "COM_is_initialized.h"     // COMIsInitializedInThisThreadTag
#include <filesystem>               // std::filesystem::path


namespace intellij::ui::win
{

    // TODO: docs
    class JumpItem
    {
    public: // nested types
        using SharedNativeHandle = CComPtr<IShellItem>;

    public: // ctors/dtor
        JumpItem(std::filesystem::path path, COMIsInitializedInThisThreadTag) noexcept(false);

        /// non-copyable
        JumpItem(const JumpItem& src) = delete;
        JumpItem(JumpItem&& src) noexcept;

    public: // assignments
        /// non-copyable
        JumpItem& operator=(const JumpItem& rhs) = delete;
        JumpItem& operator=(JumpItem&& rhs) noexcept;

    public: // getters
        [[nodiscard]] SharedNativeHandle shareNativeHandle(COMIsInitializedInThisThreadTag) const noexcept(false);

        [[nodiscard]] const std::filesystem::path& getPath() const noexcept;

    private:
        [[nodiscard]] static CComPtr<IShellItem> createHandleFrom(
            const std::filesystem::path& path,
            COMIsInitializedInThisThreadTag
        ) noexcept(false);

    private:
        CComPtr<IShellItem> handle_;
        std::filesystem::path path_;
    };

} // namespace intellij::ui::win

#endif // ndef WINSHELLINTEGRATION_JUMPITEM_H
