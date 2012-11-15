/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

#include <dirent.h>
#include <errno.h>
#include <linux/limits.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/inotify.h>
#include <sys/stat.h>
#include <syslog.h>
#include <unistd.h>


#define WATCH_COUNT_NAME "/proc/sys/fs/inotify/max_user_watches"

#define DEFAULT_SUBDIR_COUNT 5

typedef struct __watch_node {
  char* name;
  int wd;
  struct __watch_node* parent;
  array* kids;
} watch_node;

static int inotify_fd = -1;
static int watch_count = 0;
static table* watches;
static bool limit_reached = false;
static void (* callback)(char*, int) = NULL;

#define EVENT_SIZE (sizeof(struct inotify_event))
#define EVENT_BUF_LEN (2048 * (EVENT_SIZE + 16))
static char event_buf[EVENT_BUF_LEN];


static void read_watch_descriptors_count() {
  FILE* f = fopen(WATCH_COUNT_NAME, "r");
  if (f == NULL) {
    userlog(LOG_ERR, "can't open %s: %s", WATCH_COUNT_NAME, strerror(errno));
    return;
  }

  char* str = read_line(f);
  if (str == NULL) {
    userlog(LOG_ERR, "can't read from %s", WATCH_COUNT_NAME);
  }
  else {
    watch_count = atoi(str);
  }

  fclose(f);
}


bool init_inotify() {
  inotify_fd = inotify_init();
  if (inotify_fd < 0) {
    userlog(LOG_ERR, "inotify_init: %s", strerror(errno));
    return false;
  }
  userlog(LOG_DEBUG, "inotify fd: %d", get_inotify_fd());

  read_watch_descriptors_count();
  if (watch_count <= 0) {
    close(inotify_fd);
    inotify_fd = -1;
    return false;
  }
  userlog(LOG_INFO, "inotify watch descriptors: %d", watch_count);

  watches = table_create(watch_count);
  if (watches == NULL) {
    userlog(LOG_ERR, "out of memory");
    close(inotify_fd);
    inotify_fd = -1;
    return false;
  }

  return true;
}


inline void set_inotify_callback(void (* _callback)(char*, int)) {
  callback = _callback;
}


inline int get_inotify_fd() {
  return inotify_fd;
}


inline int get_watch_count() {
  return watch_count;
}


inline bool watch_limit_reached() {
  return limit_reached;
}


#define EVENT_MASK IN_MODIFY | IN_ATTRIB | IN_CREATE | IN_DELETE | IN_MOVE | IN_DELETE_SELF

static int add_watch(const char* path, watch_node* parent) {
  int wd = inotify_add_watch(inotify_fd, path, EVENT_MASK);
  if (wd < 0) {
    if (errno == EACCES || errno == ENOENT) {
      userlog(LOG_DEBUG, "inotify_add_watch(%s): %s", path, strerror(errno));
      return ERR_IGNORE;
    }
    else if (errno == ENOSPC) {
      userlog(LOG_WARNING, "inotify_add_watch(%s): %s", path, strerror(errno));
      limit_reached = true;
      return ERR_CONTINUE;
    }
    else {
      userlog(LOG_ERR, "inotify_add_watch(%s): %s", path, strerror(errno));
      return ERR_ABORT;
    }
  }
  else {
    userlog(LOG_DEBUG, "watching %s: %d", path, wd);
  }

  watch_node* node = table_get(watches, wd);
  if (node != NULL) {
    if (node->wd != wd) {
      userlog(LOG_ERR, "table error: corruption at %d:%s / %d:%s)", wd, path, node->wd, node->name);
      return ERR_ABORT;
    }
    else if (strcmp(node->name, path) != 0) {
      char buf1[PATH_MAX], buf2[PATH_MAX];
      const char* normalized1 = realpath(node->name, buf1);
      const char* normalized2 = realpath(path, buf2);
      if (normalized1 == NULL || normalized2 == NULL || strcmp(normalized1, normalized2) != 0) {
        userlog(LOG_ERR, "table error: collision at %d (new %s, existing %s)", wd, path, node->name);
        return ERR_ABORT;
      }
      else {
        userlog(LOG_INFO, "intersection at %d: (new %s, existing %s, real %s)", wd, path, node->name, normalized1);
        return ERR_IGNORE;
      }
    }

    return wd;
  }

  node = malloc(sizeof(watch_node));

  CHECK_NULL(node, ERR_ABORT);
  node->name = strdup(path);
  CHECK_NULL(node->name, ERR_ABORT);
  node->wd = wd;
  node->parent = parent;
  node->kids = NULL;

  if (parent != NULL) {
    if (parent->kids == NULL) {
      parent->kids = array_create(DEFAULT_SUBDIR_COUNT);
      CHECK_NULL(parent->kids, ERR_ABORT);
    }
    CHECK_NULL(array_push(parent->kids, node), ERR_ABORT);
  }

  if (table_put(watches, wd, node) == NULL) {
    userlog(LOG_ERR, "table error: unable to put (%d:%s)", wd, path);
    return ERR_ABORT;
  }

  return wd;
}


static void rm_watch(int wd, bool update_parent) {
  watch_node* node = table_get(watches, wd);
  if (node == NULL) {
    return;
  }

  userlog(LOG_DEBUG, "unwatching %s: %d (%p)", node->name, node->wd, node);

  if (inotify_rm_watch(inotify_fd, node->wd) < 0) {
    userlog(LOG_DEBUG, "inotify_rm_watch(%d:%s): %s", node->wd, node->name, strerror(errno));
  }

  for (int i=0; i<array_size(node->kids); i++) {
    watch_node* kid = array_get(node->kids, i);
    if (kid != NULL) {
      rm_watch(kid->wd, false);
    }
  }

  if (update_parent && node->parent != NULL) {
    for (int i=0; i<array_size(node->parent->kids); i++) {
      if (array_get(node->parent->kids, i) == node) {
        array_put(node->parent->kids, i, NULL);
        break;
      }
    }
  }

  free(node->name);
  array_delete(node->kids);
  free(node);
  table_put(watches, wd, NULL);
}


