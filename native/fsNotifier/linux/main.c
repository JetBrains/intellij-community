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

#include "fsnotifier.h"

#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/inotify.h>
#include <sys/select.h>
#include <syslog.h>
#include <unistd.h>

#define LOG_ENV "FSNOTIFIER_LOG_LEVEL"
#define LOG_ENV_DEBUG "debug"
#define LOG_ENV_INFO "info"
#define LOG_ENV_WARNING "warning"
#define LOG_ENV_ERROR "error"
#define LOG_ENV_OFF "off"

#define USAGE_MSG \
    "fsnotifier - IntelliJ IDEA companion program for watching and reporting file and directory structure modifications.\n\n" \
    "fsnotifier utilizes \"user\" facility of syslog(3) - messages usually can be found in /var/log/user.log.\n" \
    "Verbosity is regulated via " LOG_ENV " environment variable, possible values are: " \
    LOG_ENV_DEBUG ", " LOG_ENV_INFO ", " LOG_ENV_WARNING ", " LOG_ENV_ERROR ", " LOG_ENV_OFF "; latter is the default.\n"

#define INOTIFY_LIMIT_MSG \
    "The current <b>inotify</b>(7) watch limit of %d is too low. " \
    "<a href=\"http://confluence.jetbrains.net/display/IDEADEV/Inotify+Watches+Limit\">More details.</a>\n"

typedef struct {
  char* name;
  int id;
} watch_root;

static array* roots = NULL;

static bool show_warning = true;

#define CHECK_NULL(p) if (p == NULL)  { syslog(LOG_ERR, "out of memory"); return false; }

static void init_log();
static void main_loop();
static bool read_input();
static bool update_roots(array* new_roots);
static void unregister_roots();
static bool register_roots(array* new_roots, array* unwatchable);
static bool unwatchable_mounts(array* mounts);
static void inotify_callback(char* path, int event);


int main(int argc, char** argv) {
  if (argc == 2 && strcmp(argv[1], "--help") == 0) {
    printf(USAGE_MSG);
    return 0;
  }

  init_log();
  syslog(LOG_INFO, "started");

  setvbuf(stdin, NULL, _IONBF, 0);
  setvbuf(stdout, NULL, _IONBF, 0);

  roots = array_create(20);
  if (init_inotify() && roots != NULL) {
    set_inotify_callback(&inotify_callback);
    main_loop();
    unregister_roots();
  }
  else {
    printf("GIVEUP\n");
  }
  close_inotify();
  array_delete(roots);

  syslog(LOG_INFO, "finished");
  closelog();

  return 0;
}


static void init_log() {
  char* env_level = getenv(LOG_ENV);
  int level = LOG_EMERG;
  if (env_level != NULL) {
    if (strcmp(env_level, LOG_ENV_DEBUG) == 0)  level = LOG_DEBUG;
    else if (strcmp(env_level, LOG_ENV_INFO) == 0)  level = LOG_INFO;
    else if (strcmp(env_level, LOG_ENV_WARNING) == 0)  level = LOG_WARNING;
    else if (strcmp(env_level, LOG_ENV_ERROR) == 0)  level = LOG_ERR;
  }

  char ident[32];
  snprintf(ident, sizeof(ident), "fsnotifier[%d]", getpid());
  openlog(ident, 0, LOG_USER);
  setlogmask(LOG_UPTO(level));
}


static void main_loop() {
  int input_fd = fileno(stdin), inotify_fd = get_inotify_fd();
  int nfds = (inotify_fd > input_fd ? inotify_fd : input_fd) + 1;
  fd_set rfds;
  bool go_on = true;

  while (go_on) {
    FD_ZERO(&rfds);
    FD_SET(input_fd, &rfds);
    FD_SET(inotify_fd, &rfds);
    if (select(nfds, &rfds, NULL, NULL, NULL) < 0) {
      syslog(LOG_ERR, "select: %s", strerror(errno));
      go_on = false;
    }
    else if (FD_ISSET(input_fd, &rfds)) {
      go_on = read_input();
    }
    else if (FD_ISSET(inotify_fd, &rfds)) {
      go_on = process_inotify_input();
    }
  }
}


static bool read_input() {
  char* line = read_line(stdin);
  syslog(LOG_DEBUG, "input: %s", (line ? line : "<null>"));

  if (line == NULL || strcmp(line, "EXIT") == 0) {
    return false;
  }

  if (strcmp(line, "ROOTS") == 0) {
    array* new_roots = array_create(20);
    CHECK_NULL(new_roots);

    while (1) {
      line = read_line(stdin);
      syslog(LOG_DEBUG, "input: %s", (line ? line : "<null>"));
      if (line == NULL || strlen(line) == 0) {
        return false;
      }
      else if (strcmp(line, "#") == 0) {
        break;
      }
      else {
        if (line[0] == '|')  line++;  // flat roots will be differentiated later

        int l = strlen(line);
        if (l > 1 && line[l-1] == '/')  line[l-1] = '\0';

        CHECK_NULL(array_push(new_roots, strdup(line)));
      }
    }

    return update_roots(new_roots);
  }

  return true;
}


