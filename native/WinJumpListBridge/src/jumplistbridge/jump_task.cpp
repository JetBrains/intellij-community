// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

#include "jump_task.h"
#include "COM_errors.h" // errors::throwCOMException
#include <utility>      // std::move
#include <string_view>  // std::string_view
#include <type_traits>  // std::is_same_v
#include <cassert>      // assert


namespace intellij::ui::win
{

    // ================================================================================================================
    //  JumpTask
    // ================================================================================================================

    JumpTask::JumpTask(COMGuard comGuard, COMObjectSafePtr<IShellLinkW>&& nativeHandle) noexcept
        : comGuard_(std::move(comGuard))
        , handle_(std::move(nativeHandle))
    {}

    JumpTask::JumpTask(JumpTask &&other) noexcept = default;

    JumpTask::~JumpTask() noexcept = default;


    JumpTask& JumpTask::operator=(JumpTask&& lhs) = default;


    JumpTask::SharedNativeHandle JumpTask::shareNativeHandle() const noexcept(false)
    {
        return handle_;
    }


    // ================================================================================================================
    //  JumpTask::Builder
    // ================================================================================================================

    static constexpr std::string_view builderCtxStr = "intellij::ui::win::JumpTask::Builder";

    JumpTask::Builder::Builder(COMGuard comGuard, std::filesystem::path appPath, WideString title) noexcept(false)
        : comGuard_(std::move(comGuard))
        , appPath_(std::move(appPath))
        , title_(std::move(title))
    {
        if (appPath_.empty())
            throw std::runtime_error{
                "Attempting to construct JumpTask::Builder with an empty path to the application"
            };

        if (title_.empty())
            throw std::runtime_error{
                "Attempting to construct JumpTask::Builder with an empty title."
            };
    }

//    JumpTask::Builder::Builder(const JumpTask& src) noexcept(false)
//    {
//        (void)takeSettingsFrom(src);
//    }


    // TODO: implementation
    //JumpTask::Builder& JumpTask::Builder::takeSettingsFrom(const JumpTask& jumpTask) noexcept(false);


    JumpTask::Builder& JumpTask::Builder::setTasksApplicationPath(std::filesystem::path appPath) noexcept(false)
    {
        if (appPath.empty())
            throw std::runtime_error("Attempting to place empty application path to JumpTask::Builder");

        appPath_ = std::move(appPath);
        return *this;
    }

    JumpTask::Builder& JumpTask::Builder::setTasksApplicationArguments(WideString allArgs) noexcept
    {
        appArguments_ = std::move(allArgs);
        return *this;
    }

    JumpTask::Builder& JumpTask::Builder::setTasksApplicationWorkingDirectory(std::filesystem::path wdPath) noexcept
    {
        appWorkDir_ = std::move(wdPath);
        return *this;
    }

    JumpTask::Builder& JumpTask::Builder::setTasksTitle(WideString title) noexcept(false)
    {
        if (title.empty())
            throw std::runtime_error("Attempting to place empty title to JumpTask::Builder");

        title_ = std::move(title);
        return *this;
    }

    JumpTask::Builder& JumpTask::Builder::setTasksDescription(WideString description) noexcept
    {
        description_ = std::move(description);
        return *this;
    }


    JumpTask JumpTask::Builder::buildTask() const noexcept(false)
    {
        auto result = createJumpTask();

        this->copyAppPathToJumpTask(result)
              .copyAppArgsToJumpTask(result)
              .copyWorkDirToJumpTask(result)
              .copyTitleToJumpTask(result)
              .copyDescriptionToJumpTask(result);

        return result;
    }

    JumpTask JumpTask::Builder::createJumpTask() const noexcept(false)
    {
        IShellLinkW* nativeHandle = nullptr;

        auto comResult = CoCreateInstance(
            // request IShellLink instance
            CLSID_ShellLink,

            // ...that is not owned by IUnknown s
            nullptr,

            // The code that creates and manages objects of this class
            //  is a DLL that runs in the same process as the caller of
            //  the function specifying the class context.
            CLSCTX_INPROC_SERVER,

            // Request exactly Wide (not ANSI) version
            IID_IShellLinkW,

            reinterpret_cast<LPVOID*>(&nativeHandle)
        );

        if (comResult != S_OK)
            errors::throwCOMException(comResult, "CoCreateInstance failed", __func__, builderCtxStr);

        return JumpTask{ comGuard_, COMObjectSafePtr{nativeHandle} };
    }

