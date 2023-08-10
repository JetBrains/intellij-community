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


find_library(LIB_GLIB NAMES glib libglib libglib-2.0.so.0)
MESSAGE("LIB_GLIB: " ${LIB_GLIB})

find_library(LIB_GIO NAMES libgio-2.0.so.0)
MESSAGE("LIB_GIO: " ${LIB_GIO})

find_library(LIB_GOBJ NAMES libgobject-2.0.so.0)
MESSAGE("LIB_GOBJ: " ${LIB_GOBJ})

# use patched library, you may build it from https://github.com/JetBrains/libdbusmenu
set(LIB_DBUSMENU "${PROJECT_SOURCE_DIR}/libdbusmenu-glib.a")

set(GLIB_INCLUDE_DIRS /usr/include/glib-2.0 /usr/lib64/glib-2.0/include)
set(DBUSMENU_GLIB_INCLUDE_DIRS /usr/include/libdbusmenu-glib-0.4)

include_directories(
        ${GLIB_INCLUDE_DIRS}
        ${DBUSMENU_GLIB_INCLUDE_DIRS}
)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_FLAGS "-std=gnu99 ${CMAKE_C_FLAGS}")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11")

set(SOURCE_FILES DbusMenuWrapper.c)

add_library(dbm SHARED ${SOURCE_FILES})
target_link_libraries(dbm ${LIB_GLIB} ${LIB_GIO} ${LIB_DBUSMENU} ${LIB_GOBJ})

if (TESTS_ENABLED)
  find_library(LIB_GTK NAMES libgtk-3.so)
  MESSAGE("LIB_GTK: " ${LIB_GTK})

  set(GTK_INCLUDE_DIRS /usr/include/gtk-3.0)
  include_directories(${GTK_INCLUDE_DIRS} /usr/include/pango-1.0 /usr/include/harfbuzz /usr/include/cairo /usr/include/gdk-pixbuf-2.0 /usr/include/atk-1.0)

  find_library(LIB_GDK NAMES libgdk-3.so) # needs to get xid from GtkWidget
  MESSAGE("LIB_GDK: " ${LIB_GDK})

  add_executable(dbmexec test.cc)
  target_link_libraries(dbmexec dbm ${LIB_GLIB} ${LIB_GIO} ${LIB_DBUSMENU} ${LIB_DBUSMENU_GTK} ${LIB_GOBJ} ${LIB_GTK} ${LIB_GDK})
endif(TESTS_ENABLED)
