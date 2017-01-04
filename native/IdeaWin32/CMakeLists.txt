cmake_minimum_required(VERSION 3.1)
project(IdeaWin32)

if(NOT WIN32)
  message(FATAL_ERROR "Windows only.")
endif()

set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} /MT")

add_library(IdeaWin32 SHARED IdeaWin32.cpp IdeaWin32.h)

if(CMAKE_GENERATOR_PLATFORM STREQUAL "x64")
  set(JAVA_HOME $ENV{JDK_18_x64})
  set_target_properties(IdeaWin32 PROPERTIES OUTPUT_NAME "IdeaWin64")
else()
  set(JAVA_HOME $ENV{JDK_18})
endif()

include_directories(${JAVA_HOME}/include ${JAVA_HOME}/include/win32)