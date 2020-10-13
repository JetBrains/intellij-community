# Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

add_library(jumplistbridge SHARED
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
