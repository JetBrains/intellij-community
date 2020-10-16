// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/**
 * Smart pointer for WinAPI IUnknown objects. See below for the documentation.
 *
 * @author Nikita Provotorov
 */

#ifndef WINJUMPLISTBRIDGE_COM_OBJECT_SAFE_PTR_H
#define WINJUMPLISTBRIDGE_COM_OBJECT_SAFE_PTR_H

#include "winapi.h"     // IUnknown
#include "COM_errors.h" // errors::throwCOMException
#include <type_traits>  // std::is_base_of_v, std::add_lvalue_reference
#include <cassert>      // assert
#include <utility>      // std::move


namespace intellij::ui::win
{

    /// Smart pointer for WinAPI IUnknown objects: RAII wrapper around IUnknown::AddRef, IUnknown::Release methods.
    /// @warning DON'T USE IUnknown::AddRef, IUnknown::Release, IUnknown::QueryInterface METHODS EXPLICITLY!
    ///
    /// @tparam T - any type derived from IUnknown interface
    template<typename T>
    class COMObjectSafePtr
    {
        static_assert(std::is_base_of_v<IUnknown, T>, "T must be derived from IUnknown");

    public: // nested types
        using pointer = T*;
        using element_Type = T;

    public: // ctors/dtor
        constexpr COMObjectSafePtr() noexcept
            : obj_(nullptr)
        {}

        constexpr COMObjectSafePtr(std::nullptr_t) noexcept // NOLINT(google-explicit-constructor)
            : COMObjectSafePtr()
        {}


        explicit COMObjectSafePtr(pointer obj) noexcept
            : obj_(obj)
        {}


        COMObjectSafePtr(const COMObjectSafePtr& other)
            : obj_(nullptr)
        {
            if (other)
            {
                [[maybe_unused]] const auto refCounter = other.obj_->AddRef();
                assert( (refCounter > 1) );
            }

            obj_ = other.obj_;
        }

        COMObjectSafePtr(COMObjectSafePtr&& other) noexcept
            : obj_(other.release())
        {}


        ~COMObjectSafePtr()
        {
            reset();
        }

    public: // assignments
        COMObjectSafePtr& operator=(const COMObjectSafePtr& rhs)
        {
            if (this != &rhs)
            {
                auto copy = rhs;
                *this = std::move(copy);
            }

            return *this;
        }

        COMObjectSafePtr& operator=(COMObjectSafePtr&& rhs) // NOLINT(performance-noexcept-move-constructor)
        {
            if (this != &rhs)
            {
                reset(rhs.release());
            }

            return *this;
        }

    public: // modifiers
        /// replaces the managed object
        void reset(pointer newObj = nullptr)
        {
            auto* const oldObj = obj_;
            obj_ = newObj;

            if (oldObj != nullptr)
            {
                [[maybe_unused]] const auto refCounter = oldObj->Release();
            }
        }

        /// returns a pointer to the managed object and releases the ownership
        pointer release() noexcept
        {
            const pointer result = obj_;
            obj_ = nullptr;
            return result;
        }

    public: // getters
        explicit operator bool() const noexcept
        {
            return (obj_ != nullptr);
        }


        pointer get() const noexcept
        {
            return obj_;
        }


        typename std::add_lvalue_reference<T>::type operator*() const
        {
            assert( (get() != nullptr) );
            return *get();
        }

        pointer operator->() const noexcept
        {
            assert( (get() != nullptr) );
            return get();
        }


        template<typename D>
        COMObjectSafePtr<D> COMCast() const noexcept(false)
        {
            if (!*this)
                return nullptr;

            D* result = nullptr;

            if (const auto comResult = obj_->QueryInterface(&result); comResult != S_OK)
                errors::throwCOMException(comResult, "IUnknown::QueryInterface failed", __func__, "COMObjectSafePtr");

            return COMObjectSafePtr<D>{result};
        }

    private:
        T* obj_;
    };


    template<typename T>
    bool operator==(const COMObjectSafePtr<T>& ptr, std::nullptr_t)
    {
        return (!ptr);
    }

    template<typename T>
    bool operator==(std::nullptr_t, const COMObjectSafePtr<T>& ptr)
    {
        return (ptr == nullptr);
    }


    template<typename T>
    bool operator!=(const COMObjectSafePtr<T>& ptr, std::nullptr_t)
    {
        return (!(ptr == nullptr));
    }

    template<typename T>
    bool operator!=(std::nullptr_t, const COMObjectSafePtr<T>& ptr)
    {
        return (ptr != nullptr);
    }

} // namespace intellij::ui::win


#endif // ndef WINJUMPLISTBRIDGE_COM_OBJECT_SAFE_PTR_H
