// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

#include "win_shell_integration_bridge.h"
#include "winshellintegration.h"            // intellij::ui::win::*
#include <utility>                          // std::forward
#include <thread>                           // std::thread::id, std::this_thread::get_id
#include <stdexcept>                        // std::system_error, std::runtime_error, std::logic_error, std::exception
#include <vector>                           // std::vector
#include <sstream>                          // std::stringstream
#include <type_traits>                      // std::decay_t, std::is_base_of
#include <filesystem>                       // std::filesystem::path
#include <cassert>                          // assert


namespace intellij::ui::win::jni
{

    // ================================================================================================================
    //  Some helpers
    // ================================================================================================================

    namespace
    {

        void ensureJNINoErrors(JNIEnv& jEnv) noexcept(false)
        {
            if (auto err = jEnv.ExceptionOccurred(); err != nullptr)
                throw err; // NOLINT(misc-throw-by-value-catch-by-reference,hicpp-exception-baseclass)
        }


        WideString jStringToWideString(JNIEnv *jEnv, jstring jStr)
        {
            static_assert(
                sizeof(jchar) == sizeof(WideString::value_type),
                "Implementation relies on sizeof(jchar) == sizeof(WideString::value_type)"
            );

            const auto resultLength = jEnv->GetStringLength(jStr);

            WideString result;

            result.resize(resultLength + 1, 0);

            jEnv->GetStringRegion(jStr, 0, resultLength, reinterpret_cast<jchar*>(result.data()));
            ensureJNINoErrors(*jEnv);

            result.resize(resultLength);

            return result;
        }


        std::string&& cleanExceptionDescription(std::string&& description)
        {
            // remove \r\n and \n at the end of the description
            while (!description.empty())
            {
                if (description.back() != '\n')
                    break;

                description.pop_back();

                if (description.empty())
                    break;

                if (description.back() == '\r')
                    description.pop_back();
            }

            // remove \r\n and \n at the start of the description
            std::string::size_type prefixToRemoveLength = 0;
            while (prefixToRemoveLength < description.size())
            {
                if ( (description[prefixToRemoveLength] != '\r') && (description[prefixToRemoveLength] != '\n') )
                    break;

                ++prefixToRemoveLength;
            }
            description.erase(0, prefixToRemoveLength);

            return std::move(description);
        }

    } // namespace


    // ================================================================================================================
    //  WinShellIntegrationBridge
    // ================================================================================================================

    struct WinShellIntegrationBridge::PrivateCtorTag final { explicit PrivateCtorTag() noexcept = default; };


    template<typename... Ts>
    WinShellIntegrationBridge::WinShellIntegrationBridge(PrivateCtorTag, Ts&&... args)
        : WinShellIntegrationBridge(std::forward<Ts>(args)...)
    {}

    WinShellIntegrationBridge::WinShellIntegrationBridge() noexcept(false)
        : comGuard_(COINIT_APARTMENTTHREADED | COINIT_DISABLE_OLE1DDE | COINIT_SPEED_OVER_MEMORY /* NOLINT(hicpp-signed-bitwise) */)
    {
        (void)Application::getInstance();
    }


    void WinShellIntegrationBridge::initialize(
        JNIEnv* jEnv,
        [[maybe_unused]] jobject jThis) noexcept
    {
        try
        {
            assert( (jEnv != nullptr) );

            auto& storage = accessStorage();

            if (storage.has_value())
                throw std::logic_error("intellij::ui::win::jni::WinShellIntegrationBridge has already been initialized");

            storage.emplace(PrivateCtorTag{});
        }
        catch (const jthrowable& javaErrWillBeThrown)
        {
            jEnv->DeleteLocalRef(javaErrWillBeThrown);
        }
        catch (const std::system_error& err)
        {
            return (void)handleException(err, jEnv, __func__);
        }
        catch (const std::runtime_error& err)
        {
            return (void)handleException(err, jEnv, __func__);
        }
        catch (const std::logic_error& err)
        {
            return (void)handleException(err, jEnv, __func__);
        }
        catch (const std::exception& err)
        {
            return (void)handleException(err, jEnv, __func__);
        }
        catch (...)
        {
            return (void)handleUnknownException(jEnv, __func__);
        }
    }


    void WinShellIntegrationBridge::setAppUserModelId(
        JNIEnv *jEnv,
        [[maybe_unused]] jobject jThis,
        jstring jAppUserModelId) noexcept
    {
        try
        {
            if (jAppUserModelId == nullptr)
                throw std::logic_error{ "jAppUserModelId == nullptr" };

            const auto appUserModelId = jStringToWideString(jEnv, jAppUserModelId);

            if (Application::getInstance().obtainAppUserModelId() != appUserModelId)
                Application::getInstance().setAppUserModelId(appUserModelId);
        }
        catch (const jthrowable& javaErrWillBeThrown)
        {
            jEnv->DeleteLocalRef(javaErrWillBeThrown);
        }
        catch (const std::system_error& err)
        {
            handleException(err, jEnv, __func__);
        }
        catch (const std::runtime_error& err)
        {
            handleException(err, jEnv, __func__);
        }
        catch (const std::logic_error& err)
        {
            handleException(err, jEnv, __func__);
        }
        catch (const std::exception& err)
        {
            handleException(err, jEnv, __func__);
        }
        catch (...)
        {
            handleUnknownException(jEnv, __func__);
        }
    }


