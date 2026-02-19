# Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

include(CheckSymbolExists)
check_symbol_exists("_M_ARM64" "" TARGET_ARM64)
check_symbol_exists("_M_AMD64" "" TARGET_X64)

# output binary name
if(TARGET_ARM64)
  set(WSIBRIDGE_BINARY_NAME "WinShellIntegrationBridge64a")
elseif(TARGET_X64)
  set(WSIBRIDGE_BINARY_NAME "WinShellIntegrationBridge64")
else()
  set(WSIBRIDGE_BINARY_NAME "WinShellIntegrationBridge")
endif()

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

if(NOT JDK_PATH)
  if(NOT JAVA_HOME)
    message(SEND_ERROR "Path to the JDK (JDK_PATH) is not specified, neither heuristics succeed. 'winshellintegrationbridge' target will not be generated.")
  else()
    set(JDK_PATH "${JAVA_HOME}")
  endif()
endif()

configure_file("resources.rc.in" "${CMAKE_CURRENT_BINARY_DIR}/resources.rc" @ONLY)

add_library(winshellintegrationbridge SHARED
    "COM_guard.h"
    "COM_guard.cpp"

    "win_shell_integration_bridge.h"
    "win_shell_integration_bridge.cpp"

    "com_intellij_ui_win_WinShellIntegration_Bridge.h"
    "com_intellij_ui_win_WinShellIntegration_Bridge.cpp"

    "${CMAKE_CURRENT_BINARY_DIR}/resources.rc"
)

target_include_directories(winshellintegrationbridge
    PRIVATE "${JDK_PATH}/include"
    PRIVATE "${JDK_PATH}/include/win32"
)

target_link_libraries(winshellintegrationbridge
    PRIVATE "winshellintegration"
    PRIVATE "Ole32.lib"
)

# forces to link dynamically to against exactly release version of MSVC runtime (msvcp140.dll), not debug version (msvcp140d.dll)
# in both release and debug builds.
set_target_properties(winshellintegration winshellintegrationbridge PROPERTIES
    MSVC_RUNTIME_LIBRARY "MultiThreadedDLL"
)

set_target_properties(winshellintegrationbridge PROPERTIES
    OUTPUT_NAME "${WSIBRIDGE_BINARY_NAME}"
)

include("${PROJECT_SOURCE_DIR}/src/build/EnableCompilerExtraWarnings.cmake")
enable_target_compile_extra_warnings(winshellintegrationbridge)
