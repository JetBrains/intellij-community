// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

#include "winshellintegration/jump_task.h"
#include "winshellintegration/COM_errors.h" // errors::throwCOMException
#include <string_view>                      // std::string_view
#include <type_traits>                      // std::is_same_v
#include <cassert>                          // assert


namespace intellij::ui::win
{

    // ================================================================================================================
    //  JumpTask
    // ================================================================================================================

    JumpTask::JumpTask(CComPtr<IShellLinkW>&& nativeHandle, COMIsInitializedInThisThreadTag) noexcept
        : handle_(std::move(nativeHandle))
    {}

    JumpTask::JumpTask(JumpTask &&other) noexcept = default;


    JumpTask::BuildSession JumpTask::startBuilding(std::filesystem::path appPath, WideString title) noexcept(false)
    {
        return { std::move(appPath), std::move(title) };
    }


    JumpTask::~JumpTask() noexcept = default;


    JumpTask& JumpTask::operator=(JumpTask&& lhs) = default;


    JumpTask::SharedNativeHandle JumpTask::shareNativeHandle(COMIsInitializedInThisThreadTag) const noexcept(false)
    {
        return handle_;
    }


    // ================================================================================================================
    //  JumpTask::BuildSession
    // ================================================================================================================

    static constexpr std::string_view buildSessionCtxName = "intellij::ui::JumpTask::BuildSession";


    JumpTask::BuildSession::BuildSession(std::filesystem::path appPath, WideString title) noexcept(false)
        : appPath_(std::move(appPath))
        , title_(std::move(title))
    {
        if (appPath_.empty())
            throw std::runtime_error{
                "Attempting to construct JumpTask::BuildSession with an empty path to the application"
            };

        if (title_.empty())
            throw std::runtime_error{
                "Attempting to construct JumpTask::BuildSession with an empty title"
            };
    }

    //JumpTask::BuildSession::BuildSession(BuildSession&&) noexcept = default;
    //JumpTask::BuildSession& JumpTask::BuildSession::operator=(BuildSession&&) noexcept = default;


    JumpTask::BuildSession& JumpTask::BuildSession::setApplicationArguments(WideString allArgs) noexcept
    {
        appArguments_ = std::move(allArgs);
        return *this;
    }

    JumpTask::BuildSession& JumpTask::BuildSession::setApplicationWorkingDirectory(std::filesystem::path wdPath) noexcept
    {
        appWorkDir_ = std::move(wdPath);
        return *this;
    }

    JumpTask::BuildSession& JumpTask::BuildSession::setDescription(WideString description) noexcept
    {
        description = std::move(description);
        return *this;
    }


    JumpTask JumpTask::BuildSession::buildTask(COMIsInitializedInThisThreadTag com) const noexcept(false)
    {
        auto result = createJumpTask(com);

        copyAppPathToJumpTask(result);
        copyAppArgsToJumpTask(result);
        copyWorkDirToJumpTask(result);
        copyTitleToJumpTask(result);
        copyDescriptionToJumpTask(result);

        return result;
    }


    JumpTask JumpTask::BuildSession::createJumpTask(COMIsInitializedInThisThreadTag com) const noexcept(false)
    {
        CComPtr<IShellLinkW> nativeHandle;

        auto comResult = nativeHandle.CoCreateInstance(
            // request IShellLink instance
            CLSID_ShellLink,

            // ...that is not owned by IUnknown s
            nullptr,

            // The code that creates and manages objects of this class
            //  is a DLL that runs in the same process as the caller of
            //  the function specifying the class context.
            CLSCTX_INPROC_SERVER
        );

        if (comResult != S_OK)
            errors::throwCOMException(comResult, "CoCreateInstance failed", __func__, buildSessionCtxName);

        return JumpTask{ std::move(nativeHandle), com };
    }

    void JumpTask::BuildSession::copyAppPathToJumpTask(JumpTask& task) const noexcept(false)
    {
        static_assert(std::is_same_v<std::filesystem::path::value_type, WCHAR>, "std::filesystem::path must use WCHARs");

        if (const auto comResult = task.handle_->SetPath(appPath_.c_str()); comResult != S_OK)
            errors::throwCOMException(comResult, "IShellLinkW::SetPath failed", __func__, buildSessionCtxName);
    }

    void JumpTask::BuildSession::copyAppArgsToJumpTask(JumpTask& task) const noexcept(false)
    {
        if (appArguments_.has_value())
        {
            if (const auto comResult = task.handle_->SetArguments(appArguments_->c_str()); comResult != S_OK)
                errors::throwCOMException(comResult, "IShellLinkW::SetArguments failed", __func__, buildSessionCtxName);
        }
    }

    void JumpTask::BuildSession::copyWorkDirToJumpTask(JumpTask& task) const noexcept(false)
    {
        static_assert(std::is_same_v<std::filesystem::path::value_type, WCHAR>, "std::filesystem::path must use WCHARs");

        if (appWorkDir_.has_value())
        {
            if (const auto comResult = task.handle_->SetWorkingDirectory(appWorkDir_->c_str()); comResult != S_OK)
                errors::throwCOMException(comResult, "IShellLinkW::SetWorkingDirectory failed", __func__, buildSessionCtxName);
        }
    }

    void JumpTask::BuildSession::copyTitleToJumpTask(JumpTask& task) const noexcept(false)
    {
        constexpr std::string_view funcName = __func__;

        const auto properties = [&task, funcName] {
            CComPtr<IPropertyStore> result;
            if (const auto comResult = task.handle_.QueryInterface(&result); comResult != S_OK)
                errors::throwCOMException(
                    comResult,
                    "QueryInterface failed",
                    funcName,
                    buildSessionCtxName
                );
            return result;
        }();
        assert( (properties != nullptr) );

        const struct TitleProperty
        {
            PROPVARIANT value{};

            explicit TitleProperty(const WideString& title) noexcept(false)
            {
                if (const auto comResult = InitPropVariantFromString(title.c_str(), &value); comResult != S_OK)
                    errors::throwCOMException(
                        comResult,
                        "InitPropVariantFromString failed",
                        __func__,
                        buildSessionCtxName
                    );
            }

            ~TitleProperty() noexcept
            {
                assert( (PropVariantClear(&value) == S_OK) );
            }
        } titleProperty(title_);

        if (const auto comResult = properties->SetValue(PKEY_Title, titleProperty.value); comResult != S_OK)
            errors::throwCOMException(comResult, "IPropertyStore::SetValue failed", funcName, buildSessionCtxName);

        if (const auto comResult = properties->Commit(); comResult != S_OK)
            errors::throwCOMException(comResult, "IPropertyStore::Commit failed", funcName, buildSessionCtxName);
    }

    void JumpTask::BuildSession::copyDescriptionToJumpTask(JumpTask& task) const noexcept(false)
    {
        if (description_.has_value())
        {
            if (const auto comResult = task.handle_->SetDescription(description_->c_str()); comResult != S_OK)
                errors::throwCOMException(comResult, "IShellLinkW::SetDescription failed", __func__, buildSessionCtxName);
        }
    }

} // namespace intellij::ui::win
