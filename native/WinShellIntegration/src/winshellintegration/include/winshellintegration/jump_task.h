// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/**
 * intellij::ui::win::JumpTask and intellij::ui::win::JumpTask::BuildSession classes implementation.
 *
 * See below for the documentation.
 *
 * @author Nikita Provotorov
 */

#ifndef WINSHELLINTEGRATION_JUMPTASK_H
#define WINSHELLINTEGRATION_JUMPTASK_H

#include "winapi.h"                 // IShellLinkW, CComPtr
#include "COM_is_initialized.h"     // COMIsInitializedInThisThreadTag
#include "wide_string.h"            // WideString, WideStringView
#include <filesystem>               // std::filesystem
#include <optional>                 // std::optional
#include <utility>                  // std::forward


namespace intellij::ui::win
{

    // TODO: docs
    class JumpTask
    {
    public: // nested types
        using SharedNativeHandle = CComPtr<IShellLinkW>;

        // see declaration below
        class BuildSession;

    public: // ctors/dtor
        /// JumpTask is non-copyable.
        JumpTask(const JumpTask&) = delete;

        JumpTask(JumpTask&& other) noexcept;


        /// Starts the process of JumpTask building.
        ///
        /// @param[in] appPath - the path to the application to execute. Must not be empty.
        /// @param[in] title - the text displayed for the task in the Jump List. Must not be empty;
        ///                    the task will be unclickable otherwise.
        ///
        /// @exception std::runtime_error in case of empty one of the passed parameters
        [[nodiscard]] static BuildSession startBuilding(
            std::filesystem::path appPath,
            WideString title
        ) noexcept(false);


        ~JumpTask() noexcept;

    public: // assignments
        /// JumpTask is non-copyable.
        JumpTask& operator=(const JumpTask&) = delete;

        JumpTask& operator=(JumpTask&& lhs);

    public: // getters
        [[nodiscard]] SharedNativeHandle shareNativeHandle(COMIsInitializedInThisThreadTag) const noexcept(false);

    private:
        /// Use JumpTask::startBuilding to create a task
        explicit JumpTask(CComPtr<IShellLinkW>&& nativeHandle, COMIsInitializedInThisThreadTag) noexcept;

    private:
        CComPtr<IShellLinkW> handle_;
    };


    // TODO: docs
    class JumpTask::BuildSession
    {
        friend class JumpTask;

    public: // ctors/dtor
        // non-copyable
        BuildSession(const BuildSession&) = delete;
        // non-movable
        BuildSession(BuildSession&&) = delete;

    public: // assignments
        // non-copyable
        BuildSession& operator=(const BuildSession&) = delete;
        // non-movable
        BuildSession& operator=(BuildSession&&) = delete;

    public: // modifiers of optional parameters
        /// Sets the arguments passed to the application on startup.
        /// @returns *this
        BuildSession& setApplicationArguments(WideString allArgs) noexcept;
        /// Conditionally sets the arguments passed to the application on startup.
        ///
        /// @param[in] condition - if true then this invokes setApplicationArguments({std::forward<Ts>(allArgs)...});
        ///                        nothing will be performed otherwise.
        ///
        /// @returns *this
        template<typename... Ts>
        BuildSession& setApplicationArgumentsIf(bool condition, Ts&&... allArgs)
        {
            if (condition)
                return setApplicationArguments({std::forward<Ts>(allArgs)...});
            return *this;
        }

        /// Sets the working directory of the application on startup.
        /// @returns *this
        BuildSession& setApplicationWorkingDirectory(std::filesystem::path wdPath) noexcept;
        /// Conditionally sets the working directory of the application on startup.
        ///
        /// @param[in] condition - if true then this invokes setApplicationWorkingDirectory({std::forward<Ts>(allArgs)...});
        ///                        nothing will be performed otherwise.
        ///
        /// @returns *this
        template<typename... Ts>
        BuildSession& setApplicationWorkingDirectoryIf(bool condition, Ts&&... allArgs)
        {
            if (condition)
                return setApplicationWorkingDirectory({std::forward<Ts>(allArgs)...});
            return *this;
        }

        /// Sets the text displayed in the tooltip for the tasks in the Jump List.
        /// @returns *this
        BuildSession& setDescription(WideString description) noexcept;
        /// Conditionally sets the text displayed in the tooltip for the tasks in the Jump List.
        ///
        /// @param[in] condition - if true then this invokes setDescription({std::forward<Ts>(allArgs)...});
        ///                        nothing will be performed otherwise.
        ///
        /// @returns *this
        template<typename... Ts>
        BuildSession& setDescriptionIf(bool condition, Ts&&... allArgs)
        {
            if (condition)
                return setDescription({std::forward<Ts>(allArgs)...});
            return *this;
        }

    public: // getters
        [[nodiscard]] JumpTask buildTask(COMIsInitializedInThisThreadTag) const noexcept(false);

    private:
        /// Use JumpTask::startBuilding method
        ///
        /// @exception std::runtime_error in case of empty one of the passed parameters
        BuildSession(std::filesystem::path appPath, WideString title) noexcept(false);

        //BuildSession(BuildSession&&) noexcept;
        //BuildSession& operator=(BuildSession&&) noexcept;

    private: // helpers
        [[nodiscard]] JumpTask createJumpTask(COMIsInitializedInThisThreadTag) const noexcept(false);
        void copyAppPathToJumpTask(JumpTask& task) const noexcept(false);
        void copyAppArgsToJumpTask(JumpTask& task) const noexcept(false);
        void copyWorkDirToJumpTask(JumpTask& task) const noexcept(false);
        void copyTitleToJumpTask(JumpTask& task) const noexcept(false);
        void copyDescriptionToJumpTask(JumpTask& task) const noexcept(false);

    private:
        const std::filesystem::path appPath_;
        const WideString title_;
        std::optional<WideString> appArguments_;
        std::optional<std::filesystem::path> appWorkDir_;
        std::optional<WideString> description_;
    };

} // namespace intellij::ui::win

#endif // ndef WINSHELLINTEGRATION_JUMPTASK_H
