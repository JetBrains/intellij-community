// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

#include "winshellintegration/application.h"
#include "winshellintegration/winapi.h"         // SetCurrentProcessExplicitAppUserModelID,
                                                // GetCurrentProcessExplicitAppUserModelID,
                                                // COM,
                                                // Shell

#include "winshellintegration/COM_errors.h"     // errors::throwCOMException
#include "winshellintegration/jump_item.h"      // JumpItem
#include "winshellintegration/jump_task.h"      // JumpTask
#include "winshellintegration/jump_list.h"      // JumpList
#include <string_view>                          // std::string_view
#include <exception>                            // std::uncaught_exceptions
#include <stdexcept>                            // std::logic_error
#include <cassert>                              // assert


namespace intellij::ui::win
{

    // ================================================================================================================
    //  JumpListTransaction
    // ================================================================================================================

    namespace
    {

        class JumpListTransaction final
        {
        public:
            static constexpr std::string_view jumpListTransactionCtxName = "intellij::ui::win::Application::JumpListTransaction";

            UINT maxSlots;
            CComPtr<IObjectArray> removedObjects;

        public: // ctors/dtor
            explicit JumpListTransaction(ICustomDestinationList* jumpListHandle) noexcept(false)
                : handle_(nullptr)
                , maxSlots(0)
            {
                auto hr = jumpListHandle->BeginList(&maxSlots, IID_PPV_ARGS(&removedObjects));

                if (hr != S_OK)
                    errors::throwCOMException(
                        hr,
                        "ICustomDestinationList::BeginList failed",
                        __func__,
                        jumpListTransactionCtxName
                    );

                assert( (removedObjects != nullptr) );

                handle_ = jumpListHandle;
            }

            // non-copyable
            JumpListTransaction(const JumpListTransaction&) = delete;

            // non-movable
            JumpListTransaction(JumpListTransaction&&) = delete;


            ~JumpListTransaction() noexcept(false)
            {
                if (handle_ == nullptr)
                    return;

                auto *const localHandle = handle_;
                handle_ = nullptr;

                const auto hr = localHandle->AbortList();

                if (std::uncaught_exceptions() != 0)
                    assert( (hr == S_OK) );
                else if (hr != S_OK)
                    errors::throwCOMException(
                        hr,
                        "ICustomDestinationList::AbortList failed",
                        __func__,
                        jumpListTransactionCtxName
                    );
            }

        public:
            // non-copyable
            JumpListTransaction& operator=(const JumpListTransaction&) = delete;

            // non-movable
            JumpListTransaction& operator=(JumpListTransaction&&) = delete;

        public:
            void commit() noexcept(false)
            {
                if (handle_ == nullptr)
                    return;

                if (const auto hr = handle_->CommitList(); hr != S_OK)
                    errors::throwCOMException(
                        hr,
                        "ICustomDestinationList::CommitList failed",
                        __func__,
                        jumpListTransactionCtxName
                    );

                handle_ = nullptr;
            }

        public:
            [[nodiscard]] ICustomDestinationList* operator->() const noexcept {
                assert( (handle_ != nullptr) );
                return handle_;
            }

            [[nodiscard]] ICustomDestinationList& operator*() const noexcept {
                assert( (handle_ != nullptr) );
                return *handle_;
            }

        private:
            ICustomDestinationList* handle_;
        };

    } // namespace


    // ================================================================================================================
    //  Some helpers
    // ================================================================================================================

    namespace
    {

        CComPtr<IObjectCollection> createCategoryNativeContainer(
            const std::string_view callerFuncName,
            const std::string_view callerCtxName) noexcept(false)
        {
            CComPtr<IObjectCollection> result;

            auto hr = result.CoCreateInstance(
                CLSID_EnumerableObjectCollection,
                nullptr,
                CLSCTX_INPROC_SERVER
            );

            if (hr != S_OK)
                errors::throwCOMException(
                    hr,
                    "CoCreateInstance(CLSID_EnumerableObjectCollection) failed",
                    callerFuncName,
                    callerCtxName
                );

            assert( (result != nullptr) );

            return result;
        }

        void insertToCategoryNativeContainer(
            IObjectCollection& nativeContainer,
            const JumpList::value_type& item,
            const std::string_view callerFuncName,
            const std::string_view callerCtxName,
            COMIsInitializedInThisThreadTag com) noexcept(false)
        {
            std::visit(
                [&nativeContainer, callerFuncName, callerCtxName, com](const auto& unwrappedItem) {
                    auto hr = nativeContainer.AddObject(unwrappedItem.shareNativeHandle(com));
                    if (hr != S_OK)
                        errors::throwCOMException(
                            hr,
                            "IObjectCollection::AddObject failed",
                            callerFuncName,
                            callerCtxName
                        );
                },

                item
            );
        }


