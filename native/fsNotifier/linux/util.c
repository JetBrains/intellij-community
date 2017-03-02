/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

#include "fsnotifier.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>


#define REALLOC_FACTOR 2

#define REMOVED_ELEMENTS_THRESHOLD 0.1

#define STEP 1

struct __array {
  void** data;
  int size;
  int capacity;
};

static bool array_realloc(array* a) {
  if (a->size == a->capacity) {
    int new_cap = a->capacity * REALLOC_FACTOR;
    void* new_ptr = realloc(a->data, sizeof(void*) * new_cap);
    if (new_ptr == NULL) {
      return false;
    }
    a->capacity = new_cap;
    a->data = new_ptr;
  }
  return true;
}

array* array_create(int initial_capacity) {
  array* a = calloc(1, sizeof(array));
  if (a == NULL) {
    return NULL;
  }

  a->data = calloc(initial_capacity, sizeof(void*));
  if (a->data == NULL) {
    free(a);
    return NULL;
  }

  a->capacity = initial_capacity;
  a->size = 0;

  return a;
}

inline int array_size(array* a) {
  return (a != NULL ? a->size : 0);
}

void* array_push(array* a, void* element) {
  if (a == NULL || !array_realloc(a)) {
    return NULL;
  }
  a->data[a->size++] = element;
  return element;
}

void* array_pop(array* a) {
  if (a != NULL && a->size > 0) {
    return a->data[--a->size];
  }
  else {
    return NULL;
  }
}

void array_put(array* a, int index, void* element) {
  if (a != NULL && index >=0 && index < a->capacity) {
    a->data[index] = element;
    if (a->size <= index) {
      a->size = index + 1;
    }
  }
}

void* array_get(array* a, int index) {
  if (a != NULL && index >= 0 && index < a->size) {
    return a->data[index];
  }
  else {
    return NULL;
  }
}

void array_delete(array* a) {
  if (a != NULL) {
    free(a->data);
    free(a);
  }
}

void array_delete_vs_data(array* a) {
  if (a != NULL) {
    array_delete_data(a);
    array_delete(a);
  }
}

void array_delete_data(array* a) {
  if (a != NULL) {
    for (int i=0; i<a->size; i++) {
      if (a->data[i] != NULL) {
        free(a->data[i]);
      }
    }
    a->size = 0;
  }
}


struct __table {
  void** data;
  int capacity;
};

table* table_create(int capacity) {
  table* t = calloc(1, sizeof(table));
  if (t == NULL) {
    return NULL;
  }

  t->data = calloc(capacity, sizeof(void*));
  if (t->data == NULL) {
    free(t);
    return NULL;
  }

  t->capacity = capacity;

  return t;
}

static inline int wrap(int key, table* t) {
  return (t != NULL ? key % t->capacity : -1);
}

// todo: resolve collisions (?)
void* table_put(table* t, int key, void* value) {
  int k = wrap(key, t);
  if (k < 0 || (value != NULL && t->data[k] != NULL)) {
    return NULL;
  }
  else {
    return t->data[k] = value;
  }
}

void* table_get(table* t, int key) {
  int k = wrap(key, t);
  if (k < 0) {
    return NULL;
  }
  else {
    return t->data[k];
  }
}

void table_delete(table* t) {
  if (t != NULL) {
    free(t->data);
    free(t);
  }
}


static char* REMOVED_PLACEHOLDER = "";

// linear-probing based hashset
struct __set {
  char** data;
  int capacity;
  int elements;
  int removed;
};

static unsigned long hash(char *str);

static bool set_realloc(set* s, int new_cap);

set* set_create(int capacity) {
  if (capacity < 1) {
    capacity = 1;
  }
  set* s = calloc(1, sizeof(set));
  if (s == NULL) {
    return NULL;
  }

  s->data = calloc(capacity, sizeof(char*));
  if (s->data == NULL) {
    free(s);
    return NULL;
  }

  s->capacity = capacity;
  s->elements = 0;
  s->removed = 0;

  return s;
}

void set_delete(set* s) {
  if (s != NULL) {
    free(s->data);
    free(s);
  }
}

void set_delete_data(set* s) {
  if (s != NULL) {
    for (int i=0; i<s->capacity; i++) {
      if (s->data[i] != NULL) {
        free(s->data[i]);
      }
    }
    s->elements = 0;
    s->removed = 0;
  }
}

void set_delete_vs_data(set* a) {
  if (a != NULL) {
    set_delete_data(a);
    set_delete(a);
  }
}

