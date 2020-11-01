# Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

if (NOT WIN32)
    message(FATAL_ERROR "This project is intended for Windows only.")
endif()

cmake_minimum_required(VERSION 3.15)    # 3.15 is required for the CMP0092 policy
project(WinShellIntegration CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(CMAKE_CXX_EXTENSIONS OFF)

add_subdirectory("src/winshellintegration")
add_subdirectory("src/winshellintegrationbridge")

#enable_testing()
#add_subdirectory("tests")
