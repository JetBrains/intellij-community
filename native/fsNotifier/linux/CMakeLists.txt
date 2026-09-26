cmake_minimum_required(VERSION 3.20)
project(fsnotifier C)

if(NOT CMAKE_SYSTEM_NAME STREQUAL "Linux")
    message(FATAL_ERROR "Linux only.")
endif()

if(NOT VERSION)
    string(TIMESTAMP VERSION %Y%m%d.%H%M)
endif()

set(CMAKE_C_STANDARD 11)

add_executable(fsnotifier fsnotifier.h main.c inotify.c util.c)
target_compile_definitions(fsnotifier PRIVATE VERSION="${VERSION}")
target_compile_options(fsnotifier PRIVATE "-O2" "-Wall" "-Wextra" "-Wpedantic")
target_link_options(fsnotifier PRIVATE "-static" "-flto=full")

if(CMAKE_SYSTEM_PROCESSOR STREQUAL "amd64" OR CMAKE_SYSTEM_PROCESSOR STREQUAL "aarch64")
    cmake_path(SET install_dir NORMALIZE ${CMAKE_CURRENT_LIST_DIR}/../../../bin/linux)
    install(TARGETS fsnotifier DESTINATION ${install_dir}/${CMAKE_SYSTEM_PROCESSOR})
else()
    message(WARNING "Cannot install on ${CMAKE_SYSTEM_PROCESSOR}")
endif()
