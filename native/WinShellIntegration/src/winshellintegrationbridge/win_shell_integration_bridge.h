// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

#ifndef WINSHELLINTEGRATION_WIN_SHELL_INTEGRATION_BRIDGE_H
#define WINSHELLINTEGRATION_WIN_SHELL_INTEGRATION_BRIDGE_H

#include <jni.h>
#include "COM_guard.h"  // COMGuard
#include <optional>     // std::optional
#include <string_view>  // std::string_view


/**
 * Implementation of JNI methods of com.intellij.ui.win.WinShellIntegration class.
 *
 * @author Nikita Provotorov
 */

namespace intellij::ui::win::jni
{

    class WinShellIntegrationBridge final
    {
    public:
        static void initialize(JNIEnv* jEnv, jobject jThis) noexcept;

    public:
        static void setAppUserModelId(JNIEnv* jEnv, jobject jThis, jstring jAppUserModelId) noexcept;

        static void clearRecentTasksList(JNIEnv* jEnv, jobject jThis) noexcept;

        static void setRecentTasksList(
            JNIEnv* jEnv,
            jobject jThis,
            jobjectArray jTasks
        ) noexcept;

    private:
        static std::optional<WinShellIntegrationBridge>& accessStorage() noexcept(false);
        static WinShellIntegrationBridge& getInstance() noexcept(false);

    private: // exception handling
        template<typename E>
        static void handleException(
            const E& exception,
            JNIEnv* jEnv,
            std::string_view pass_func_here /* __func__ */
        ) noexcept;

        static void handleUnknownException(JNIEnv* jEnv, std::string_view pass_func_here /* __func__ */) noexcept;

    private:
        static constexpr std::string_view thisCtxName = "intellij::ui::win::jni::WinShellIntegrationBridge";

    private: // impl
        void clearRecentTasksListImpl(JNIEnv* jEnv, jobject jThis) noexcept(false);

        void setRecentTasksListImpl(
            JNIEnv* jEnv,
            jobject jThis,
            jobjectArray jTasks
        ) noexcept(false);

    private:
        struct PrivateCtorTag;

    public:
        // It's public only by implementation reasons
        template<typename... Ts>
        explicit WinShellIntegrationBridge(PrivateCtorTag, Ts&&... args);

    private:
        WinShellIntegrationBridge() noexcept(false);

    private:
        [[maybe_unused]] const COMGuard comGuard_;
    };

} // namespace intellij::ui::win::jni

#endif // ndef WINSHELLINTEGRATION_WIN_SHELL_INTEGRATION_BRIDGE_H
