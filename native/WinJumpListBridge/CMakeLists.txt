# Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

if (NOT WIN32)
    message(FATAL_ERROR "This project is intended for Windows only.")
endif()

if (NOT JDK_PATH)
    message(FATAL_ERROR "Path to the JDK is not specified.")
endif()

cmake_minimum_required(VERSION 3.15)    # 3.15 is required for the CMP0092 policy
project(WinJumpListBridge CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(CMAKE_CXX_EXTENSIONS OFF)

#include_directories("include")

add_subdirectory("src/jumplistbridge")

#enable_testing()
#add_subdirectory("tests")
