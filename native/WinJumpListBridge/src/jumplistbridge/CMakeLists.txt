# Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

# TODO: split to a separate JumpList static library and the jumplistbridge DLL.

add_library(jumplistbridge SHARED
    "winapi.h"
    "wide_string.h"
    "COM_errors.h"
    "COM_errors.cpp"
    "COM_object_safe_ptr.h"
    "COM_guard.h"
    "COM_guard.cpp"
    "COM_is_initialized.h"
    "jump_task.h"
    "jump_task.cpp"
    "jump_item.h"
    "jump_item.cpp"
    "jump_list.h"
    "jump_list.cpp"
    "app_user_model_id.h"
    "application.h"
    "application.cpp"

    "com_intellij_ui_win_RecentTasks.h"
    "com_intellij_ui_win_RecentTasks.cpp"

    "dllmain.cpp"
)

if ("${CMAKE_SIZEOF_VOID_P}" STREQUAL "8")
    set_target_properties(jumplistbridge PROPERTIES
        OUTPUT_NAME "jumplistbridge64"
    )
else()
    set_target_properties(jumplistbridge PROPERTIES
        OUTPUT_NAME "jumplistbridge"
    )
endif()

include("${PROJECT_SOURCE_DIR}/src/build/EnableCompilerExtraWarnings.cmake")
enable_target_compile_extra_warnings(jumplistbridge)

target_include_directories(jumplistbridge
    PRIVATE "${JDK_PATH}/include"
    PRIVATE "${JDK_PATH}/include/win32"
)

target_link_libraries(jumplistbridge
    Shell32.lib
)

# TODO: insert copyrights to DLL->Properties->Details


# jumplisttests

# TODO: remove this target
add_executable(jumplisttests
    "winapi.h"
    "wide_string.h"
    "COM_errors.h"
    "COM_errors.cpp"
    "COM_object_safe_ptr.h"
    "COM_guard.h"
    "COM_guard.cpp"
    "COM_is_initialized.h"
    "jump_task.h"
    "jump_task.cpp"
    "jump_item.h"
    "jump_item.cpp"
    "jump_list.h"
    "jump_list.cpp"
    "app_user_model_id.h"
    "application.h"
    "application.cpp"

    "testsmain.cpp"
)

enable_target_compile_extra_warnings(jumplisttests)

target_link_libraries(jumplisttests
    Shell32.lib
)


# TODO: remove the registry entries:
# 1. Computer\HKEY_CLASSES_ROOT\Applications\jumplisttests.exe
