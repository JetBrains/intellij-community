cmake_minimum_required(VERSION 3.1)
project(IdeaWin32)
include(CheckSymbolExists)

if(NOT WIN32)
  message(FATAL_ERROR "Windows only.")
endif()

if (DEFINED ENV{BUILD_NUMBER})
  set(BUILD_NUMBER $ENV{BUILD_NUMBER})
else()
  set(BUILD_NUMBER 9999)
endif()

string(TIMESTAMP YEAR "%Y")

check_symbol_exists("_M_ARM64" "" TARGET_ARM64)
check_symbol_exists("_M_AMD64" "" TARGET_X64)
if(TARGET_ARM64)
  set(FILE_NAME "IdeaWin64a")
  set(JAVA_HOME $ENV{JDK_11_0_ARM64})
elseif(TARGET_X64)
  set(FILE_NAME "IdeaWin64")
  set(JAVA_HOME $ENV{JDK_11_0_x64})
else()
  set(FILE_NAME "IdeaWin32")
  set(JAVA_HOME $ENV{JDK_11_0})
endif()
if(NOT JAVA_HOME)
  message(FATAL_ERROR "JDK_11_0/JDK_11_0_x64/JDK_11_0_ARM64 variables not defined (x64=${TARGET_X64}, arm64=${TARGET_ARM64}).")
endif()

set(CMAKE_C_FLAGS_RELEASE "${CMAKE_C_FLAGS_RELEASE} /MT")
include_directories(${JAVA_HOME}/include ${JAVA_HOME}/include/win32)

configure_file(${CMAKE_CURRENT_SOURCE_DIR}/IdeaWin32.rc ${CMAKE_CURRENT_BINARY_DIR}/IdeaWin32.rc @ONLY)

add_library(IdeaWin32 SHARED IdeaWin32.c IdeaWin32.h ${CMAKE_CURRENT_BINARY_DIR}/IdeaWin32.rc)

set_target_properties(IdeaWin32 PROPERTIES OUTPUT_NAME ${FILE_NAME})