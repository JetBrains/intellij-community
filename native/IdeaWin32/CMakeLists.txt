cmake_minimum_required(VERSION 3.1)
project(IdeaWin32)

if(NOT WIN32)
  message(FATAL_ERROR "Windows only.")
endif()

if (DEFINED ENV{BUILD_NUMBER})
  set(BUILD_NUMBER $ENV{BUILD_NUMBER})
else()
  set(BUILD_NUMBER 9999)
endif()

string(TIMESTAMP YEAR "%Y")

if(CMAKE_GENERATOR_PLATFORM STREQUAL "x64")
  set(FILE_NAME "IdeaWin64")
  set(JAVA_HOME $ENV{JDK_18_x64})
else()
  set(FILE_NAME "IdeaWin32")
  set(JAVA_HOME $ENV{JDK_18})
endif()

set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} /MT")
include_directories(${JAVA_HOME}/include ${JAVA_HOME}/include/win32)

configure_file(${CMAKE_CURRENT_SOURCE_DIR}/IdeaWin32.rc ${CMAKE_CURRENT_BINARY_DIR}/IdeaWin32.rc @ONLY)

add_library(IdeaWin32 SHARED IdeaWin32.cpp IdeaWin32.h ${CMAKE_CURRENT_BINARY_DIR}/IdeaWin32.rc)

set_target_properties(IdeaWin32 PROPERTIES OUTPUT_NAME ${FILE_NAME})