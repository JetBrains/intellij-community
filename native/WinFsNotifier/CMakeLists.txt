cmake_minimum_required(VERSION 3.1)
project(WinFsNotifier)
include(CheckSymbolExists)

if(NOT WIN32)
  message(FATAL_ERROR "Windows only.")
endif()

if (DEFINED ENV{BUILD_NUMBER})
  set(BUILD_NUMBER $ENV{BUILD_NUMBER})
else()
  set(BUILD_NUMBER 9999)
endif()

string(TIMESTAMP YEAR "%Y")

check_symbol_exists("_M_AMD64" "" TARGET_X64)
if(TARGET_X64)
  set(FILE_NAME "fsnotifier64")
else()
  set(FILE_NAME "fsnotifier")
endif()

set(CMAKE_C_FLAGS_RELEASE "${CMAKE_C_FLAGS_RELEASE} /MT")

configure_file(${CMAKE_CURRENT_SOURCE_DIR}/fileWatcher3.rc ${CMAKE_CURRENT_BINARY_DIR}/fileWatcher3.rc @ONLY)

add_executable(WinFsNotifier fileWatcher3.c ${CMAKE_CURRENT_BINARY_DIR}/fileWatcher3.rc)

set_target_properties(WinFsNotifier PROPERTIES OUTPUT_NAME ${FILE_NAME})