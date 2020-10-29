// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/**
 * Implementation of JNI methods of com.intellij.ui.win.RecentTasks class.
 *
 * @author Nikita Provotorov
 */

#include "com_intellij_ui_win_RecentTasks.h"
#include "COM_guard.h"      // intellij::ui::win::COMGuard
#include "jump_list.h"      // intellij::ui::win::JumpList
#include "application.h"    // intellij::ui::win::Application
#include <string_view>      // std::string_view
#include <thread>           // std::thread::id
#include <stdexcept>        // std::system_error, std::runtime_error, std::logic_error, std::exception
#include <optional>         // std::optional
#include <vector>           // std::vector
#include <sstream>          // std::stringstream
#include <cassert>          // assert


namespace intellij::ui::win::jni
{

    class RecentTasks final
    {
    private:
        struct PrivateCtorTag final { explicit PrivateCtorTag() noexcept = default; };

    public:
        template<typename... Ts>
        RecentTasks(PrivateCtorTag, Ts&&... args)
            : RecentTasks(std::forward<Ts>(args)...)
        {}

    public:
        static void initialize(JNIEnv* jEnv, jclass jClass, jstring jAppId) noexcept;

        static void addTasksNativeForCategory(
            JNIEnv* jEnv,
            jclass jClass,
            jstring jCategoryName,
            jobjectArray jTasks
        ) noexcept;

        static jstring getShortenPath(JNIEnv* jEnv, jclass jClass, jstring jGeneralPath) noexcept;

        static void clearNative(JNIEnv* jEnv, jclass jClass) noexcept;

    private:
        static std::optional<RecentTasks>& accessStorage() noexcept(false);
        static RecentTasks& getInstance() noexcept(false);

    private: // exception handling
        static void handleException(
            const std::system_error& exception,
            JNIEnv* jEnv,
            std::string_view pass_func_here /* __func */
        ) noexcept;

        static void handleException(
            const std::runtime_error& exception,
            JNIEnv* jEnv,
            std::string_view pass_func_here /* __func */
        ) noexcept;

        static void handleException(
            const std::logic_error& exception,
            JNIEnv* jEnv,
            std::string_view pass_func_here /* __func */
        ) noexcept;

        static void handleException(
            const std::exception& exception,
            JNIEnv* jEnv,
            std::string_view pass_func_here /* __func */
        ) noexcept;

        static void handleUnknownException(JNIEnv* jEnv, std::string_view pass_func_here /* __func */) noexcept;

    private:
        static constexpr std::string_view thisCtxName = "intellij::ui::win::jni::RecentTasks";

    private: // impl
        void addTasksNativeForCategoryImpl(
                JNIEnv* jEnv,
                jclass jClass,
                jstring jCategoryName,
                jobjectArray jTasks
        ) noexcept(false);

        jstring getShortenPathImpl(JNIEnv* jEnv, jclass jClass, jstring jGeneralPath) noexcept(false);

        void clearNativeImpl(JNIEnv* jEnv, jclass jClass) noexcept(false);

    private:
        RecentTasks() noexcept(false);

    private:
        const COMGuard comGuard_;
    };

} // namespace intellij::ui::win::jni


/*
 * Class:     com_intellij_ui_win_RecentTasks
 * Method:    initialize
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_com_intellij_ui_win_RecentTasks_initialize(
    JNIEnv* jEnv,
    jclass jClass,
    jstring jAppId)
{
    return (void)intellij::ui::win::jni::RecentTasks::initialize(jEnv, jClass, jAppId);
}

/*
 * Class:     com_intellij_ui_win_RecentTasks
 * Method:    addTasksNativeForCategory
 * Signature: (Ljava/lang/String;[Lcom/intellij/ui/win/Task;)V
 */
JNIEXPORT void JNICALL Java_com_intellij_ui_win_RecentTasks_addTasksNativeForCategory(
    JNIEnv* jEnv,
    jclass jClass,
    jstring jCategoryName,
    jobjectArray jTasks)
{
    return (void)intellij::ui::win::jni::RecentTasks::addTasksNativeForCategory(jEnv, jClass, jCategoryName, jTasks);
}

