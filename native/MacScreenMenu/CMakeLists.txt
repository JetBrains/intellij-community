cmake_minimum_required(VERSION 3.1)
project(MacScreenMenu OBJC)

if(NOT APPLE)
    message(FATAL_ERROR "macOS only.")
endif()

set(CMAKE_C_STANDARD 11)
set(CMAKE_OSX_ARCHITECTURES "x86_64;arm64")
set(CMAKE_OSX_DEPLOYMENT_TARGET 10.12.2)


set(JAVA_HOME $ENV{JAVA_HOME})
if (NOT JAVA_HOME)
    message(FATAL_ERROR "JAVA_HOME variables not defined.")
endif ()

include_directories(${JAVA_HOME}/include ${JAVA_HOME}/include/darwin)

add_compile_options("-fmodules" "-flto=full" "-Wall" "-Wextra" "-Wpedantic" "-Wno-newline-eof" "-Wno-unused-parameter")
add_library(macscreenmenu64 SHARED
        src/MenuItem.h src/MenuItem.m src/Menu.m
        src/CustomMenuItemView.h src/CustomMenuItemView.m src/Menu.h)

install(FILES $<TARGET_FILE:macscreenmenu64> DESTINATION ${CMAKE_CURRENT_LIST_DIR}/../../bin/mac)