        [[nodiscard]] CComPtr<ICustomDestinationList> createJumpListHandle(
            const Application::UserModelId* appId,
            const std::string_view callerFuncName,
            const std::string_view callerCtxName) noexcept(false)
        {
            CComPtr<ICustomDestinationList> result = nullptr;

            auto hr = result.CoCreateInstance(
                CLSID_DestinationList,
                nullptr,
                CLSCTX_INPROC_SERVER
            );

            if (hr != S_OK)
                errors::throwCOMException(
                    hr,
                    "CoCreateInstance(CLSID_DestinationList) failed",
                    callerFuncName,
                    callerCtxName
                );

            assert( (result != nullptr) );

            if (appId != nullptr)
            {
                if (hr = result->SetAppID(appId->c_str()); hr != S_OK)
                    errors::throwCOMException(
                        hr,
                        "ICustomDestinationList::SetAppID failed",
                        callerFuncName,
                        callerCtxName
                    );
            }

            return result;
        }

        [[nodiscard]] CComPtr<IApplicationDestinations> createRecentsAndFrequentsHandle(
            const Application::UserModelId* appId,
            const std::string_view callerFuncName,
            const std::string_view callerCtxName) noexcept(false)
        {
            CComPtr<IApplicationDestinations> result = nullptr;

            auto hr = result.CoCreateInstance(
                CLSID_ApplicationDestinations,
                nullptr,
                CLSCTX_INPROC_SERVER
            );

            if (hr != S_OK)
                errors::throwCOMException(
                    hr,
                    "CoCreateInstance(CLSID_ApplicationDestinations) failed",
                    callerFuncName,
                    callerCtxName
                );

            assert( (result != nullptr) );

            if (appId != nullptr)
            {
                if (hr = result->SetAppID(appId->c_str()); hr != S_OK)
                    errors::throwCOMException(
                        hr,
                        "IApplicationDestinations::SetAppID failed",
                        callerFuncName,
                        callerCtxName
                    );
            }

            return result;
        }


        void appendCustomCategoryTo(
            ICustomDestinationList& jumpListHandle,
            const WideString& categoryName,
            const JumpList::CustomCategoryContainer& items,
            const std::string_view callerFuncName,
            const std::string_view callerCtxName,
            COMIsInitializedInThisThreadTag com) noexcept(false)
        {
            if (items.empty())
                return;

            const auto nativeContainer = createCategoryNativeContainer(callerFuncName, callerCtxName);

            for (const auto& item: items)
                insertToCategoryNativeContainer(*nativeContainer, item, callerFuncName, callerCtxName, com);

            if (const auto hr = jumpListHandle.AppendCategory(categoryName.c_str(), nativeContainer); hr != S_OK)
                errors::throwCOMException(
                    hr,
                    "ICustomDestinationList::AppendCategory failed",
                    callerFuncName,
                    callerCtxName
                );
        }

        void appendUserTasksTo(
            ICustomDestinationList& jumpListHandle,
            const JumpList::UserTasksCategoryContainer& items,
            const std::string_view callerFuncName,
            const std::string_view callerCtxName,
            COMIsInitializedInThisThreadTag com) noexcept(false)
        {
            if (items.empty())
                return;

            const auto nativeContainer = createCategoryNativeContainer(callerFuncName, callerCtxName);

            for (const auto& item: items)
                insertToCategoryNativeContainer(*nativeContainer, item, callerFuncName, callerCtxName, com);

            if (const auto hr = jumpListHandle.AddUserTasks(nativeContainer); hr != S_OK)
                errors::throwCOMException(
                    hr,
                    "ICustomDestinationList::AddUserTasks failed",
                    callerFuncName,
                    callerCtxName
                );
        }

    } // namespace


    // ================================================================================================================
    //  Application
    // ================================================================================================================

    static constexpr std::string_view applicationCtxName = "intellij::ui::win::Application";


    Application::Application() noexcept = default;

    Application::~Application() noexcept = default;


    Application& Application::getInstance() noexcept
    {
        static Application instance;
        return instance;
    }


    void Application::setAppUserModelId(const UserModelId& appId) noexcept(false)
    {
        if (const auto hr = SetCurrentProcessExplicitAppUserModelID(appId.c_str()); hr != S_OK)
            errors::throwCOMException(
                hr,
                "SetCurrentProcessExplicitAppUserModelID failed",
                __func__,
                applicationCtxName
            );
    }


    void Application::registerRecentlyUsed(const std::filesystem::path& path) noexcept(false)
    {
        SHAddToRecentDocs(SHARD_PATHW, path.c_str());
    }


    void Application::registerRecentlyUsed(
        const JumpItem& recentJumpItem, COMIsInitializedInThisThreadTag com) noexcept(false)
    {
        refreshJumpListHandle();
        auto& [appId_, jumpListHandle_] = idAndJumpListHandle_;

        const auto itemHandle = recentJumpItem.shareNativeHandle(com);

        if (appId_.has_value())
        {
            SHARDAPPIDINFO jumpItemInfo{};
            jumpItemInfo.psi = itemHandle;
            jumpItemInfo.pszAppID = appId_->c_str();

            SHAddToRecentDocs(SHARD_APPIDINFO, &jumpItemInfo);
        }
        else
        {
            SHAddToRecentDocs(SHARD_SHELLITEM, itemHandle);
        }
    }

