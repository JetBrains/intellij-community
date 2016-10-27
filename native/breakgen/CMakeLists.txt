cmake_minimum_required(VERSION 3.1.0)
project(breakgen)

include_directories($ENV{JAVA_HOME}/include $ENV{JAVA_HOME}/include/win32)

add_library(breakgen SHARED AppMain.c)