    void WinShellIntegrationBridge::clearRecentTasksList(
        JNIEnv* jEnv,
        jobject jThis) noexcept
    {
        try
        {
            return (void)getInstance().clearRecentTasksListImpl(jEnv, jThis);
        }
        catch (const jthrowable& javaErrWillBeThrown)
        {
            jEnv->DeleteLocalRef(javaErrWillBeThrown);
        }
        catch (const std::system_error& err)
        {
            handleException(err, jEnv, __func__);
        }
        catch (const std::runtime_error& err)
        {
            handleException(err, jEnv, __func__);
        }
        catch (const std::logic_error& err)
        {
            handleException(err, jEnv, __func__);
        }
        catch (const std::exception& err)
        {
            handleException(err, jEnv, __func__);
        }
        catch (...)
        {
            handleUnknownException(jEnv, __func__);
        }
    }

    void WinShellIntegrationBridge::clearRecentTasksListImpl( // NOLINT(readability-convert-member-functions-to-static)
        JNIEnv* jEnv,
        [[maybe_unused]] jobject jThis) noexcept(false)
    {
        assert( (jEnv != nullptr) );

        ensureJNINoErrors(*jEnv);

        Application::getInstance().clearRecentsAndFrequents(COM_IS_INITIALIZED_IN_THIS_THREAD);
    }


    void WinShellIntegrationBridge::setRecentTasksList(
        JNIEnv* jEnv,
        jobject jThis,
        jobjectArray jTasks) noexcept
    {
        try
        {
            return (void)getInstance().setRecentTasksListImpl(jEnv, jThis, jTasks);
        }
        catch (const jthrowable& javaErrWillBeThrown)
        {
            jEnv->DeleteLocalRef(javaErrWillBeThrown);
        }
        catch (const std::system_error& err)
        {
            handleException(err, jEnv, __func__);
        }
        catch (const std::runtime_error& err)
        {
            handleException(err, jEnv, __func__);
        }
        catch (const std::logic_error& err)
        {
            handleException(err, jEnv, __func__);
        }
        catch (const std::exception& err)
        {
            handleException(err, jEnv, __func__);
        }
        catch (...)
        {
            handleUnknownException(jEnv, __func__);
        }
    }

    void WinShellIntegrationBridge::setRecentTasksListImpl( // NOLINT(readability-convert-member-functions-to-static)
        JNIEnv* jEnv,
        [[maybe_unused]] jobject jThis,
        jobjectArray jTasks) noexcept(false)
    {
        assert( (jEnv != nullptr) );

        ensureJNINoErrors(*jEnv);

        const jsize tasksCount = jEnv->GetArrayLength(jTasks);

        std::vector<JumpTask> nativeTasks;
        nativeTasks.reserve(tasksCount);

        for (jsize i = 0; i < tasksCount; ++i)
        {
            auto jTask = jEnv->GetObjectArrayElement(jTasks, i);
            ensureJNINoErrors(*jEnv);

            auto jTaskClass = jEnv->GetObjectClass(jTask);
            ensureJNINoErrors(*jEnv);

            auto pathFieldId = jEnv->GetFieldID(jTaskClass, "path", "Ljava/lang/String;");
            ensureJNINoErrors(*jEnv);

            auto argsFieldId = jEnv->GetFieldID(jTaskClass, "args", "Ljava/lang/String;");
            ensureJNINoErrors(*jEnv);

            auto descriptionFieldId = jEnv->GetFieldID(jTaskClass, "description", "Ljava/lang/String;");
            ensureJNINoErrors(*jEnv);

            auto jTaskPath  = static_cast<jstring>(jEnv->GetObjectField(jTask, pathFieldId));
            ensureJNINoErrors(*jEnv);

            auto jTaskArgs  = static_cast<jstring>(jEnv->GetObjectField(jTask, argsFieldId));
            ensureJNINoErrors(*jEnv);

            auto jTaskDescription  = static_cast<jstring>(jEnv->GetObjectField(jTask, descriptionFieldId));
            ensureJNINoErrors(*jEnv);

            auto nativeTaskPath = jStringToWideString(jEnv, jTaskPath);
            auto nativeTaskArgs = jStringToWideString(jEnv, jTaskArgs);
            auto nativeTaskDescription = jStringToWideString(jEnv, jTaskDescription);

            // cleanup

            jEnv->DeleteLocalRef(jTaskDescription);
            ensureJNINoErrors(*jEnv);

            jEnv->DeleteLocalRef(jTaskArgs);
            ensureJNINoErrors(*jEnv);

            jEnv->DeleteLocalRef(jTaskPath);
            ensureJNINoErrors(*jEnv);

            jEnv->DeleteLocalRef(jTaskClass);
            ensureJNINoErrors(*jEnv);

            jEnv->DeleteLocalRef(jTask);
            ensureJNINoErrors(*jEnv);

            nativeTasks.emplace_back(
                JumpTask::startBuilding(std::move(nativeTaskPath), std::move(nativeTaskDescription))
                          .setApplicationArguments(std::move(nativeTaskArgs))
                          .buildTask(COM_IS_INITIALIZED_IN_THIS_THREAD)
            );
        }

        for (auto iter = nativeTasks.crbegin(); iter != nativeTasks.crend(); ++iter)
        {
            const auto& task = *iter;
            Application::getInstance().registerRecentlyUsed(task, COM_IS_INITIALIZED_IN_THIS_THREAD);
        }
    }


