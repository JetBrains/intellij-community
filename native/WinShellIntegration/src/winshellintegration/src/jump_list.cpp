// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

#include "winshellintegration/jump_list.h"


namespace intellij::ui::win
{

    JumpList::JumpList() noexcept
        : isRecentCategoryVisible_(false)
        , isFrequentCategoryVisible_(false)
    {}

    JumpList::JumpList(JumpList&&) = default;


    JumpList::~JumpList() noexcept = default;


    JumpList& JumpList::operator=(JumpList&&) = default;


    JumpList& JumpList::setRecentCategoryVisible(bool visible) noexcept
    {
        isRecentCategoryVisible_ = visible;
        return *this;
    }

    JumpList& JumpList::setFrequentCategoryVisible(bool visible) noexcept
    {
        isFrequentCategoryVisible_ = visible;
        return *this;
    }


    JumpList& JumpList::appendToUserTasks(JumpTask jumpTask, COMIsInitializedInThisThreadTag)
    {
        userTasksCategory_.emplace_back(std::move(jumpTask));
        return *this;
    }

    JumpList& JumpList::appendToUserTasks(JumpItem jumpItem, COMIsInitializedInThisThreadTag)
    {
        userTasksCategory_.emplace_back(std::move(jumpItem));
        return *this;
    }


    JumpList& JumpList::appendToCustomCategory(const WideStringView categoryName, JumpItem jumpItem, COMIsInitializedInThisThreadTag)
    {
        auto* category = findCustomCategory(categoryName);
        if (category == nullptr)
            category = &customCategories_.emplace_back(WideString{categoryName}, CustomCategoryContainer{}).second;

        category->emplace_back(std::move(jumpItem));

        return *this;
    }

    JumpList& JumpList::appendToCustomCategory(const WideStringView categoryName, JumpTask jumpTask, COMIsInitializedInThisThreadTag)
    {
        auto* category = findCustomCategory(categoryName);
        if (category == nullptr)
            category = &customCategories_.emplace_back(WideString{categoryName}, CustomCategoryContainer{}).second;

        category->emplace_back(std::move(jumpTask));

        return *this;
    }


    bool JumpList::isRecentCategoryVisible() const noexcept
    {
        return isRecentCategoryVisible_;
    }

    bool JumpList::isFrequentCategoryVisible() const noexcept
    {
        return isFrequentCategoryVisible_;
    }


    const JumpList::UserTasksCategoryContainer& JumpList::getUserTasksCategory() const noexcept
    {
        return userTasksCategory_;
    }

    const JumpList::CustomCategoriesContainer& JumpList::getAllCustomCategories() const noexcept
    {
        return customCategories_;
    }


    /// returns nullptr if a custom category with the passed name is not found
    const JumpList::CustomCategoryContainer* JumpList::findCustomCategory(WideStringView categoryName) const noexcept
    {
        for (const auto& [itCategoryName, itCategoryItems]: customCategories_)
            if (categoryName == itCategoryName)
                return &itCategoryItems;

        return nullptr;
    }

    /// returns nullptr if a custom category with the passed name is not found
    JumpList::CustomCategoryContainer* JumpList::findCustomCategory(WideStringView categoryName) noexcept
    {
        for (auto& [itCategoryName, itCategoryItems]: customCategories_)
            if (categoryName == itCategoryName)
                return &itCategoryItems;

        return nullptr;
    }

} // namespace intellij::ui::win
