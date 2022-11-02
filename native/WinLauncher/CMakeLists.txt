cmake_minimum_required(VERSION 3.15)
project(WinLauncher CXX)
include(CheckSymbolExists)

if(NOT WIN32)
  message(FATAL_ERROR "Windows only.")
endif()

if(DEFINED ENV{BUILD_NUMBER})
  set(BUILD_NUMBER $ENV{BUILD_NUMBER})
else()
  set(BUILD_NUMBER 9999)
endif()

string(TIMESTAMP YEAR "%Y")

if(NOT JAVA_HOME)
  set(JAVA_HOME $ENV{JDK_11_0})
endif()
if(NOT JAVA_HOME)
  set(JAVA_HOME $ENV{JDK_11_0_x64})
endif()
if(NOT JAVA_HOME)
  set(JAVA_HOME $ENV{JDK_11_0_ARM64})
endif()
if(NOT JAVA_HOME)
  set(JAVA_HOME $ENV{JAVA_HOME})
endif()
if(NOT JAVA_HOME)
  message(FATAL_ERROR "JDK_11_0/JDK_11_0_x64/JDK_11_0_ARM64/JAVA_HOME env variables not defined.")
endif()

set(FILE_NAME "WinLauncher")

configure_file(${CMAKE_CURRENT_SOURCE_DIR}/WinLauncher.rc ${CMAKE_CURRENT_BINARY_DIR}/WinLauncher.rc @ONLY)

add_executable(WinLauncher WIN32 WinLauncher.cpp WinLauncher.manifest ${CMAKE_CURRENT_BINARY_DIR}/WinLauncher.rc)

set_target_properties(WinLauncher PROPERTIES
    CXX_STANDARD 14
    MSVC_RUNTIME_LIBRARY "MultiThreaded")

target_compile_definitions(WinLauncher PRIVATE UNICODE _UNICODE)
target_include_directories(WinLauncher PRIVATE ${JAVA_HOME}/include ${JAVA_HOME}/include/win32 ${CMAKE_CURRENT_SOURCE_DIR})
target_compile_options(WinLauncher PRIVATE /W3 /Zi /Oi /Gy /GL)
target_link_options(WinLauncher PRIVATE /INCREMENTAL:NO /DEBUG /LTCG /OPT:REF /OPT:ICF "/pdbaltpath:%_PDB%")
