# Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
cmake_minimum_required(VERSION 3.16 FATAL_ERROR)
project(WinProcessListHelper CXX)

if (NOT WIN32)
  message(FATAL_ERROR "Windows only.")
endif()

if (DEFINED ENV{BUILD_NUMBER})
  set(BUILD_NUMBER $ENV{BUILD_NUMBER})
else()
  set(BUILD_NUMBER 9999)
endif()

string(TIMESTAMP YEAR "%Y")

configure_file(${CMAKE_CURRENT_SOURCE_DIR}/${PROJECT_NAME}.rc ${CMAKE_CURRENT_BINARY_DIR}/${PROJECT_NAME}.rc @ONLY)

add_executable(${PROJECT_NAME} "${PROJECT_NAME}.cpp" ${CMAKE_CURRENT_BINARY_DIR}/${PROJECT_NAME}.rc)

set_target_properties(${PROJECT_NAME} PROPERTIES
    MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>"
)

target_compile_definitions(${PROJECT_NAME} PRIVATE UNICODE _UNICODE WIN32_LEAN_AND_MEAN)

target_compile_options(${PROJECT_NAME} PRIVATE "/sdl" "/Oi" "/Ob2" "/FC" "/GL" "/Zi" "/EHsc" "/Gy")

target_link_options(${PROJECT_NAME} PRIVATE /INCREMENTAL:NO /DEBUG /LTCG /OPT:REF /OPT:ICF "/pdbaltpath:%_PDB%")

