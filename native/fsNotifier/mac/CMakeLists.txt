cmake_minimum_required(VERSION 3.1)
project(fsnotifier C)

if(NOT APPLE)
    message(FATAL_ERROR "macOS only.")
endif()

set(CMAKE_C_FLAGS "-O2 -Wall -Wextra -Wpedantic -Wno-newline-eof")
set(CMAKE_C_STANDARD 11)
set(CMAKE_OSX_DEPLOYMENT_TARGET 10.8)

find_library(CORE_SERVICES CoreServices)

add_executable(fsnotifier fsnotifier.c)
target_link_libraries(fsnotifier ${CORE_SERVICES})