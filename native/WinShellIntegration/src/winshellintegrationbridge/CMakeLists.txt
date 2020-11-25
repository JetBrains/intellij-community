# Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

if (NOT JDK_PATH)
    message(SEND_ERROR "Path to the JDK is not specified. winshellintegrationbridge target will not be generated.")
endif()

add_library(winshellintegrationbridge SHARED
    "COM_guard.h"
    "COM_guard.cpp"

    "win_shell_integration_bridge.h"
    "win_shell_integration_bridge.cpp"

    "com_intellij_ui_win_WinShellIntegration_Bridge.h"
    "com_intellij_ui_win_WinShellIntegration_Bridge.cpp"

    "dllmain.cpp"
)

target_include_directories(winshellintegrationbridge
    PRIVATE "${JDK_PATH}/include"
    PRIVATE "${JDK_PATH}/include/win32"
)

target_link_libraries(winshellintegrationbridge
    PRIVATE "winshellintegration"
    PRIVATE "Ole32.lib"
)

if ("${CMAKE_SIZEOF_VOID_P}" STREQUAL "8")
    set_target_properties(winshellintegrationbridge PROPERTIES
        OUTPUT_NAME "winshellintegrationbridge64"
    )
else()
    set_target_properties(winshellintegrationbridge PROPERTIES
        OUTPUT_NAME "winshellintegrationbridge"
    )
endif()

include("${PROJECT_SOURCE_DIR}/src/build/EnableCompilerExtraWarnings.cmake")
enable_target_compile_extra_warnings(winshellintegrationbridge)

# TODO: insert copyrights to DLL->Properties->Details
