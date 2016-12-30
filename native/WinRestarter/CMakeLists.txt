cmake_minimum_required(VERSION 3.1)
project(WinRestarter)

if (NOT WIN32)
  message(FATAL_ERROR "Windows only.")
endif ()

set(CMAKE_C_FLAGS_RELEASE "${CMAKE_C_FLAGS_RELEASE} /MT")

add_executable(restarter restarter.c)