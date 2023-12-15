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
    CXX_STANDARD 17
    MSVC_RUNTIME_LIBRARY "MultiThreaded")

set(USE_CEF_SANDBOX "ON")
if(USE_CEF_SANDBOX)
  # Setup linking with CEF sandbox library
  include("DownloadCEF.cmake")
  if(NOT DEFINED CEF_VERSION)
    set(CEF_VERSION "119.4.3+gc76a3b9+chromium-119.0.6045.159")
  endif()

  include(CheckSymbolExists)
  # MSVC predefines _M_ARM64 for compilations that target ARM64
  # and _M_AMD64 for compilations that target x86_64.
  check_symbol_exists("_M_ARM64" "" CEF_PLATFORM_WINARM64)
  check_symbol_exists("_M_AMD64" "" CEF_PLATFORM_WIN64)

  if(CEF_PLATFORM_WINARM64)
    set(CEF_PLATFORM "windowsarm64")
  elseif(CEF_PLATFORM_WIN64)
    set(CEF_PLATFORM "windows64")
  endif()

  DownloadCEF(${CEF_PLATFORM} "${CEF_VERSION}" "${CMAKE_SOURCE_DIR}/cef")
  message(STATUS "Use cef_sandbox.lib from root: ${CEF_ROOT}")

  set(CEF_INCLUDE_PATH "${CEF_ROOT}")
  set(CEF_SANDBOX_LIB_RELEASE "${CEF_ROOT}/Release/cef_sandbox.lib")
  add_library(cef_sandbox_lib STATIC IMPORTED)
  set_target_properties(cef_sandbox_lib PROPERTIES
      IMPORTED_LOCATION "${CEF_SANDBOX_LIB_RELEASE}")
  set(CEF_SANDBOX_STANDARD_LIBS
      Advapi32.lib
      dbghelp.lib
      Delayimp.lib
      ntdll.lib
      OleAut32.lib
      PowrProf.lib
      Propsys.lib
      psapi.lib
      SetupAPI.lib
      Shell32.lib
      shlwapi.lib
      Shcore.lib
      Userenv.lib
      version.lib
      wbemuuid.lib
      winmm.lib
      ws2_32.lib
      WindowsApp.lib
      winmm.lib)
  target_link_libraries(WinLauncher cef_sandbox_lib ${CEF_SANDBOX_STANDARD_LIBS})

  set(CEF_COMPILE_DEFS "USE_CEF_SANDBOX")
endif()

target_compile_definitions(WinLauncher PRIVATE UNICODE _UNICODE ${CEF_COMPILE_DEFS})
target_include_directories(WinLauncher PRIVATE ${JAVA_HOME}/include ${JAVA_HOME}/include/win32 ${CMAKE_CURRENT_SOURCE_DIR} ${CEF_INCLUDE_PATH})
target_compile_options(WinLauncher PRIVATE /W3 /Zi /Oi /Gy /GL)
target_link_options(WinLauncher PRIVATE /INCREMENTAL:NO /DEBUG /LTCG /OPT:REF /OPT:ICF "/pdbaltpath:%_PDB%")
