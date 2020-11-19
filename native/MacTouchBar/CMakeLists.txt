cmake_minimum_required(VERSION 3.1)
project(MacTouchBar OBJC)

if(NOT APPLE)
    message(FATAL_ERROR "macOS only.")
endif()

set(CMAKE_C_STANDARD 11)
set(CMAKE_OSX_ARCHITECTURES "x86_64;arm64")
set(CMAKE_OSX_DEPLOYMENT_TARGET 10.12.2)

add_compile_options("-fmodules" "-flto=full" "-Wall" "-Wextra" "-Wpedantic" "-Wno-newline-eof")
add_library(nst64 SHARED
    src/JTypes.h src/Button.m src/Popover.m src/Scrubber.m
    src/ScrubberItemView.h src/ScrubberItemView.m
    src/TouchBar.h src/TouchBar.m
    src/Utils.h src/Utils.m
)

install(FILES $<TARGET_FILE:nst64> DESTINATION ${CMAKE_CURRENT_LIST_DIR}/../../bin/mac)
