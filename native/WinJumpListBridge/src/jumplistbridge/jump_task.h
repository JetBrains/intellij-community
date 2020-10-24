// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/**
 * intellij::ui::win::JumpTask class implementation.
 *
 * See below for the documentation.
 *
 * @author Nikita Provotorov
 */

#ifndef WINJUMPLISTBRIDGE_JUMPTASK_H
#define WINJUMPLISTBRIDGE_JUMPTASK_H

#include "winapi.h"                 // IShellLinkW
#include "COM_is_initialized.h"     // COMIsInitializedInThisThreadTag
#include "wide_string.h"            // WideString, WideStringView
#include "COM_object_safe_ptr.h"    // COMObjectSafePtr
#include <filesystem>               // std::filesystem
#include <optional>                 // std::optional


namespace intellij::ui::win
{

    // TODO: docs
    // TODO: merge JumpTask::Builder into JumpTask
    class JumpTask
    {
    public: // nested types
        using SharedNativeHandle = COMObjectSafePtr<IShellLinkW>;

        // see declaration below
        class Builder;

    public: // ctors/dtor
        /// JumpTask is non-copyable.
        /// If you really need to copy a task, use JumpTask::Builder::takeSettingsFrom + JumpTask::Builder::buildTask
        JumpTask(const JumpTask&) = delete;

        /// @param [in,out] other - is in valid but unspecified state afterwards
        JumpTask(JumpTask&& other) noexcept;

        ~JumpTask() noexcept;

    public: // assignments
        /// JumpTask is non-copyable.
        /// If you really need to copy a task, use JumpTask::Builder::takeSettingsFrom + JumpTask::Builder::buildTask
        JumpTask& operator=(const JumpTask&) = delete;

        /// @param [in] lhs - is in valid but unspecified state afterwards
        JumpTask& operator=(JumpTask&& lhs);

    public: // getters
        [[nodiscard]] SharedNativeHandle shareNativeHandle(COMIsInitializedInThisThreadTag) const noexcept(false);

    private:
        /// Use JumpTask::Builder to create a task
        explicit JumpTask(COMObjectSafePtr<IShellLinkW>&& nativeHandle, COMIsInitializedInThisThreadTag) noexcept;

    private:
        COMObjectSafePtr<IShellLinkW> handle_;
    };


    // TODO: docs
    class JumpTask::Builder
    {
    public: // ctors/dtor
        /// @param appPath [in] - must be not empty. See setTasksApplicationPath method for more info.
        /// @param title [in] - must be not empty. See setTasksTitle method for more info.
        ///
        /// @exception std::runtime_error - if appPath is empty
        /// @exception std::runtime_error - if title is empty
        Builder(std::filesystem::path appPath, WideString title) noexcept(false);
        /// Copies all parameters of the passed JumpTask to *this
        Builder(const JumpTask& src) noexcept(false);

    public: // modifiers
        /// Copies all parameters of the passed JumpTask to *this
        Builder& takeSettingsFrom(const JumpTask& jumpTask, COMIsInitializedInThisThreadTag) noexcept(false);

        /// Sets the path to the application.
        /// Each JumpTask must have non-empty application path: what to execute otherwise?
        ///
        /// @exception std::runtime_error - if appPath is empty
        Builder& setTasksApplicationPath(std::filesystem::path appPath) noexcept(false);
        /// Sets the arguments passed to the application on startup.
        Builder& setTasksApplicationArguments(WideString allArgs) noexcept;
        /// Sets the working directory of the application on startup.
        Builder& setTasksApplicationWorkingDirectory(std::filesystem::path wdPath) noexcept;
        /// Sets the text displayed for the tasks in the Jump List.
        /// Each JumpTask must have non-empty title: it will be unclickable otherwise.
        ///
        /// @exception std::runtime_error - if title is empty
        Builder& setTasksTitle(WideString title) noexcept(false);
        /// Sets the text displayed in the tooltip for the tasks in the Jump List.
        Builder& setTasksDescription(WideString description) noexcept;

    public: // getters
        /// @throws std::system_error
        // TODO: docs
        [[nodiscard]] JumpTask buildTask(COMIsInitializedInThisThreadTag) const noexcept(false);

        /// Returns the path to the application.
        [[nodiscard]] const std::filesystem::path& getTasksApplicationPath() const noexcept;
        /// Returns the arguments passed to the application on startup.
        /// @returns nullptr if no arguments will be passed to the application on startup.
        ///          You can set the passing arguments via getTasksApplicationArguments method.
        [[nodiscard]] const WideString* getTasksApplicationArguments() const noexcept;
        /// Returns the working directory of the application on startup.
        /// @returns nullptr if the working directory will not be set (via setTasksWorkingDirectory).
        ///          You can set it via setTasksWorkingDirectory method.
        [[nodiscard]] const std::filesystem::path* getTasksApplicationWorkingDirectory() const noexcept;
        /// Returns the text displayed for the tasks in the Jump List.
        [[nodiscard]] WideStringView getTasksTitle() const noexcept;
        /// Returns the text displayed in the tooltip for the tasks in the Jump List.
        /// @returns nullptr if the tooltip text will not be set for the tasks.
        ///          You can set it via setTasksDescription method.
        [[nodiscard]] const WideString* getTasksDescription() const noexcept;

    private:
        [[nodiscard]] JumpTask createJumpTask(COMIsInitializedInThisThreadTag) const noexcept(false);
        const Builder& copyAppPathToJumpTask(JumpTask& task) const noexcept(false);
        const Builder& copyAppArgsToJumpTask(JumpTask& task) const noexcept(false);
        const Builder& copyWorkDirToJumpTask(JumpTask& task) const noexcept(false);
        const Builder& copyTitleToJumpTask(JumpTask& task) const noexcept(false);
        const Builder& copyDescriptionToJumpTask(JumpTask& task) const noexcept(false);

    private:
        std::filesystem::path appPath_;
        std::optional<WideString> appArguments_;
        std::optional<std::filesystem::path> appWorkDir_;
        WideString title_;
        std::optional<WideString> description_;
    };

} // namespace intellij::ui::win

#endif // ndef WINJUMPLISTBRIDGE_JUMPTASK_H
