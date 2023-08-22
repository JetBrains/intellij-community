cmake_minimum_required(VERSION 3.1)
project(restarter OBJC)

if(NOT APPLE)
    message(FATAL_ERROR "macOS only.")
endif()

set(CMAKE_C_STANDARD 11)
set(CMAKE_OSX_ARCHITECTURES "x86_64;arm64")
set(CMAKE_OSX_DEPLOYMENT_TARGET 10.9)

add_compile_options("-fmodules" "-flto=full" "-Wall" "-Wextra" "-Wpedantic" "-Wno-newline-eof")
add_executable(restarter restarter.m)

install(TARGETS restarter DESTINATION ${CMAKE_CURRENT_LIST_DIR}/../../bin/mac)