    const JumpTask::Builder& JumpTask::Builder::copyAppPathToJumpTask(JumpTask& task) const noexcept(false)
    {
        static_assert(std::is_same_v<std::filesystem::path::value_type, WCHAR>, "std::filesystem::path must use WCHARs");

        if (const auto comResult = task.handle_->SetPath(appPath_.c_str()); comResult != S_OK)
            errors::throwCOMException(comResult, "IShellLinkW::SetPath failed", __func__, builderCtxStr);

        return *this;
    }

    const JumpTask::Builder& JumpTask::Builder::copyAppArgsToJumpTask(JumpTask& task) const noexcept(false)
    {
        if (appArguments_.has_value())
        {
            if (const auto comResult = task.handle_->SetArguments(appArguments_->c_str()); comResult != S_OK)
                errors::throwCOMException(comResult, "IShellLinkW::SetArguments failed", __func__, builderCtxStr);
        }

        return *this;
    }

    const JumpTask::Builder& JumpTask::Builder::copyWorkDirToJumpTask(JumpTask& task) const noexcept(false)
    {
        static_assert(std::is_same_v<std::filesystem::path::value_type, WCHAR>, "std::filesystem::path must use WCHARs");

        if (appWorkDir_.has_value())
        {
            if (const auto comResult = task.handle_->SetWorkingDirectory(appWorkDir_->c_str()); comResult != S_OK)
                errors::throwCOMException(comResult, "IShellLinkW::SetWorkingDirectory failed", __func__, builderCtxStr);
        }

        return *this;
    }

    const JumpTask::Builder& JumpTask::Builder::copyTitleToJumpTask(JumpTask& task) const noexcept(false)
    {
        const auto properties = task.handle_.COMCast<IPropertyStore>();
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
                            "JumpTask::Builder::copyTitleToJumpTask::TitleProperty::TitleProperty()"
                    );
            }

            ~TitleProperty() noexcept
            {
                assert( (PropVariantClear(&value) == S_OK) );
            }
        } titleProperty(title_);

        if (const auto comResult = properties->SetValue(PKEY_Title, titleProperty.value); comResult != S_OK)
            errors::throwCOMException(comResult, "IPropertyStore::Commit failed", __func__, builderCtxStr);

        if (const auto comResult = properties->Commit(); comResult != S_OK)
            errors::throwCOMException(comResult, "IPropertyStore::Commit failed", __func__, builderCtxStr);

        return *this;
    }

    const JumpTask::Builder& JumpTask::Builder::copyDescriptionToJumpTask(JumpTask& task) const noexcept(false)
    {
        if (description_.has_value())
        {
            if (const auto comResult = task.handle_->SetDescription(description_->c_str()); comResult != S_OK)
                errors::throwCOMException(comResult, "IShellLinkW::SetDescription failed", __func__, builderCtxStr);
        }

        return *this;
    }


    const std::filesystem::path& JumpTask::Builder::getTasksApplicationPath() const noexcept
    {
        return appPath_;
    }

    const WideString* JumpTask::Builder::getTasksApplicationArguments() const noexcept
    {
        if (!appArguments_.has_value())
            return nullptr;

        return (&(*appArguments_));
    }

    const std::filesystem::path* JumpTask::Builder::getTasksApplicationWorkingDirectory() const noexcept
    {
        if (!appWorkDir_.has_value())
            return nullptr;

        return (&(*appWorkDir_));
    }

    WideStringView JumpTask::Builder::getTasksTitle() const noexcept
    {
        return title_;
    }

    const WideString* JumpTask::Builder::getTasksDescription() const noexcept
    {
        if (!description_.has_value())
            return nullptr;

        return (&(*description_));
    }

} // namespace intellij::ui::win
