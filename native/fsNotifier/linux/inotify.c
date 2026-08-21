// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

#include "fsnotifier.h"

#include <dirent.h>
#include <errno.h>
#include <limits.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/inotify.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>


#define WATCH_COUNT_NAME "/proc/sys/fs/inotify/max_user_watches"

#define DEFAULT_SUBDIR_COUNT 5
#define ADD_WATCH_ATTEMPTS 3

typedef struct watch_node_str {
  int wd;
  dev_t device;
  ino_t inode;
  struct watch_node_str* parent;
  array* kids;
  unsigned int path_len;
  struct watch_node_str* prev;
  struct watch_node_str* next;
  char path[];
} watch_node;

static int inotify_fd = -1;
static int watch_count = 0;
static table* watches;
static bool limit_reached = false;
static void (* callback)(const char*, uint32_t) = NULL;

#define EVENT_SIZE (sizeof(struct inotify_event))
#define EVENT_BUF_LEN (2048 * (EVENT_SIZE + 16))
static char event_buf[EVENT_BUF_LEN];

static char path_buf[2 * PATH_MAX];

static void read_watch_descriptors_count(void);
static void watch_limit_reached(void);
static int path_error_result(int error, bool root);
static bool watch_node_aliases_match(const watch_node* node);
#ifdef FSNOTIFIER_TESTING
static void run_add_watch_test_hook(void);
#endif


bool init_inotify(void) {
  inotify_fd = inotify_init();
  if (inotify_fd < 0) {
    int e = errno;
    userlog(LOG_ERR, "inotify_init: %s", strerror(e));
    if (e == EMFILE) {
      message("inotify.instance.limit");
    }
    return false;
  }

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

static void read_watch_descriptors_count(void) {
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
    watch_count = (int)strtol(str, NULL, 10);
  }

  fclose(f);
}


void set_inotify_callback(void (* _callback)(const char*, uint32_t)) {
  callback = _callback;
}


int get_inotify_fd(void) {
  return inotify_fd;
}


#define EVENT_MASK (IN_MODIFY | IN_ATTRIB | IN_CREATE | IN_DELETE | IN_MOVE | IN_DELETE_SELF | IN_MOVE_SELF)

static bool is_same_file_system_node(const watch_node* expected, const char* path) {
  struct stat actual;
  return stat(path, &actual) == 0 && actual.st_dev == expected->device && actual.st_ino == expected->inode;
}


