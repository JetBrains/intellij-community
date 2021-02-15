// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/**
 * intellij::ui::win::Application class implementation.
 *
 * See below for the documentation.
 *
 * @author Nikita Provotorov
 */

#ifndef WINSHELLINTEGRATION_APPLICATION_H
#define WINSHELLINTEGRATION_APPLICATION_H

#include "app_user_model_id.h"      // AppUserModelId
#include "COM_is_initialized.h"     // COMIsInitializedInThisThreadTag
#include <optional>                 // std::optional
#include <filesystem>               // std::filesystem::path
#include <utility>                  // std::pair


struct ICustomDestinationList;  // forward declaration


namespace intellij::ui::win
{

    class JumpItem;             // forward declaration
    class JumpTask;             // forward declaration
    class JumpList;             // forward declaration


    /// @brief Application class is the entry point for all platform-specific calls
    ///        that explicitly affect the current application.
    ///
    /// For now it provides access to the following features:
    ///     * managing AppUserModelID property of the application (see documentation for the Application::UserModelId type);
    ///     * managing list of the recently used documents, directories, actions by user;
    ///     * managing Windows custom Jump Lists.
    ///
    /// @note Some member functions require the COM library to be initialized in the invoking thread.
    ///       All of them receive an additional tag parameter of the intellij::ui::win::COMIsInitializedInThisThreadTag type.
    class Application final
    {
    public: // nested types
        /// Application User Model IDs (AppUserModelIDs) are used extensively by the taskbar
        ///     in Windows 7 and later systems to associate processes, files, and windows with a particular application.
        /// See https://docs.microsoft.com/en-us/windows/win32/shell/appids for detailed description
        using UserModelId = AppUserModelId;

    public: // ctors/dtor
        // non-copyable
        Application(const Application&) = delete;
        // non-movable
        Application(Application&&) = delete;

        static Application& getInstance() noexcept;

    public: // assignments
        // non-copyable
        Application& operator=(const Application&) = delete;
        // non-movable
        Application& operator=(Application&&) = delete;

    public: // modifiers
        /// @brief Specifies a unique application-defined Application User Model ID (AppUserModelID) that
        ///        identifies the current process to the taskbar.
        ///
        /// See docs for Application::UserModelId type for more info.
        ///
        /// @exception std::system_error in case of failed system call
        /// @exception exceptions derived from std::exception in case of some internal errors
        void setAppUserModelId(const UserModelId& appId) noexcept(false);

        /// @brief Notifies the system that an document has been accessed,
        ///        for the purposes of tracking those items used most recently and most frequently.
        ///
        /// @attention This method will work only if the application is registered at HKEY_CLASSES_ROOT/Applications.
        ///            See https://docs.microsoft.com/en-us/windows/win32/api/shlobj_core/nf-shlobj_core-shaddtorecentdocs
        ///            for detailed info about Recent items.
        ///
        /// @exception std::system_error in case of failed system call
        /// @exception exceptions derived from std::exception in case of some internal errors
        void registerRecentlyUsed(const std::filesystem::path& path) noexcept(false);

        /// @brief Notifies the system that an item has been accessed,
        ///        for the purposes of tracking those items used most recently and most frequently.
        ///
        /// @attention This method will work only if the application is registered at HKEY_CLASSES_ROOT/Applications.
        ///            See https://docs.microsoft.com/en-us/windows/win32/api/shlobj_core/nf-shlobj_core-shaddtorecentdocs
        ///            for detailed info about Recent items.
        ///
        /// @exception std::system_error in case of failed system call
        /// @exception exceptions derived from std::exception in case of some internal errors
        void registerRecentlyUsed(const JumpItem& recentJumpItem, COMIsInitializedInThisThreadTag) noexcept(false);

        /// @brief Notifies the system that an action has been accessed,
        ///        for the purposes of tracking those items used most recently and most frequently.
        ///
        /// @remark Recent JumpTasks are not added to the Windows Explorer's Recent folder,
        ///         although they are reflected in an application's Jump List (see
        ///         https://docs.microsoft.com/en-us/windows/win32/api/shlobj_core/nf-shlobj_core-shaddtorecentdocs
        ///         for more info).
        ///
        /// @exception std::system_error in case of failed system call
        /// @exception exceptions derived from std::exception in case of some internal errors
        void registerRecentlyUsed(const JumpTask& recentJumpTask, COMIsInitializedInThisThreadTag) noexcept(false);

        /// @brief Clears all usage data on all Recent and Frequent items
        ///        and also removes all items of Recent and Frequent categories of the Jump List.
        ///
        /// @exception std::system_error in case of failed system call
        /// @exception exceptions derived from std::exception in case of some internal errors
        void clearRecentsAndFrequents(COMIsInitializedInThisThreadTag) noexcept(false);

        /// Applies the passed Jump List to the current application. Includes call of deleteJumpList method.
        ///
        /// @exception std::system_error in case of failed system call
        /// @exception exceptions derived from std::exception in case of some internal errors
        void setJumpList(const JumpList& jumpList, COMIsInitializedInThisThreadTag) noexcept(false);

        /// Erase any custom Jump List set previously.
        ///
        /// @exception std::system_error in case of failed system call
        /// @exception exceptions derived from std::exception in case of some internal errors
        void deleteJumpList(COMIsInitializedInThisThreadTag) noexcept(false);

    public: // getters
        /// @brief Returns a unique application-defined Application User Model ID (AppUserModelID)
        ///        previously set by Application::setAppUserModelId method.
        ///
        /// See docs for Application::UserModelId type for more info about AppUserModelIDs.
        ///
        /// @returns empty optional if UserModelId was not set previously
        ///
        /// @exception std::system_error in case of failed system call
        /// @exception exceptions derived from std::exception in case of some internal errors
        [[nodiscard]] std::optional<UserModelId> obtainAppUserModelId() const noexcept(false);

    private:
        Application() noexcept;
        ~Application() noexcept;

    private: // helpers
        void refreshJumpListHandle() noexcept(false);

    private:
        std::pair< std::optional<UserModelId>, CComPtr<ICustomDestinationList> > idAndJumpListHandle_;
    };

} // namespace intellij::ui::win

#endif // ndef WINSHELLINTEGRATION_APPLICATION_H
