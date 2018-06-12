cmake_minimum_required(VERSION 3.1)
project(WinRestarter)

if(NOT WIN32)
  message(FATAL_ERROR "Windows only.")
endif()

if (DEFINED ENV{BUILD_NUMBER})
  set(BUILD_NUMBER $ENV{BUILD_NUMBER})
else()
  set(BUILD_NUMBER 9999)
endif()

string(TIMESTAMP YEAR "%Y")

set(CMAKE_C_FLAGS_RELEASE "${CMAKE_C_FLAGS_RELEASE} /MT")

configure_file(${CMAKE_CURRENT_SOURCE_DIR}/restarter.rc ${CMAKE_CURRENT_BINARY_DIR}/restarter.rc @ONLY)

add_executable(restarter restarter.c ${CMAKE_CURRENT_BINARY_DIR}/restarter.rc)