static int add_watch(unsigned int path_len, watch_node* parent, watch_node** result) {
  int wd = -1;
  watch_node* existing = NULL;
  struct stat path_stat;
  for (int attempt = 0; attempt < ADD_WATCH_ATTEMPTS; attempt++) {
    struct stat before;
    if (stat(path_buf, &before) != 0) {
      int e = errno;
      userlog(LOG_INFO, "stat(%s): %s", path_buf, strerror(e));
      return path_error_result(e, parent == NULL);
    }

    wd = inotify_add_watch(inotify_fd, path_buf, EVENT_MASK);
    if (wd < 0) {
      int e = errno;
      if (e == EACCES || e == ENOENT) {
        userlog(LOG_INFO, "inotify_add_watch(%s): %s", path_buf, strerror(e));
        return path_error_result(e, parent == NULL);
      }
      else if (e == ENOSPC) {
        userlog(LOG_WARNING, "inotify_add_watch(%s): %s", path_buf, strerror(e));
        watch_limit_reached();
        return ERR_CONTINUE;
      }
      else {
        userlog(LOG_ERR, "inotify_add_watch(%s): %s", path_buf, strerror(e));
        return ERR_ABORT;
      }
    }
    else {
      userlog(LOG_INFO, "watching %s: %d", path_buf, wd);
    }

    existing = table_get(watches, wd);
#ifdef FSNOTIFIER_TESTING
    run_add_watch_test_hook();
#endif

    if (stat(path_buf, &path_stat) != 0) {
      int e = errno;
      userlog(LOG_INFO, "stat(%s): %s", path_buf, strerror(e));
      if (existing == NULL && inotify_rm_watch(inotify_fd, wd) < 0) {
        userlog(LOG_INFO, "inotify_rm_watch(%d:%s): %s", wd, path_buf, strerror(errno));
      }
      return path_error_result(e, parent == NULL);
    }

    if (before.st_dev == path_stat.st_dev && before.st_ino == path_stat.st_ino) {
      break;
    }

    userlog(LOG_INFO, "watch path changed during registration, retrying: %s", path_buf);
    if (existing == NULL && inotify_rm_watch(inotify_fd, wd) < 0) {
      userlog(LOG_INFO, "inotify_rm_watch(%d:%s): %s", wd, path_buf, strerror(errno));
    }
    wd = -1;
  }

  if (wd < 0) {
    return parent == NULL ? ERR_MISSING : ERR_IGNORE;
  }

  watch_node* tail = existing;
  if (existing != NULL) {
    char normalized_path[PATH_MAX];
    const char* normalized = realpath(path_buf, normalized_path);
    bool same_node_found = false;

    for (watch_node* node = existing; node != NULL; node = node->next) {
      if (node->wd != wd) {
        userlog(LOG_ERR, "table error: corruption at %d:%s / %d:%s / %d", wd, path_buf, node->wd, node->path, watch_count);
        return ERR_ABORT;
      }
      bool same_node = path_stat.st_dev == node->device && path_stat.st_ino == node->inode;
      if (same_node && strcmp(node->path, path_buf) == 0) {
        *result = node;
        return wd;
      }

      char normalized_existing_path[PATH_MAX];
      const char* normalized_existing = realpath(node->path, normalized_existing_path);
      if (same_node && normalized != NULL && normalized_existing != NULL && strcmp(normalized, normalized_existing) == 0) {
        userlog(LOG_INFO, "intersection at %d: (new %s, existing %s, real %s)", wd, path_buf, node->path, normalized);
        return ERR_IGNORE;
      }

      if (same_node) {
        same_node_found = true;
      }
      tail = node;
    }

    if (!same_node_found) {
      userlog(LOG_ERR, "table error: collision at %d (new %s, existing %s)", wd, path_buf, existing->path);
      return ERR_ABORT;
    }
  }

  watch_node* node = malloc(sizeof(watch_node) + path_len + 1);
  CHECK_NULL(node, ERR_ABORT)
  memcpy(node->path, path_buf, path_len + 1);
  node->path_len = path_len;
  node->wd = wd;
  node->device = path_stat.st_dev;
  node->inode = path_stat.st_ino;
  node->parent = parent;
  node->kids = NULL;
  node->prev = tail;
  node->next = NULL;

  if (parent != NULL) {
    if (parent->kids == NULL) {
      parent->kids = array_create(DEFAULT_SUBDIR_COUNT);
      CHECK_NULL(parent->kids, ERR_ABORT)
    }
    CHECK_NULL(array_push(parent->kids, node), ERR_ABORT)
  }

  if (tail != NULL) {
    tail->next = node;
  }
  else if (table_put(watches, wd, node) == NULL) {
    userlog(LOG_ERR, "table error: unable to put (%d:%s)", wd, path_buf);
    return ERR_ABORT;
  }

  *result = node;
  return wd;
}

static int path_error_result(int error, bool root) {
  if (error == ENOENT) {
    return root ? ERR_MISSING : ERR_IGNORE;
  }
  if (error == EACCES) {
    return ERR_IGNORE;
  }
  return ERR_ABORT;
}

#ifdef FSNOTIFIER_TESTING
static void run_add_watch_test_hook(void) {
  static bool used = false;
  const char* fds = getenv("FSNOTIFIER_TEST_REGISTRATION_FDS");
  if (used || fds == NULL) {
    return;
  }

  const char* path = getenv("FSNOTIFIER_TEST_REGISTRATION_PATH");
  if (path != NULL && strcmp(path, path_buf) != 0) {
    return;
  }

  int ready_fd;
  int resume_fd;
  if (sscanf(fds, "%d:%d", &ready_fd, &resume_fd) != 2) {
    userlog(LOG_WARNING, "invalid registration test descriptors: %s", fds);
    return;
  }

  used = true;
  char signal = 0;
  if (write(ready_fd, &signal, 1) != 1 || read(resume_fd, &signal, 1) != 1) {
    userlog(LOG_WARNING, "registration test synchronization failed: %s", strerror(errno));
  }
}
#endif