bool set_add(set* s, char* elem) {
  if (s == NULL) {
    return false;
  }

  if (s->capacity < REALLOC_FACTOR*(s->elements + s->removed)) {
    set_realloc(s, s->capacity*REALLOC_FACTOR);
  }

  int index = hash(elem) % s->capacity;
  while (s->data[index] != NULL && s->data[index] != REMOVED_PLACEHOLDER)  {
    if (strcmp(s->data[index], elem) == 0) {
      return false;
    }
    index = (index + STEP) % s->capacity;
  }

  if (s->data[index] != NULL && s->data[index] == REMOVED_PLACEHOLDER) {
    s->removed--;
  }

  s->data[index] = elem;
  s->elements++;

  return true;
}

int set_size(set* s) {
  if (s == NULL) {
    return 0;
  }
  return s->elements;
}

bool set_contains(set* s, char* elem) {
  if (s == NULL || elem == NULL) {
    return false;
  }

  int index = hash(elem) % s->capacity;
  int i = 0;

  while (s->data[index] != NULL && i++ < s->capacity ) {
    if (strcmp(s->data[index], elem) == 0) {
      return true;
    }
    index = (index + STEP) % s->capacity;
  }

  return false;
}

bool set_remove(set* s, char* elem) {
  if (s == NULL || elem == NULL) {
    return false;
  }
  int index = hash(elem) % s->capacity;
  while (s->data[index] != NULL) {
    if (strcmp(s->data[index], elem) == 0) {
      s->data[index] = REMOVED_PLACEHOLDER;
      s->removed++;
      s->elements--;
      if (s->removed > REMOVED_ELEMENTS_THRESHOLD*s->capacity) {
        set_realloc(s, s->capacity);
      }
      return true;
    }
    index = (index + STEP) % s->capacity;
  }
  return false;
}

// djb2
static inline unsigned long hash(char *str){
  unsigned long hash = 5381;

  int c = *str++;
  while (c != 0) {
    hash = ((hash << 5) + hash) + c; /* hash * 33 + c */
    c = *str++;
  }

  return hash;
}

static bool set_realloc(set* s, int new_cap) {
  if (s == NULL) {
    return false;
  }
  int old_capacity = s->capacity;
  int old_elements = s->elements;
  int old_removed = s->removed;
  char** old_data = s->data;

  char** new_data = (char**) calloc(new_cap, sizeof(char*));
  if (new_data == NULL) {
    return false;
  }

  s->data = new_data;
  s->capacity = new_cap;
  s->elements = 0;
  s->removed = 0;

  for (int i = 0; i < old_capacity; i++) {
    if (old_data[i] != NULL && old_data[i] != REMOVED_PLACEHOLDER ) {
      if (!set_add(s, old_data[i])) {
        s->data=old_data;
        s->capacity=old_capacity;
        s->elements=old_elements;
        s->removed=old_removed;
        free(new_data);
        return false;
      }
    }
  }

  free(old_data);

  return true;

}


struct __set_iterator {
  set* s;
  int i;
};

set_iterator* set_itr(set* set) {
  if (set == NULL) {
    return NULL;
  }
  set_iterator* it = calloc(1, sizeof(set_iterator));
  it->s=set;
  it->i=0;
  set_itr_has_next(it);
  return it;
}

bool set_itr_next(set_iterator* it, char** ptr) {
  if (it == NULL) {
    return false;
  }

  set* s = it->s;

  while (it->i < s->capacity) {
    if (s->data[it->i] != NULL && s->data[it->i] != REMOVED_PLACEHOLDER) {
      *ptr = s->data[it->i++];
      return true;
    }
    it->i++;
  }

  return false;
}

bool set_itr_has_next(set_iterator* it) {
  if (it == NULL) {
    return false;
  }

  set* s = it->s;

  while (it->i < s->capacity) {
    if (s->data[it->i] != NULL && s->data[it->i] != REMOVED_PLACEHOLDER) {
      return true;
    }
    it->i++;
  }

  return false;

}

void set_itr_delete(set_iterator* it) {
  free(it);
}

bool set_difference(set* s1, set* s2, set* diff) {
  if (s1 == NULL || s2 == NULL || diff == NULL) {
    return false;
  }
  set_iterator* it;
  char* elem = NULL;
  for (it = set_itr(s2); set_itr_next(it, &elem); ) {
    if (!set_contains(s1, elem)) {
      if (!set_add(diff, elem)) {
        return false;
      }
    }
  }

  set_itr_delete(it);

  return true;
}


#define INPUT_BUF_LEN 2048
static char input_buf[INPUT_BUF_LEN];

char* read_line(FILE* stream) {
  char* retval = fgets(input_buf, INPUT_BUF_LEN, stream);
  if (retval == NULL || feof(stream)) {
    return NULL;
  }
  int pos = strlen(input_buf) - 1;
  if (input_buf[pos] == '\n') {
    input_buf[pos] = '\0';
  }
  return input_buf;
}


bool is_parent_path(const char* parent_path, const char* child_path) {
  size_t parent_len = strlen(parent_path);
  return strncmp(parent_path, child_path, parent_len) == 0 &&
         (parent_len == strlen(child_path) || child_path[parent_len] == '/');
}