/*
 * Class:     com_intellij_ui_win_RecentTasks
 * Method:    getShortenPath
 * Signature: (Ljava/lang/String;)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_com_intellij_ui_win_RecentTasks_getShortenPath(
    JNIEnv* jEnv,
    jclass jClass,
    jstring jGeneralPath)
{
    return intellij::ui::win::jni::RecentTasks::getShortenPath(jEnv, jClass, jGeneralPath);
}

/*
 * Class:     com_intellij_ui_win_RecentTasks
 * Method:    clearNative
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_com_intellij_ui_win_RecentTasks_clearNative(
    JNIEnv* jEnv,
    jclass jClass)
{
    return (void)intellij::ui::win::jni::RecentTasks::clearNative(jEnv, jClass);
}


namespace intellij::ui::win::jni
{

    namespace
    {

        void ensureJNINoErrors(JNIEnv& jEnv) noexcept(false)
        {
            if (auto err = jEnv.ExceptionOccurred(); err != nullptr)
                throw err;
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

            jEnv->GetStringRegion(jStr, 0, resultLength, reinterpret_cast<jchar *>(result.data()));
            ensureJNINoErrors(*jEnv);

            result.resize(resultLength);

            return result;
        }

    } // namespace



    RecentTasks::RecentTasks() noexcept(false)
        : comGuard_(COINIT_APARTMENTTHREADED | COINIT_DISABLE_OLE1DDE | COINIT_SPEED_OVER_MEMORY /*NOLINT*/)
    {
        (void)Application::getInstance();
    }


    void RecentTasks::initialize(
        JNIEnv* jEnv,
        [[maybe_unused]] jclass jClass,
        [[maybe_unused]] jstring jAppId) noexcept
    {
        try
        {
            assert( (jEnv != nullptr) );

            auto& storage = accessStorage();

            if (storage.has_value())
                throw std::logic_error("intellij::ui::win::jni::RecentTasks has already been initialized");

            const auto appId = jStringToWideString(jEnv, jAppId);
            //Application::getInstance().setAppUserModelId(appId);

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


    void RecentTasks::addTasksNativeForCategory(
        JNIEnv* jEnv,
        jclass jClass,
        jstring jCategoryName,
        jobjectArray jTasks) noexcept
    {
        try
        {
            return (void)getInstance().addTasksNativeForCategoryImpl(jEnv, jClass, jCategoryName, jTasks);
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

    void RecentTasks::addTasksNativeForCategoryImpl(    // NOLINT(readability-convert-member-functions-to-static)
        JNIEnv* jEnv,
        [[maybe_unused]] jclass jClass,
        jstring jCategoryName,
        jobjectArray jTasks) noexcept(false)
    {
        assert( (jEnv != nullptr) );

        ensureJNINoErrors(*jEnv);

        auto categoryName = jStringToWideString(jEnv, jCategoryName);

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

            // TODO: jEnv->DeleteLocalRef ?

            nativeTasks.emplace_back(
                JumpTask::startBuilding(std::move(nativeTaskPath),
                                        std::move(nativeTaskDescription))
                                        .setApplicationArguments(std::move(nativeTaskArgs))
                                        .buildTask(COM_IS_INITIALIZED_IN_THIS_THREAD)
            );
        }

        for (const auto& task : nativeTasks)
            Application::getInstance().registerRecentlyUsed(task, COM_IS_INITIALIZED_IN_THIS_THREAD);
    }


    jstring RecentTasks::getShortenPath(
        JNIEnv* jEnv,
        jclass jClass,
        jstring jGeneralPath) noexcept
    {
        try
        {
            return getInstance().getShortenPathImpl(jEnv, jClass, jGeneralPath);
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

        return nullptr;
    }

    jstring RecentTasks::getShortenPathImpl(
        JNIEnv* jEnv,
        [[maybe_unused]] jclass jClass,
        jstring jGeneralPath) noexcept(false)
    {
        assert( (jEnv != nullptr) );

        ensureJNINoErrors(*jEnv);

        const WideString generalPath = jStringToWideString(jEnv, jGeneralPath);

        auto length = GetShortPathNameW(generalPath.c_str(), nullptr, 0);
        if (length < 1)
            throw std::system_error{
                static_cast<int>(GetLastError()),
                std::system_category(),
                "GetShortPathNameW(..., nullptr, 0) failed"
            };

        WideString result;
        result.resize(static_cast<WideString::size_type>(length), 0);

        length = GetShortPathNameW( generalPath.c_str(), result.data(), static_cast<DWORD>(result.length()) );
        if (length < 1)
            throw std::system_error{
                static_cast<int>(GetLastError()),
                std::system_category(),
                "GetShortPathNameW(..., result.data, result.length) failed"
            };

        result.resize(result.length() - 1);

        static_assert(
            sizeof(WideString::value_type) == sizeof(jchar),
            "Implementation relies on sizeof(WideString::value_type) == sizeof(jchar)"
        );

        auto jResult = jEnv->NewString(
            reinterpret_cast<const jchar*>(result.c_str()),
            static_cast<jsize>(result.length())
        );

        ensureJNINoErrors(*jEnv);

        return jResult;
    }


    void RecentTasks::clearNative(
        JNIEnv* jEnv,
        jclass jClass) noexcept
    {
        try
        {
            return (void)getInstance().clearNativeImpl(jEnv, jClass);
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

    void RecentTasks::clearNativeImpl(
        JNIEnv* jEnv,
        [[maybe_unused]] jclass jClass) noexcept(false)
    {
        assert( (jEnv != nullptr) );

        ensureJNINoErrors(*jEnv);

        //Application::getInstance().deleteJumpList(COM_IS_INITIALIZED_IN_THIS_THREAD);
        Application::getInstance().clearRecentsAndFrequents(COM_IS_INITIALIZED_IN_THIS_THREAD);
    }


    std::optional<RecentTasks>& RecentTasks::accessStorage() noexcept(false)
    {
        static const auto initializerThreadId = std::this_thread::get_id();

        if (initializerThreadId != std::this_thread::get_id())
            throw std::logic_error{ "Try to access to the storage from a non-initializer thread" };

        static std::optional<RecentTasks> result;

        return result;
    }

    RecentTasks& RecentTasks::getInstance() noexcept(false)
    {
        auto& storage = accessStorage();

        if (!storage.has_value())
            throw std::logic_error{ "Instance of RecentTasks has not yet been initialized" };

        return *storage;
    }


    void RecentTasks::handleException(
        const std::system_error& exception,
        JNIEnv* jEnv,
        std::string_view pass_func_here /* __func */) noexcept
    {
        try
        {
            assert( (jEnv != nullptr) );

            if (jEnv->ExceptionCheck() == JNI_TRUE)
                return;

            std::stringstream description;

            description << "Caught std::system_error in \"" << thisCtxName << "::" << pass_func_here << "\" with code "
                        << exception.code() << " meaning \"" << exception.what() << '\"';

            jEnv->ThrowNew(
                jEnv->FindClass("java/lang/RuntimeException"),
                description.str().c_str()
            );
        }
        catch (...)
        {
            jEnv->ThrowNew(
                jEnv->FindClass("java/lang/RuntimeException"),
                "RecentTasks::handleException: failed to handle std::system_error exception"
            );
        }
    }

    void RecentTasks::handleException(
        const std::runtime_error& exception,
        JNIEnv* jEnv,
        std::string_view pass_func_here) noexcept
    {
        try
        {
            assert( (jEnv != nullptr) );

            if (jEnv->ExceptionCheck() == JNI_TRUE)
                return;

            std::stringstream description;

            description << "Caught std::runtime_error in \"" << thisCtxName << "::" << pass_func_here << "\" meaning \""
                        << exception.what() << '\"';

            jEnv->ThrowNew(
                jEnv->FindClass("java/lang/RuntimeException"),
                description.str().c_str()
            );
        }
        catch (...)
        {
            jEnv->ThrowNew(
                jEnv->FindClass("java/lang/RuntimeException"),
                "RecentTasks::handleException: failed to handle std::runtime_error exception"
            );
        }
    }

    void RecentTasks::handleException(
        const std::logic_error& exception,
        JNIEnv* jEnv,
        std::string_view pass_func_here) noexcept
    {
        try
        {
            assert( (jEnv != nullptr) );

            if (jEnv->ExceptionCheck() == JNI_TRUE)
                return;

            std::stringstream description;

            description << "Caught std::logic_error in \"" << thisCtxName << "::" << pass_func_here << "\" meaning \""
                        << exception.what() << '\"';

            jEnv->ThrowNew(
                    jEnv->FindClass("java/lang/RuntimeException"),
                    description.str().c_str()
            );
        }
        catch (...)
        {
            jEnv->ThrowNew(
                jEnv->FindClass("java/lang/RuntimeException"),
                "RecentTasks::handleException: failed to handle std::logic_error exception"
            );
        }
    }

    void RecentTasks::handleException(
        const std::exception& exception,
        JNIEnv* jEnv,
        std::string_view pass_func_here) noexcept
    {
        try
        {
            assert( (jEnv != nullptr) );

            if (jEnv->ExceptionCheck() == JNI_TRUE)
                return;

            std::stringstream description;

            description << "Caught std::exception in \"" << thisCtxName << "::" << pass_func_here << "\" meaning \""
                        << exception.what() << '\"';

            jEnv->ThrowNew(
                jEnv->FindClass("java/lang/RuntimeException"),
                description.str().c_str()
            );
        }
        catch (...)
        {
            jEnv->ThrowNew(
                jEnv->FindClass("java/lang/RuntimeException"),
                "RecentTasks::handleException: failed to handle std::exception exception"
            );
        }
    }

    void RecentTasks::handleUnknownException(JNIEnv* jEnv, std::string_view pass_func_here /* __func */) noexcept
    {
        try
        {
            assert( (jEnv != nullptr) );

            if (jEnv->ExceptionCheck() == JNI_TRUE)
                return;

            std::stringstream description;

            description << "Caught an unknown exception in \"" << thisCtxName << "::" << pass_func_here << '\"';

            jEnv->ThrowNew(
                jEnv->FindClass("java/lang/RuntimeException"),
                description.str().c_str()
            );
        }
        catch (...)
        {
            jEnv->ThrowNew(
                jEnv->FindClass("java/lang/RuntimeException"),
                "RecentTasks::handleException: failed to handle an unknown exception"
            );
        }
    }

} // namespace intellij::ui::win::jni
