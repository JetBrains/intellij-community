cmake_minimum_required(VERSION 3.1)
project(MacLauncher OBJC)

if (NOT APPLE)
    message(FATAL_ERROR "macOS only.")
endif ()

set(CMAKE_C_STANDARD 11)
set(CMAKE_OSX_ARCHITECTURES "x86_64;arm64")
set(CMAKE_OSX_DEPLOYMENT_TARGET 10.9)

set(JAVA_HOME $ENV{JDK_11_x64})
if (NOT JAVA_HOME)
    message(FATAL_ERROR "JDK_11_x64 variable not defined.")
endif ()

add_compile_options("-fno-objc-arc" "-flto=full" "-Wall" "-Wextra" "-Wpedantic")

add_executable(MacLauncher main.m Launcher.m VMOptionsReader.m PropertyFileReader.m utils.m rosetta.m)
target_include_directories(MacLauncher PRIVATE ${JAVA_HOME}/include ${JAVA_HOME}/include/darwin)
target_link_libraries(MacLauncher "-framework Foundation" "-framework AppKit" "-framework Cocoa")