static void watch_limit_reached(void) {
  if (!limit_reached) {
    limit_reached = true;
    message("inotify.watch.limit");
  }
}

static void rm_watch(watch_node* node, bool update_parent) {
  if (node == NULL) {
    return;
  }

  userlog(LOG_INFO, "unwatching %s: %d (%p)", node->path, node->wd, node);

  for (int i = 0; i < array_size(node->kids); i++) {
    watch_node* kid = array_get(node->kids, i);
    if (kid != NULL) {
      rm_watch(kid, false);
    }
  }

  if (update_parent && node->parent != NULL) {
    for (int i = 0; i < array_size(node->parent->kids); i++) {
      if (array_get(node->parent->kids, i) == node) {
        array_put(node->parent->kids, i, NULL);
        break;
      }
    }
  }

  bool last_alias = node->prev == NULL && node->next == NULL;
  if (node->prev != NULL) {
    node->prev->next = node->next;
  }
  else {
    table_set(watches, node->wd, node->next);
  }
  if (node->next != NULL) {
    node->next->prev = node->prev;
  }

  if (last_alias && inotify_rm_watch(inotify_fd, node->wd) < 0) {
    userlog(LOG_INFO, "inotify_rm_watch(%d:%s): %s", node->wd, node->path, strerror(errno));
  }

  array_delete(node->kids);
  free(node);
}


