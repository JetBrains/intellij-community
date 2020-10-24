// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

#include "jump_item.h"


namespace intellij::ui::win
{
    static constexpr std::string_view jumpItemCtxName = "intellij::ui::win::JumpItem";


    JumpItem::JumpItem(std::filesystem::path path, COMIsInitializedInThisThreadTag com) noexcept(false)
        : handle_(createHandleFrom(path, com))
        , path_(std::move(path))
    {}

    JumpItem::JumpItem(JumpItem&& src) noexcept = default;


    JumpItem& JumpItem::operator=(JumpItem&& rhs) noexcept(false) = default;


    JumpItem::SharedNativeHandle JumpItem::shareNativeHandle(COMIsInitializedInThisThreadTag) const noexcept(false)
    {
        return handle_;
    }

    const std::filesystem::path& JumpItem::getPath() const noexcept
    {
        return path_;
    }


    COMObjectSafePtr<IShellItem> JumpItem::createHandleFrom(
        const std::filesystem::path& path,
        COMIsInitializedInThisThreadTag) noexcept(false)
    {
        static_assert(std::is_same_v<std::filesystem::path::value_type, WCHAR>, "std::filesystem::path must use WCHARs");

        IShellItem* result;

        if (const auto hr = SHCreateItemFromParsingName(path.c_str(), nullptr, IID_PPV_ARGS(&result)); hr != S_OK)
            errors::throwCOMException(hr, "SHCreateItemFromParsingName failed", __func__, jumpItemCtxName);

        return COMObjectSafePtr{result};
    }

} // namespace intellij::ui::win
