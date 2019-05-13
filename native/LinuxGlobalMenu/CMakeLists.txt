cmake_minimum_required(VERSION 2.6.0)
project(dbm)

include(CheckCXXSourceCompiles)
include (CheckCXXCompilerFlag)

check_cxx_compiler_flag(-fvisibility=hidden __DBUSMENU_HAVE_GCC_VISIBILITY)
if (__DBUSMENU_HAVE_GCC_VISIBILITY AND NOT WIN32)
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fvisibility=hidden")
endif (__DBUSMENU_HAVE_GCC_VISIBILITY AND NOT WIN32)

check_cxx_compiler_flag(-Woverloaded-virtual __DBUSMENU_HAVE_W_OVERLOADED_VIRTUAL)
if (__DBUSMENU_HAVE_W_OVERLOADED_VIRTUAL)
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Woverloaded-virtual")
endif (__DBUSMENU_HAVE_W_OVERLOADED_VIRTUAL)

check_cxx_compiler_flag(-Wall __DBUSMENU_HAVE_W_ALL)
if (__DBUSMENU_HAVE_W_ALL)
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wall")
endif (__DBUSMENU_HAVE_W_ALL)


find_library(LIB_GLIB NAMES glib libglib libglib-2.0.so.0 PATHS /lib/x86_64-linux-gnu)
MESSAGE("LIB_GLIB: " ${LIB_GLIB})

find_library(LIB_DBUSMENU NAMES libdbusmenu-glib.so PATHS /usr/lib/x86_64-linux-gnu)
MESSAGE("LIB_DBUSMENU: " ${LIB_DBUSMENU})

find_library(LIB_GIO NAMES libgio-2.0.so.0 PATHS /usr/lib/x86_64-linux-gnu)
MESSAGE("LIB_GIO: " ${LIB_GIO})

find_library(LIB_GOBJ NAMES libgobject-2.0.so.0 PATHS /usr/lib/x86_64-linux-gnu)
MESSAGE("LIB_GOBJ: " ${LIB_GOBJ})

set(GLIB_INCLUDE_DIRS /usr/include/glib-2.0 /usr/lib/x86_64-linux-gnu/glib-2.0/include)
set(DBUSMENU_GLIB_INCLUDE_DIRS /usr/include/libdbusmenu-glib-0.4)

include_directories(
        ${GLIB_INCLUDE_DIRS}
        ${DBUSMENU_GLIB_INCLUDE_DIRS}
)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11")

set(SOURCE_FILES DbusMenuWrapper.c)

add_library(dbm SHARED ${SOURCE_FILES})
target_link_libraries(dbm ${LIB_GLIB} ${LIB_GIO} ${LIB_DBUSMENU} ${LIB_GOBJ})

add_executable(dbmexec test.cc)
target_link_libraries(dbmexec dbm ${LIB_GLIB} ${LIB_GIO} ${LIB_DBUSMENU} ${LIB_DBUSMENU_GTK} ${LIB_GOBJ} ${LIB_GTK} ${LIB_GDK})

add_custom_command(TARGET dbm POST_BUILD
        COMMAND ${CMAKE_COMMAND} -E copy
        $<TARGET_FILE:dbm> /home/parallels/projects/IDEA/community/bin/linux/libdbm64.so)
add_custom_command(TARGET dbm POST_BUILD
        COMMAND ${CMAKE_COMMAND} -E copy
        $<TARGET_FILE:dbm> /home/parallels/IdeaProjects/TestMenu/libdbm.so)