    std::optional<WinShellIntegrationBridge>& WinShellIntegrationBridge::accessStorage() noexcept(false)
    {
        static const auto initializerThreadId = std::this_thread::get_id();

        if (initializerThreadId != std::this_thread::get_id())
            throw std::logic_error{ "Try to access to the storage from a non-initializer thread" };

        static std::optional<WinShellIntegrationBridge> result;

        return result;
    }

    WinShellIntegrationBridge& WinShellIntegrationBridge::getInstance() noexcept(false)
    {
        auto& storage = accessStorage();

        if (!storage.has_value())
            throw std::logic_error{ "Instance of WinShellIntegrationBridge has not yet been initialized" };

        return *storage;
    }


    template<typename E>
    void WinShellIntegrationBridge::handleException(
        const E& exception,
        JNIEnv* jEnv,
        std::string_view funcName /* __func__ */) noexcept
    {
        using ExceptionType = std::decay_t<E>;

        constexpr std::string_view exceptionTypeName =
            std::is_base_of_v<std::system_error, ExceptionType>     ? "std::system_error"
            : std::is_base_of_v<std::runtime_error, ExceptionType>  ? "std::runtime_error"
            : std::is_base_of_v<std::logic_error, ExceptionType>    ? "std::logic_error"
            : std::is_base_of_v<std::exception, ExceptionType>      ? "std::exception"
                                                                    : "<failed-to-deduce-exception-type>";

        constexpr const char* catchDescription =
            std::is_base_of_v<std::system_error, ExceptionType>     ? "WinShellIntegrationBridge::handleException: failed to handle std::system_error exception"
            : std::is_base_of_v<std::runtime_error, ExceptionType>  ? "WinShellIntegrationBridge::handleException: failed to handle std::runtime_error exception"
            : std::is_base_of_v<std::logic_error, ExceptionType>    ? "WinShellIntegrationBridge::handleException: failed to handle std::logic_error exception"
            : std::is_base_of_v<std::exception, ExceptionType>      ? "WinShellIntegrationBridge::handleException: failed to handle std::exception exception"
                                                                    : "WinShellIntegrationBridge::handleException: failed to handle <failed-to-deduce-exception-type> exception";

        try
        {
            assert( (jEnv != nullptr) );

            if (jEnv->ExceptionCheck() == JNI_TRUE)
                return;

            std::stringstream description;

            description << "Caught " << exceptionTypeName << " in \"" << thisCtxName << "::" << funcName << '\"';
            if constexpr (std::is_base_of_v<std::system_error, ExceptionType>)
            {
                description << " with code " << exception.code();
            }
            description << " meaning \"" << cleanExceptionDescription(exception.what()) << '\"';

            jEnv->ThrowNew(
                jEnv->FindClass("java/lang/RuntimeException"),
                description.str().c_str()
            );
        }
        catch (...)
        {
            jEnv->ThrowNew(
                jEnv->FindClass("java/lang/RuntimeException"),
                catchDescription
            );
        }
    }

    void WinShellIntegrationBridge::handleUnknownException(
        JNIEnv* jEnv,
        std::string_view funcName /* __func__ */) noexcept
    {
        try
        {
            assert( (jEnv != nullptr) );

            if (jEnv->ExceptionCheck() == JNI_TRUE)
                return;

            std::stringstream description;

            description << "Caught an unknown exception in \"" << thisCtxName << "::" << funcName << '\"';

            jEnv->ThrowNew(
                jEnv->FindClass("java/lang/RuntimeException"),
                description.str().c_str()
            );
        }
        catch (...)
        {
            jEnv->ThrowNew(
                jEnv->FindClass("java/lang/RuntimeException"),
                "WinShellIntegrationBridge::handleUnknownException: failed to handle an unknown exception"
            );
        }
    }

} // namespace intellij::ui::win::jni