static bool update_roots(array* new_roots) {
  syslog(LOG_INFO, "updating roots (curr:%d, new:%d)", array_size(roots), array_size(new_roots));

  unregister_roots();
  if (array_size(new_roots) == 0) {
    return true;
  }
  else if (array_size(new_roots) == 1 && strcmp(array_get(new_roots, 0), "/") == 0) {  // refuse to watch entire tree
    printf("UNWATCHEABLE\n/\n#\n");
    syslog(LOG_INFO, "unwatchable: /");
    array_delete_vs_data(new_roots);
    return true;
  }

  array* unwatchable = array_create(20);
  CHECK_NULL(unwatchable);
  if (!unwatchable_mounts(unwatchable)) {
    return false;
  }

  if (!register_roots(new_roots, unwatchable)) {
    return false;
  }

  // todo: sort/optimize list
  printf("UNWATCHEABLE\n");
  for (int i=0; i<array_size(unwatchable); i++) {
    char* s = array_get(unwatchable, i);
    printf("%s\n", s);
    syslog(LOG_INFO, "unwatchable: %s", s);
  }
  printf("#\n");

  array_delete_vs_data(unwatchable);
  array_delete(new_roots);

  return true;
}


static void unregister_roots() {
  watch_root* root;
  while ((root = array_pop(roots)) != NULL) {
    syslog(LOG_INFO, "unregistering root: %s\n", root->name);
    unwatch(root->id);
    free(root->name);
    free(root);
  };
}


static bool register_roots(array* new_roots, array* unwatchable) {
  for (int i=0; i<array_size(new_roots); i++) {
    char* new_root = array_get(new_roots, i);
    syslog(LOG_INFO, "registering root: %s\n", new_root);
    int id = watch(new_root, unwatchable);
    if (id == ERR_ABORT) {
      return false;
    }
    else if (id >= 0) {
      watch_root* root = malloc(sizeof(watch_root));
      CHECK_NULL(root);
      root->id = id;
      root->name = new_root;
      CHECK_NULL(array_push(roots, root));
    }
    else {
      if (show_warning && watch_limit_reached()) {
        int limit = get_watch_count();
        syslog(LOG_WARNING, "watch limit (%d) reached", limit);
        printf("MESSAGE\n" INOTIFY_LIMIT_MSG, limit);
        show_warning = false;  // warn only once
      }
      CHECK_NULL(array_push(unwatchable, new_root));
    }
  }

  return true;
}

static bool is_watchable(const char* dev, const char* mnt, const char* fs) {
  // don't watch special and network filesystems
  return !(strncmp(mnt, "/dev", 4) == 0 || strncmp(mnt, "/proc", 5) == 0 || strncmp(mnt, "/sys", 4) == 0 ||
           strcmp(fs, "fuse.gvfs-fuse-daemon") == 0 || strcmp(fs, "cifs") == 0 || strcmp(fs, "nfs") == 0);
}

#define MTAB_DELIMS " \t"

static bool unwatchable_mounts(array* mounts) {
  FILE* mtab = fopen("/etc/mtab", "r");
  if (mtab == NULL) {
    mtab = fopen("/proc/mounts", "r");
  }
  if (mtab == NULL) {
    syslog(LOG_ERR, "neither /etc/mtab nor /proc/mounts can be read");
    return false;
  }

  char* line;
  while ((line = read_line(mtab)) != NULL) {
    syslog(LOG_DEBUG, "mtab: %s", line);
    char* dev = strtok(line, MTAB_DELIMS);
    char* point = strtok(NULL, MTAB_DELIMS);
    char* fs = strtok(NULL, MTAB_DELIMS);

    if (dev == NULL || point == NULL || fs == NULL) {
      syslog(LOG_ERR, "can't parse mount line");
      return false;
    }

    if (!is_watchable(dev, point, fs)) {
      CHECK_NULL(array_push(mounts, strdup(point)));
    }
  }

  fclose(mtab);
  return true;
}


static void inotify_callback(char* path, int event) {
  if (event & IN_CREATE || event & IN_MOVED_TO) {
    printf("CREATE\n%s\n", path);
    syslog(LOG_DEBUG, "CREATE: %s", path);
    return;
  }

  if (event & IN_MODIFY) {
    printf("CHANGE\n%s\n", path);
    syslog(LOG_DEBUG, "CHANGE: %s", path);
    return;
  }

  if (event & IN_ATTRIB) {
    printf("STATS\n%s\n", path);
    syslog(LOG_DEBUG, "STATS: %s", path);
    return;
  }

  if (event & IN_DELETE || event & IN_MOVED_FROM) {
    printf("DELETE\n%s\n", path);
    syslog(LOG_DEBUG, "DELETE: %s", path);
    return;
  }

  if (event & IN_UNMOUNT) {
    printf("RESET\n");
    syslog(LOG_DEBUG, "RESET");
    return;
  }
}
