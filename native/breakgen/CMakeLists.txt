cmake_minimum_required(VERSION 3.1)
project(breakgen)
include(CheckSymbolExists)

if (NOT WIN32)
  message(FATAL_ERROR "Windows only.")
endif ()

set(CMAKE_C_FLAGS_RELEASE "${CMAKE_C_FLAGS_RELEASE} /MT")

check_symbol_exists("_M_AMD64" "" TARGET_X64)
if (TARGET_X64)
  set(FILE_NAME "breakgen64")
  set(JAVA_HOME $ENV{JDK_18_x64})
else ()
  set(FILE_NAME "breakgen")
  set(JAVA_HOME $ENV{JDK_18})
endif ()
if (NOT JAVA_HOME)
  message(FATAL_ERROR "JDK_18/JDK_18_x64 variables not defined (x64=${TARGET_X64}).")
endif ()

include_directories(${JAVA_HOME}/include ${JAVA_HOME}/include/win32)

add_library(breakgen SHARED AppMain.c)

set_target_properties(breakgen PROPERTIES OUTPUT_NAME FILE_NAME)
