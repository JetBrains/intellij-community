# Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

find_package(Threads REQUIRED)

add_library(winshellintegration STATIC
    "include/winshellintegration.h"

    "include/winshellintegration/winapi.h"

    "include/winshellintegration/wide_string.h"

    "include/winshellintegration/COM_errors.h"
    "src/COM_errors.cpp"

    "include/winshellintegration/COM_is_initialized.h"

    "include/winshellintegration/jump_task.h"
    "src/jump_task.cpp"

    "include/winshellintegration/jump_item.h"
    "src/jump_item.cpp"

    "include/winshellintegration/jump_list.h"
    "src/jump_list.cpp"

    "include/winshellintegration/app_user_model_id.h"

    "include/winshellintegration/application.h"
    "src/application.cpp"
)

target_include_directories(winshellintegration
    PUBLIC "include"
)

target_link_libraries(winshellintegration
    PUBLIC "shell32.lib"
    PUBLIC "Threads::Threads"
)

target_compile_definitions(winshellintegration
    # Force Unicode version of WinAPI.
    # It is necessary for:
    #  * fixing of WinAPI SHARDAPPIDINFOLINK structure.
    PUBLIC UNICODE
    PUBLIC _UNICODE

    # Exclude rarely-used stuff from Windows headers
    PRIVATE WIN32_LEAN_AND_MEAN

    # Make the project supports Windows 8 and later.
    # See https://docs.microsoft.com/ru-ru/cpp/porting/modifying-winver-and-win32-winnt for more info.
    # 0x0602 is the identifier of Windows 8
    PUBLIC WINSHELLINTEGRATION_WINVER=0x0602
    PUBLIC WINVER=WINSHELLINTEGRATION_WINVER
    PUBLIC _WIN32_WINNT=WINSHELLINTEGRATION_WINVER
)

include("${PROJECT_SOURCE_DIR}/src/build/EnableCompilerExtraWarnings.cmake")
enable_target_compile_extra_warnings(winshellintegration)
