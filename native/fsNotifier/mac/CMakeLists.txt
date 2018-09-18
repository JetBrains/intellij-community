cmake_minimum_required(VERSION 3.1)
project(fsnotifier C)

if(NOT APPLE)
    message(FATAL_ERROR "macOS only.")
endif()

set(CMAKE_C_STANDARD 11)

find_library(CORE_SERVICES CoreServices)

add_executable(fsnotifier fsnotifier.c)
target_link_libraries(fsnotifier ${CORE_SERVICES})