    void Application::registerRecentlyUsed(
        const JumpTask& recentJumpTask,
        COMIsInitializedInThisThreadTag com) noexcept(false)
    {
        refreshJumpListHandle();
        auto& [appId_, jumpListHandle_] = idAndJumpListHandle_;

        const auto taskHandle = recentJumpTask.shareNativeHandle(com);

        if (appId_.has_value())
        {
            SHARDAPPIDINFOLINK jumpTaskInfo{};
            jumpTaskInfo.psl = taskHandle;
            jumpTaskInfo.pszAppID = appId_->c_str();

            SHAddToRecentDocs(SHARD_APPIDINFOLINK, &jumpTaskInfo);
        }
        else
        {
            SHAddToRecentDocs(SHARD_LINK, taskHandle);
        }
    }

    void Application::clearRecentsAndFrequents(COMIsInitializedInThisThreadTag) noexcept(false)
    {
        refreshJumpListHandle();

        auto& [appId_, jumpListHandle_] = idAndJumpListHandle_;

        const auto rfHandle = createRecentsAndFrequentsHandle(
            (appId_.has_value() ? &(*appId_) : nullptr),
            __func__,
            applicationCtxName
        );

        auto hr = rfHandle->RemoveAllDestinations();
        if ( (hr != S_OK) &&
             // it is ok if no recent docs was registered before
             (hr != HRESULT_FROM_WIN32(ERROR_FILE_NOT_FOUND)) )
            errors::throwCOMException(
                hr,
                "IApplicationDestinations::RemoveAllDestinations failed",
                __func__,
                applicationCtxName
            );

        // clears all usage data on all Recent items
        SHAddToRecentDocs(SHARD_PIDL, nullptr);
    }


    void Application::setJumpList(const JumpList& jumpList, COMIsInitializedInThisThreadTag com) noexcept(false)
    {
        deleteJumpList(com); // implies refreshJumpListHandle

        auto& [appId_, jumpListHandle_] = idAndJumpListHandle_;

        if (jumpListHandle_ == nullptr)
            throw std::logic_error("Application::setJumpList: jumpListHandle_ can not be nullptr");

        JumpListTransaction tr{jumpListHandle_};

        appendUserTasksTo(*tr, jumpList.getUserTasksCategory(), __func__, applicationCtxName, com);

        const auto& allCustomCategories = jumpList.getAllCustomCategories();
        for (const auto& customCategory : allCustomCategories)
            appendCustomCategoryTo(*tr, customCategory.first, customCategory.second, __func__, applicationCtxName, com);

        if (jumpList.isRecentCategoryVisible())
        {
            if (const auto hr = tr->AppendKnownCategory(KDC_RECENT); hr != S_OK)
                errors::throwCOMException(
                    hr,
                    "ICustomDestinationList::AppendKnownCategory(KDC_RECENT) failed",
                    __func__,
                    applicationCtxName
                );
        }

        if (jumpList.isFrequentCategoryVisible())
        {
            if (const auto hr = tr->AppendKnownCategory(KDC_FREQUENT); hr != S_OK)
                errors::throwCOMException(
                    hr,
                    "ICustomDestinationList::AppendKnownCategory(KDC_FREQUENT) failed",
                    __func__,
                    applicationCtxName
                );
        }

        tr.commit();
    }


    void Application::deleteJumpList(COMIsInitializedInThisThreadTag) noexcept(false)
    {
        refreshJumpListHandle();

        auto& [appId_, jumpListHandle_] = idAndJumpListHandle_;

        auto* const appIdCStr = appId_.has_value() ? appId_->c_str() : nullptr;

        if (const auto hr = jumpListHandle_->DeleteList(appIdCStr); hr != S_OK)
            errors::throwCOMException(
                hr,
                "ICustomDestinationList::DeleteList failed",
                __func__,
                applicationCtxName
            );
    }


    [[nodiscard]] std::optional<Application::UserModelId> Application::obtainAppUserModelId() const noexcept(false)
    {
        struct COMStr
        {
            PWSTR data = nullptr;
            ~COMStr()
            {
                if (data != nullptr)
                {
                    CoTaskMemFree(data);
                }
            }
        } comStr;

        if (const auto hr = GetCurrentProcessExplicitAppUserModelID(&comStr.data); hr != S_OK)
        {
            // E_FAIL is returned when appId was not set (via SetCurrentProcessExplicitAppUserModelID)
            if (hr == E_FAIL)
                return std::nullopt;

            errors::throwCOMException(
                hr,
                "GetCurrentProcessExplicitAppUserModelID failed",
                __func__,
                applicationCtxName
            );
        }

        assert( (comStr.data != nullptr) );

        return comStr.data;
    }


    void Application::refreshJumpListHandle() noexcept(false)
    {
        auto newAppId = obtainAppUserModelId();

        auto& [appId_, jumpListHandle_] = idAndJumpListHandle_;

        if ( (jumpListHandle_ == nullptr) || (appId_ != newAppId) )
        {
            jumpListHandle_ = createJumpListHandle(
                (newAppId.has_value() ? &(*newAppId) : nullptr),
                __func__,
                applicationCtxName
            );
            appId_ = std::move(newAppId);
        }
    }

} // namespace intellij::ui::win
