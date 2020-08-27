// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

#pragma once

#define VERSION "20200827.1844"

#include <stdbool.h>
#include <stdio.h>


// messaging and logging
void message(const char *text);
void userlog(int priority, const char* format, ...);

#define CHECK_NULL(p, r) if (p == NULL) { userlog(LOG_ERR, "out of memory"); return r; }


// variable-length array
typedef struct __array array;

array* array_create(int initial_capacity);
int array_size(array* a);
void* array_push(array* a, void* element);
void* array_pop(array* a);
void array_put(array* a, int index, void* element);
void* array_get(array* a, int index);
void array_delete(array* a);
void array_delete_vs_data(array* a);
void array_delete_data(array* a);


// poor man's hash table
typedef struct __table table;

table* table_create(int capacity);
void* table_put(table* t, int key, void* value);
void* table_get(table* t, int key);
void table_delete(table* t);


// inotify subsystem
enum {
  ERR_IGNORE = -1,
  ERR_CONTINUE = -2,
  ERR_ABORT = -3,
  ERR_MISSING = -4
};

bool init_inotify();
void set_inotify_callback(void (* callback)(const char*, int));
int get_inotify_fd();
int watch(const char* root, array* mounts);
void unwatch(int id);
bool process_inotify_input();
void close_inotify();


// reads one line from stream, trims trailing carriage return if any
// returns pointer to the internal buffer (will be overwritten on next call)
char* read_line(FILE* stream);


// path comparison
bool is_parent_path(const char* parent_path, const char* child_path);
