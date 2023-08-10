cmake_minimum_required(VERSION 3.16 FATAL_ERROR)
project(WinElevator)

if (NOT WIN32)
  message(FATAL_ERROR "Windows only.")
endif ()

if (DEFINED ENV{BUILD_NUMBER})
  set(BUILD_NUMBER $ENV{BUILD_NUMBER})
else()
  set(BUILD_NUMBER 9999)
endif()

string(TIMESTAMP YEAR "%Y")

set(CMAKE_C_FLAGS_RELEASE "${CMAKE_C_FLAGS_RELEASE} /MT")

add_compile_definitions("UNICODE" "_UNICODE")

include_directories("${CMAKE_CURRENT_SOURCE_DIR}")

function(define_project_part target)
  configure_file(${CMAKE_CURRENT_SOURCE_DIR}/WinElevator.rc ${CMAKE_CURRENT_BINARY_DIR}/${target}.rc @ONLY)

  add_executable(${target} "${target}.c" ${CMAKE_CURRENT_BINARY_DIR}/${target}.rc)

  target_compile_options(${target} PRIVATE "/sdl" "/Oi" "/Ob2" "/FC" "/GL" "/Zi" "/EHsc" "/Gy")

  target_link_options(${target} PRIVATE "/DEBUG" "/OPT:REF" "/OPT:ICF" "/LTCG:incremental" "/pdbaltpath:%_PDB%")

  set_target_properties(${target} PROPERTIES
    MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>"
  )

  set_source_files_properties(jb.ico PROPERTIES LANGUAGE RC)
endfunction()

define_project_part(elevator)
define_project_part(launcher)

target_link_options(elevator PRIVATE "/MANIFESTUAC:level=requireAdministrator"  "/MANIFESTUAC:uiAccess=false")
target_link_options(launcher PRIVATE "/MANIFESTUAC:level=asInvoker"             "/MANIFESTUAC:uiAccess=false")