static int walk_tree(unsigned int path_len, watch_node* parent, bool recursive, array* mounts) {
  for (int j = 0; j < array_size(mounts); j++) {
    char* mount = array_get(mounts, j);
    if (strncmp(path_buf, mount, strlen(mount)) == 0) {
      userlog(LOG_INFO, "watch path '%s' crossed mount point '%s' - skipping", path_buf, mount);
      return ERR_IGNORE;
    }
  }

  DIR* dir = NULL;
  if (recursive) {
    if ((dir = opendir(path_buf)) == NULL) {
      if (errno == EACCES || errno == ENOENT || errno == ENOTDIR) {
        userlog(LOG_INFO, "opendir(%s): %d", path_buf, errno);
        return ERR_IGNORE;
      }
      else {
        userlog(LOG_ERR, "opendir(%s): %s", path_buf, strerror(errno));
        return ERR_CONTINUE;
      }
    }
  }

  watch_node* node;
  int id = add_watch(path_len, parent, &node);

  if (dir == NULL) {
    return id;
  }
  else if (id < 0) {
    closedir(dir);
    return id;
  }

  path_buf[path_len] = '/';

  struct dirent* entry;
  while ((entry = readdir(dir)) != NULL) {
    if (strcmp(entry->d_name, ".") == 0 || strcmp(entry->d_name, "..") == 0) {
      continue;
    }
    if (entry->d_type != DT_UNKNOWN && entry->d_type != DT_DIR) {
      continue;
    }

    unsigned int name_len = strlen(entry->d_name);
    memcpy(path_buf + path_len + 1, entry->d_name, name_len + 1);

    if (entry->d_type == DT_UNKNOWN) {
      struct stat st;
      if (stat(path_buf, &st) != 0) {
        userlog(LOG_INFO, "(DT_UNKNOWN) stat(%s): %d", path_buf, errno);
        continue;
      }
      if (!S_ISDIR(st.st_mode)) {
        continue;
      }
    }

    int subdir_id = walk_tree(path_len + 1 + name_len, node, recursive, mounts);
    if (subdir_id < 0 && subdir_id != ERR_IGNORE) {
      rm_watch(node, true);
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

  size_t path_len = strlen(root);
  if (root[path_len - 1] == '/') {
    --path_len;
  }

  struct stat st;
  if (stat(root, &st) != 0) {
    if (errno == ENOENT) {
      return ERR_MISSING;
    }
    else if (errno == EACCES || errno == ELOOP || errno == ENAMETOOLONG || errno == ENOTDIR) {
      userlog(LOG_INFO, "stat(%s): %s", root, strerror(errno));
      return ERR_CONTINUE;
    }
    else {
      userlog(LOG_ERR, "stat(%s): %s", root, strerror(errno));
      return ERR_ABORT;
    }
  }

  if (S_ISREG(st.st_mode)) {
    recursive = false;
  }
  else if (!S_ISDIR(st.st_mode)) {
    userlog(LOG_WARNING, "unexpected node type: %s, %d", root, st.st_mode);
    return ERR_IGNORE;
  }

  memcpy(path_buf, root, path_len);
  path_buf[path_len] = '\0';
  return walk_tree(path_len, NULL, recursive, mounts);
}


void unwatch(int id, const char* path) {
  for (watch_node* node = table_get(watches, id); node != NULL; node = node->next) {
    if (strcmp(node->path, path) == 0) {
      rm_watch(node, true);
      return;
    }
  }
}


bool watch_tree_matches(int id, const char* path) {
  for (watch_node* node = table_get(watches, id); node != NULL; node = node->next) {
    if (strcmp(node->path, path) == 0) {
      return is_same_file_system_node(node, path) && watch_node_aliases_match(node);
    }
  }
  return false;
}


static bool watch_node_aliases_match(const watch_node* node) {
  if ((node->prev != NULL || node->next != NULL) && !is_same_file_system_node(node, node->path)) {
    return false;
  }
  for (int i = 0; i < array_size(node->kids); i++) {
    watch_node* kid = array_get(node->kids, i);
    if (kid != NULL && !watch_node_aliases_match(kid)) {
      return false;
    }
  }
  return true;
}


static bool process_inotify_event(struct inotify_event* event) {
  watch_node* node = table_get(watches, event->wd);
  if (node == NULL) {
    return true;
  }

  while (node != NULL) {
    watch_node* next = node->next;
    if (event->mask & (IN_ATTRIB | IN_DELETE_SELF | IN_MOVE_SELF) &&
        !is_same_file_system_node(node, node->path)) {
      memcpy(path_buf, node->path, node->path_len + 1);
      rm_watch(node, true);
      if (callback != NULL) {
        (*callback)(path_buf, IN_DELETE_SELF);
      }
      node = next;
      continue;
    }

    uint32_t event_mask = event->mask & ~(IN_DELETE_SELF | IN_MOVE_SELF);
    if (event_mask == 0) {
      node = next;
      continue;
    }

    bool is_dir = (event->mask & IN_ISDIR) == IN_ISDIR;
    userlog(LOG_INFO, "inotify: wd=%d mask=%d dir=%d name=%s", event->wd, event_mask & (~IN_ISDIR), is_dir, node->path);

    unsigned int path_len = node->path_len;
    memcpy(path_buf, node->path, path_len + 1);
    if (event->len > 0) {
      path_buf[path_len] = '/';
      unsigned int name_len = strlen(event->name);
      memcpy(path_buf + path_len + 1, event->name, name_len + 1);
      path_len += name_len + 1;
    }

    if (callback != NULL) {
      (*callback)(path_buf, event_mask);
    }

    if (is_dir && event_mask & (IN_CREATE | IN_MOVED_TO)) {
      int result = walk_tree(path_len, node, true, NULL);
      if (result < 0 && result != ERR_IGNORE && result != ERR_CONTINUE) {
        return false;
      }
    }

    if (is_dir && event_mask & (IN_DELETE | IN_MOVED_FROM)) {
      for (int i = 0; i < array_size(node->kids); i++) {
        watch_node* kid = array_get(node->kids, i);
        if (kid != NULL && strcmp(path_buf, kid->path) == 0) {
          rm_watch(kid, false);
          array_put(node->kids, i, NULL);
          break;
        }
      }
    }

    node = next;
  }

  return true;
}


bool process_inotify_input(void) {
  ssize_t len = read(inotify_fd, event_buf, EVENT_BUF_LEN);
  if (len < 0) {
    userlog(LOG_ERR, "read: %s", strerror(errno));
    return false;
  }

  ssize_t i = 0;
  while (i < len) {
    struct inotify_event *event = (struct inotify_event *) &event_buf[i];
    i += (int)EVENT_SIZE + event->len;

    if (event->mask & IN_IGNORED) {
      continue;
    }
    if (event->mask & IN_Q_OVERFLOW) {
      userlog(LOG_INFO, "event queue overflow");
      continue;
    }

    if (!process_inotify_event(event)) {
      return false;
    }
  }

  return true;
}


void close_inotify(void) {
  if (watches != NULL) {
    table_delete(watches);
  }

  if (inotify_fd >= 0) {
    close(inotify_fd);
  }
}
