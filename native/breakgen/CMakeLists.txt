cmake_minimum_required(VERSION 3.1)
project(breakgen)

if(NOT WIN32)
  message(FATAL_ERROR "Windows only.")
endif()

set(CMAKE_C_FLAGS_RELEASE "${CMAKE_C_FLAGS_RELEASE} /MT")

add_library(breakgen SHARED AppMain.c)

if(CMAKE_GENERATOR_PLATFORM STREQUAL "x64")
  set(JAVA_HOME $ENV{JDK_18_x64})
  set_target_properties(breakgen PROPERTIES OUTPUT_NAME "breakgen64")
else()
  set(JAVA_HOME $ENV{JDK_18})
endif()

include_directories(${JAVA_HOME}/include ${JAVA_HOME}/include/win32)