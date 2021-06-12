cmake_minimum_required(VERSION 3.1)
project(WinLauncher)
include(CheckSymbolExists)

if (NOT WIN32)
  message(FATAL_ERROR "Windows only.")
endif ()

set(CMAKE_CXX_STANDARD 14)

# generating .pdb files
set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} /Zi")
set(CMAKE_EXE_LINKER_FLAGS_RELEASE "${CMAKE_EXE_LINKER_FLAGS_RELEASE} /DEBUG /OPT:REF /OPT:ICF")

set(CMAKE_RC_FLAGS "${CMAKE_RC_FLAGS} /i${CMAKE_CURRENT_SOURCE_DIR}")

if (DEFINED ENV{BUILD_NUMBER})
  set(BUILD_NUMBER $ENV{BUILD_NUMBER})
else ()
  set(BUILD_NUMBER 9999)
endif ()

string(TIMESTAMP YEAR "%Y")

check_symbol_exists("_M_AMD64" "" TARGET_X64)
if (TARGET_X64)
  set(FILE_NAME "WinLauncher64")
  set(JAVA_HOME $ENV{JDK_11_x64})
else ()
  set(FILE_NAME "WinLauncher")
  set(JAVA_HOME $ENV{JDK_11})
endif ()
if (NOT JAVA_HOME)
  message(FATAL_ERROR "JDK_11/JDK_11_x64 variables not defined (x64=${TARGET_X64}).")
endif ()

include_directories(${JAVA_HOME}/include ${JAVA_HOME}/include/win32)

configure_file(${CMAKE_CURRENT_SOURCE_DIR}/WinLauncher.rc ${CMAKE_CURRENT_BINARY_DIR}/WinLauncher.rc @ONLY)

add_executable(WinLauncher WIN32 WinLauncher.cpp ${CMAKE_CURRENT_BINARY_DIR}/WinLauncher.rc)

set_target_properties(WinLauncher PROPERTIES OUTPUT_NAME ${FILE_NAME})
