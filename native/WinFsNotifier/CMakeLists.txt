cmake_minimum_required(VERSION 3.1)
project(WinFsNotifier)

if(NOT WIN32)
  message(FATAL_ERROR "Windows only.")
endif()

set(BUILD_NUMBER $ENV{BUILD_NUMBER})
string(TIMESTAMP YEAR "%Y")
if(CMAKE_GENERATOR_PLATFORM STREQUAL "x64")
  set(FILE_NAME "fsnotifier64")
else()
  set(FILE_NAME "fsnotifier")
endif()

set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} /MT")

configure_file(${CMAKE_CURRENT_SOURCE_DIR}/fileWatcher3.rc ${CMAKE_CURRENT_BINARY_DIR}/fileWatcher3.rc @ONLY)

add_executable(WinFsNotifier fileWatcher3.cpp ${CMAKE_CURRENT_BINARY_DIR}/fileWatcher3.rc)
target_link_libraries(WinFsNotifier mpr.lib)

set_target_properties(WinFsNotifier PROPERTIES OUTPUT_NAME ${FILE_NAME})