static int walk_tree(const char* path, watch_node* parent, bool recursive, array* mounts) {
  for (int j=0; j<array_size(mounts); j++) {
    char* mount = array_get(mounts, j);
    if (strncmp(path, mount, strlen(mount)) == 0) {
      userlog(LOG_DEBUG, "watch path '%s' crossed mount point '%s' - skipping", path, mount);
      return ERR_IGNORE;
    }
  }

  DIR* dir = NULL;
  if (recursive) {
    if ((dir = opendir(path)) == NULL) {
      if (errno == EACCES || errno == ENOENT || errno == ENOTDIR) {
        userlog(LOG_DEBUG, "opendir(%s): %d", path, errno);
        return ERR_IGNORE;
      }
      else {
        userlog(LOG_ERR, "opendir(%s): %s", path, strerror(errno));
        return ERR_CONTINUE;
      }
    }
  }

  int id = add_watch(path, parent);

  if (dir == NULL) {
    return id;
  }
  else if (id < 0) {
    closedir(dir);
    return id;
  }

  char subdir[PATH_MAX];
  strcpy(subdir, path);
  if (subdir[strlen(subdir) - 1] != '/') {
    strcat(subdir, "/");
  }
  char* p = subdir + strlen(subdir);

  struct dirent* entry;
  while ((entry = readdir(dir)) != NULL) {
    if (entry->d_type != DT_DIR ||
        strcmp(entry->d_name, ".") == 0 || strcmp(entry->d_name, "..") == 0) {
      continue;
    }

    strcpy(p, entry->d_name);

    int subdir_id = walk_tree(subdir, table_get(watches, id), recursive, mounts);
    if (subdir_id < 0 && subdir_id != ERR_IGNORE) {
      rm_watch(id, true);
      id = subdir_id;
      break;
    }
  }

  closedir(dir);
  return id;
}


int watch(const char* root, array* mounts) {
  bool recursive = true;
  if (root[0] == '|') {
    root++;
    recursive = false;
  }

  struct stat st;
  if (stat(root, &st) != 0) {
    if (errno == EACCES) {
      return ERR_IGNORE;
    }
    else if (errno == ENOENT) {
      return ERR_CONTINUE;
    }
    userlog(LOG_ERR, "stat(%s): %s", root, strerror(errno));
    return ERR_ABORT;
  }

  if (S_ISREG(st.st_mode)) {
    recursive = false;
  }
  else if (!S_ISDIR(st.st_mode)) {
    userlog(LOG_WARNING, "unexpected node type: %s, %d", root, st.st_mode);
    return ERR_IGNORE;
  }

  return walk_tree(root, NULL, recursive, mounts);
}


void unwatch(int id) {
  rm_watch(id, true);
}


static bool process_inotify_event(struct inotify_event* event) {
  watch_node* node = table_get(watches, event->wd);
  if (node == NULL) {
    return true;
  }

  bool is_dir = (event->mask & IN_ISDIR) == IN_ISDIR;
  userlog(LOG_DEBUG, "inotify: wd=%d mask=%d dir=%d name=%s", event->wd, event->mask & (~IN_ISDIR), is_dir, node->name);

  char path[PATH_MAX];
  strcpy(path, node->name);
  if (event->len > 0) {
    if (path[strlen(path) - 1] != '/') {
      strcat(path, "/");
    }
    strcat(path, event->name);
  }

  if (is_dir && ((event->mask & IN_CREATE) == IN_CREATE || (event->mask & IN_MOVED_TO) == IN_MOVED_TO)) {
    int result = walk_tree(path, node, true, NULL);
    if (result < 0 && result != ERR_IGNORE && result != ERR_CONTINUE) {
      return false;
    }
  }

  if (is_dir && ((event->mask & IN_DELETE) == IN_DELETE || (event->mask & IN_MOVED_FROM) == IN_MOVED_FROM)) {
    for (int i=0; i<array_size(node->kids); i++) {
      watch_node* kid = array_get(node->kids, i);
      if (kid != NULL && strcmp(kid->name, path) == 0) {
        rm_watch(kid->wd, false);
        array_put(node->kids, i, NULL);
        break;
      }
    }
  }

  if (callback != NULL) {
    (*callback)(path, event->mask);
  }
  return true;
}


bool process_inotify_input() {
  ssize_t len = read(inotify_fd, event_buf, EVENT_BUF_LEN);
  if (len < 0) {
    userlog(LOG_ERR, "read: %s", strerror(errno));
    return false;
  }

  int i = 0;
  while (i < len) {
    struct inotify_event* event = (struct inotify_event*) &event_buf[i];
    i += EVENT_SIZE + event->len;

    if (event->mask & IN_IGNORED) {
      continue;
    }
    if (event->mask & IN_Q_OVERFLOW) {
      userlog(LOG_ERR, "event queue overflow");
      continue;
    }

    if (!process_inotify_event(event)) {
      return false;
    }
  }

  return true;
}


void close_inotify() {
  if (watches != NULL) {
    table_delete(watches);
  }

  if (inotify_fd >= 0) {
    close(inotify_fd);
  }
}
