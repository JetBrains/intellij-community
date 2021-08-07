cmake_minimum_required(VERSION 3.1)
project(MacEnvReader C)

if (NOT APPLE)
    message(FATAL_ERROR "macOS only.")
endif ()

set(CMAKE_C_STANDARD 11)
set(CMAKE_OSX_ARCHITECTURES "x86_64;arm64")
set(CMAKE_OSX_DEPLOYMENT_TARGET 10.9)

add_compile_options("-fno-objc-arc" "-flto=full" "-Wall" "-Wextra" "-Wpedantic")

add_executable(printenv printenv.c)
