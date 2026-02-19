cmake_minimum_required(VERSION 3.24)
project(fsnotifier C)

if(NOT APPLE)
    message(FATAL_ERROR "macOS only.")
endif()

set(CMAKE_C_STANDARD 11)
set(CMAKE_OSX_ARCHITECTURES "x86_64;arm64")
set(CMAKE_OSX_DEPLOYMENT_TARGET 10.9)

add_executable(fsnotifier fsnotifier.c)
target_compile_options(fsnotifier PRIVATE "-O2" "-Wall" "-Wextra" "-Wpedantic")
target_link_options(fsnotifier PRIVATE "-flto=full")
target_link_libraries(fsnotifier PRIVATE "$<LINK_LIBRARY:FRAMEWORK,CoreFoundation>" "$<LINK_LIBRARY:FRAMEWORK,CoreServices>")

cmake_path(SET install_dir NORMALIZE ${CMAKE_CURRENT_LIST_DIR}/../../../bin/mac)
install(TARGETS fsnotifier DESTINATION ${install_dir})
