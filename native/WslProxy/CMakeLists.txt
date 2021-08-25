cmake_minimum_required(VERSION 3.5)
project(wslproxy C)

set(CMAKE_C_STANDARD 11)

add_executable(wslproxy wslproxy.c)
target_compile_options(wslproxy PRIVATE -Wall -Wextra -pedantic -Werror -Os)
target_link_libraries(wslproxy pthread)
