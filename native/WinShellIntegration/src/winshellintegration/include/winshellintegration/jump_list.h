// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/**
 * intellij::ui::win::JumpList class implementation.
 *
 * See below for the documentation.
 *
 * @author Nikita Provotorov
 */

#ifndef WINSHELLINTEGRATION_JUMPLIST_H
#define WINSHELLINTEGRATION_JUMPLIST_H

#include "jump_task.h"              // JumpTask
#include "jump_item.h"              // JumpItem
#include "wide_string.h"            // WideString, WideStringView
#include "COM_is_initialized.h"     // COMIsInitializedInThisThreadTag
#include <variant>                  // std::variant
#include <deque>                    // std::deque
#include <list>                     // std::list
#include <utility>                  // std::pair


namespace intellij::ui::win
{

    /// @brief JumpList encapsulates methods and properties that allow an application to provide
    ///        a custom Jump List, including destinations and tasks, for display in the taskbar.
    ///
    /// How to use it:
    ///     1. Create an instance of JumpList;
    ///     2. Tune it via its modifiers (see below for their documentation);
    ///     3. Apply it to the Taskbar via intellij::ui::win::Application::setJumpList method.
    ///
    /// @note Some member functions require the COM library to be initialized in the invoking thread.
    ///       All of them receive an additional tag parameter of the intellij::ui::win::COMIsInitializedInThisThreadTag type.
    class JumpList
    {
    public: // nested types
        // JumpList holds JumpItem s and/or JumpTask s
        using value_type = std::variant<JumpItem, JumpTask>;

        using UserTasksCategoryContainer = std::deque<value_type>;

        using CustomCategoryContainer = std::deque<value_type>;
        // We are using std::list not std::map/std::unordered_map because we should keep the insertion order
        using CustomCategoriesContainer = std::list< std::pair<const WideString, CustomCategoryContainer> >;

    public: // ctors/dtor
        /// isRecentCategoryVisible() returns false
        /// isFrequentCategoryVisible() returns false
        JumpList() noexcept;

        /// JumpLists are non-copyable
        JumpList(const JumpList&) = delete;
        JumpList(JumpList&&);

        ~JumpList() noexcept;

    public: // assignments
        /// JumpLists are non-copyable
        JumpList& operator=(const JumpList&) = delete;
        JumpList& operator=(JumpList&&);

    public: // modifiers
        /// Sets a value that indicates whether recently used items are displayed in the Jump List (Recent category).
        /// You can call the Application::registerRecentlyUsed method (SHAddToRecentDocs system call)
        ///     to request that the Windows shell add items to the Recent items list.
        ///
        /// @returns *this
        JumpList& setRecentCategoryVisible(bool visible) noexcept;
        /// Sets a value that indicates whether frequently used items are displayed in the Jump List (Frequent category).
        /// You can call the Application::registerRecentlyUsed method (SHAddToRecentDocs system call)
        ///     to request that the Windows shell add items to the Frequent items list.
        ///
        /// @returns *this
        JumpList& setFrequentCategoryVisible(bool visible) noexcept;

        /// Specifies items to include in the "Tasks" category.
        /// See https://docs.microsoft.com/en-us/windows/win32/api/shobjidl_core/nf-shobjidl_core-icustomdestinationlist-addusertasks
        ///     for detailed info about "Tasks" category.
        ///
        /// @returns *this
        JumpList& appendToUserTasks(JumpTask jumpTask, COMIsInitializedInThisThreadTag);
        /// Specifies items to include in the "Tasks" category.
        /// @note You probably need to use overload with JumpTask parameter. User tasks use JumpItem is a really rare case.
        ///
        /// @returns *this
        JumpList& appendToUserTasks(JumpItem jumpItem, COMIsInitializedInThisThreadTag);

        /// Defines a custom category and the destinations that it contains, for inclusion in a custom Jump List.
        ///
        /// @returns *this
        JumpList& appendToCustomCategory(WideStringView categoryName, JumpItem jumpItem, COMIsInitializedInThisThreadTag);
        /// Defines a custom category and the destinations that it contains, for inclusion in a custom Jump List.
        ///
        /// @returns *this
        JumpList& appendToCustomCategory(WideStringView categoryName, JumpTask jumpTask, COMIsInitializedInThisThreadTag);

    public: // getters
        /// See setRecentCategoryVisible for the documentation.
        [[nodiscard]] bool isRecentCategoryVisible() const noexcept;
        /// See setFrequentCategoryVisible for the documentation.
        [[nodiscard]] bool isFrequentCategoryVisible() const noexcept;

        [[nodiscard]] const UserTasksCategoryContainer& getUserTasksCategory() const noexcept;

        [[nodiscard]] const CustomCategoriesContainer& getAllCustomCategories() const noexcept;

        /// returns nullptr if a custom category with the passed name is not found
        [[nodiscard]] const CustomCategoryContainer* findCustomCategory(WideStringView categoryName) const noexcept;

    private:
        [[nodiscard]] CustomCategoryContainer* findCustomCategory(WideStringView categoryName) noexcept;

    private:
        UserTasksCategoryContainer userTasksCategory_;
        CustomCategoriesContainer customCategories_;
        bool isRecentCategoryVisible_;
        bool isFrequentCategoryVisible_;
    };

} // namespace intellij::ui::win

#endif // ndef WINSHELLINTEGRATION_JUMPLIST_H
