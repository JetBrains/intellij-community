/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef __FSNOTIFIER_H
#define __FSNOTIFIER_H

#include <stdbool.h>
#include <stdio.h>


// logging
void userlog(int priority, const char* format, ...);


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


// key/value pairs table
typedef struct __table table;

table* table_create(int capacity);
void* table_put(table* t, int key, void* value);
void* table_get(table* t, int key);
void table_delete(table* t);


// inotify subsystem
enum {
  ERR_IGNORE = -1,
  ERR_CONTINUE = -2,
  ERR_ABORT = -3
};

bool init_inotify();
void set_inotify_callback(void (* callback)(char*, int));
int get_inotify_fd();
int get_watch_count();
bool watch_limit_reached();
int watch(const char* root, array* ignores);
void unwatch(int id);
bool process_inotify_input();
void close_inotify();


// reads one line from stream, trims trailing carriage return if any
// returns pointer to the internal buffer (will be overwriten on next call)
char* read_line(FILE* stream);

#endif
