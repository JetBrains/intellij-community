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

#include <errno.h>
#include <limits.h>
#include <mntent.h>
#include <paths.h>
#include <stdarg.h>
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

#define VERSION "1.2"
#define VERSION_MSG "fsnotifier " VERSION "\n"

#define USAGE_MSG \
    "fsnotifier - IntelliJ IDEA companion program for watching and reporting file and directory structure modifications.\n\n" \
    "fsnotifier utilizes \"user\" facility of syslog(3) - messages usually can be found in /var/log/user.log.\n" \
    "Verbosity is regulated via " LOG_ENV " environment variable, possible values are: " \
    LOG_ENV_DEBUG ", " LOG_ENV_INFO ", " LOG_ENV_WARNING ", " LOG_ENV_ERROR ", " LOG_ENV_OFF "; latter is the default.\n\n" \
    "Use 'fsnotifier --selftest' to perform some self-diagnostics (output will be logged and printed to console).\n"

#define HELP_MSG \
    "Try 'fsnotifier --help' for more information.\n"

#define INOTIFY_LIMIT_MSG \
    "The current <b>inotify</b>(7) watch limit of %d is too low. " \
    "<a href=\"http://confluence.jetbrains.net/display/IDEADEV/Inotify+Watches+Limit\">More details.</a>\n"

typedef struct {
  char* name;
  int id;
} watch_root;

static array* roots = NULL;

static bool show_warning = true;
static bool self_test = false;

static void init_log();
static void run_self_test();
static void main_loop();
static bool read_input();
static bool update_roots(array* new_roots);
static void unregister_roots();
static bool register_roots(array* new_roots, array* unwatchable, array* mounts);
static array* unwatchable_mounts();
static void inotify_callback(char* path, int event);
static void output(const char* format, ...);


int main(int argc, char** argv) {
  if (argc > 1) {
    if (strcmp(argv[1], "--help") == 0) {
      printf(USAGE_MSG);
      return 0;
    }
    else if (strcmp(argv[1], "--version") == 0) {
      printf(VERSION_MSG);
      return 0;
    }
    else if (strcmp(argv[1], "--selftest") == 0) {
      self_test = true;
    }
    else {
      printf("unrecognized option: %s\n", argv[1]);
      printf(HELP_MSG);
      return 1;
    }
  }

  init_log();
  if (!self_test) {
    userlog(LOG_INFO, "started (v." VERSION ")");
  }
  else {
    userlog(LOG_INFO, "started (self-test mode) (v." VERSION ")");
  }

  setvbuf(stdin, NULL, _IONBF, 0);
  setvbuf(stdout, NULL, _IONBF, 0);

  roots = array_create(20);
  if (init_inotify() && roots != NULL) {
    set_inotify_callback(&inotify_callback);

    if (!self_test) {
      main_loop();
    }
    else {
      run_self_test();
    }

    unregister_roots();
  }
  else {
    printf("GIVEUP\n");
  }
  close_inotify();
  array_delete(roots);

  userlog(LOG_INFO, "finished");
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

  if (self_test) {
    level = LOG_DEBUG;
  }

  char ident[32];
  snprintf(ident, sizeof(ident), "fsnotifier[%d]", getpid());
  openlog(ident, 0, LOG_USER);
  setlogmask(LOG_UPTO(level));
}


void userlog(int priority, const char* format, ...) {
  va_list ap;

  va_start(ap, format);
  vsyslog(priority, format, ap);
  va_end(ap);

  if (self_test) {
    const char* level = "debug";
    switch (priority) {
      case LOG_ERR:  level = "error"; break;
      case LOG_WARNING:  level = " warn"; break;
      case LOG_INFO:  level = " info"; break;
    }
    printf("fsnotifier[%d] %s: ", getpid(), level);

    va_start(ap, format);
    vprintf(format, ap);
    va_end(ap);

    printf("\n");
  }
}


static void run_self_test() {
  array* test_roots = array_create(1);
  char* cwd = malloc(PATH_MAX);
  if (getcwd(cwd, PATH_MAX) == NULL) {
    strncpy(cwd, ".", PATH_MAX);
  }
  array_push(test_roots, cwd);
  update_roots(test_roots);
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
      userlog(LOG_ERR, "select: %s", strerror(errno));
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
  userlog(LOG_DEBUG, "input: %s", (line ? line : "<null>"));

  if (line == NULL || strcmp(line, "EXIT") == 0) {
    userlog(LOG_INFO, "exiting: %s", line);
    return false;
  }

  if (strcmp(line, "ROOTS") == 0) {
    array* new_roots = array_create(20);
    CHECK_NULL(new_roots, false);

    while (1) {
      line = read_line(stdin);
      userlog(LOG_DEBUG, "input: %s", (line ? line : "<null>"));
      if (line == NULL || strlen(line) == 0) {
        return false;
      }
      else if (strcmp(line, "#") == 0) {
        break;
      }
      else {
        int l = strlen(line);
        if (l > 1 && line[l-1] == '/')  line[l-1] = '\0';
        CHECK_NULL(array_push(new_roots, strdup(line)), false);
      }
    }

    return update_roots(new_roots);
  }

  if (strcmp(line, "VERSION") == 0) {
    output(VERSION "\n");
    return true;
  }

  userlog(LOG_INFO, "unrecognised command: %s", line);
  return true;
}


static bool update_roots(array* new_roots) {
  userlog(LOG_INFO, "updating roots (curr:%d, new:%d)", array_size(roots), array_size(new_roots));

  unregister_roots();

  if (array_size(new_roots) == 0) {
    output("UNWATCHEABLE\n#\n");
    array_delete(new_roots);
    return true;
  }
  else if (array_size(new_roots) == 1 && strcmp(array_get(new_roots, 0), "/") == 0) {  // refuse to watch entire tree
    output("UNWATCHEABLE\n/\n#\n");
    userlog(LOG_INFO, "unwatchable: /");
    array_delete_vs_data(new_roots);
    return true;
  }

  array* mounts = unwatchable_mounts();
  if (mounts == NULL) {
    return false;
  }

  array* unwatchable = array_create(20);
  if (!register_roots(new_roots, unwatchable, mounts)) {
    return false;
  }

  output("UNWATCHEABLE\n");
  for (int i=0; i<array_size(unwatchable); i++) {
    char* s = array_get(unwatchable, i);
    output("%s\n", s);
    userlog(LOG_INFO, "unwatchable: %s", s);
  }
  output("#\n");

  array_delete_vs_data(unwatchable);
  array_delete_vs_data(mounts);
  array_delete_vs_data(new_roots);

  return true;
}


static void unregister_roots() {
  watch_root* root;
  while ((root = array_pop(roots)) != NULL) {
    userlog(LOG_INFO, "unregistering root: %s", root->name);
    unwatch(root->id);
    free(root->name);
    free(root);
  };
}


static bool register_roots(array* new_roots, array* unwatchable, array* mounts) {
  for (int i=0; i<array_size(new_roots); i++) {
    char* new_root = array_get(new_roots, i);
    char* unflattened = new_root;
    if (unflattened[0] == '|') ++unflattened;
    userlog(LOG_INFO, "registering root: %s", new_root);

    if (unflattened[0] != '/') {
      userlog(LOG_WARNING, "  ... not valid, skipped");
      continue;
    }

    array* inner_mounts = array_create(5);
    CHECK_NULL(inner_mounts, false);

    bool skip = false;
    for (int j=0; j<array_size(mounts); j++) {
      char* mount = array_get(mounts, j);
      if (is_parent_path(mount, unflattened)) {
        userlog(LOG_DEBUG, "watch root '%s' is under mount point '%s' - skipping", unflattened, mount);
        CHECK_NULL(array_push(unwatchable, strdup(unflattened)), false);
        skip = true;
        break;
      }
      else if (is_parent_path(unflattened, mount)) {
        userlog(LOG_DEBUG, "watch root '%s' contains mount point '%s' - partial watch", unflattened, mount);
        char* copy = strdup(mount);
        CHECK_NULL(array_push(unwatchable, copy), false);
        CHECK_NULL(array_push(inner_mounts, copy), false);
      }
    }
    if (skip) {
      continue;
    }

    int id = watch(new_root, inner_mounts);
    array_delete(inner_mounts);

    if (id >= 0) {
      watch_root* root = malloc(sizeof(watch_root));
      CHECK_NULL(root, false);
      root->id = id;
      root->name = strdup(new_root);
      CHECK_NULL(root->name, false);
      CHECK_NULL(array_push(roots, root), false);
    }
    else if (id == ERR_ABORT) {
      return false;
    }
    else if (id != ERR_IGNORE) {
      if (show_warning && watch_limit_reached()) {
        int limit = get_watch_count();
        userlog(LOG_WARNING, "watch limit (%d) reached", limit);
        output("MESSAGE\n" INOTIFY_LIMIT_MSG, limit);
        show_warning = false;  // warn only once
      }
      CHECK_NULL(array_push(unwatchable, strdup(unflattened)), false);
    }
  }

  return true;
}


static bool is_watchable(const char* fs) {
  // don't watch special and network filesystems
  return !(strncmp(fs, "dev", 3) == 0 || strcmp(fs, "proc") == 0 || strcmp(fs, "sysfs") == 0 || strcmp(fs, MNTTYPE_SWAP) == 0 ||
           strncmp(fs, "fuse", 4) == 0 || strcmp(fs, "cifs") == 0 || strcmp(fs, MNTTYPE_NFS) == 0);
}

static array* unwatchable_mounts() {
  FILE* mtab = setmntent(_PATH_MOUNTED, "r");
  if (mtab == NULL) {
    userlog(LOG_ERR, "cannot open " _PATH_MOUNTED);
    return NULL;
  }

  array* mounts = array_create(20);
  CHECK_NULL(mounts, NULL);

  struct mntent* ent;
  while ((ent = getmntent(mtab)) != NULL) {
    userlog(LOG_DEBUG, "mtab: %s : %s", ent->mnt_dir, ent->mnt_type);
    if (strcmp(ent->mnt_type, MNTTYPE_IGNORE) != 0 && !is_watchable(ent->mnt_type)) {
      CHECK_NULL(array_push(mounts, strdup(ent->mnt_dir)), NULL);
    }
  }

  endmntent(mtab);
  return mounts;
}


static void inotify_callback(char* path, int event) {
  if (event & IN_CREATE || event & IN_MOVED_TO) {
    output("CREATE\n%s\nCHANGE\n%s\n", path, path);
    userlog(LOG_DEBUG, "CREATE: %s", path);
    return;
  }

  if (event & IN_MODIFY) {
    output("CHANGE\n%s\n", path);
    userlog(LOG_DEBUG, "CHANGE: %s", path);
    return;
  }

  if (event & IN_ATTRIB) {
    output("STATS\n%s\n", path);
    userlog(LOG_DEBUG, "STATS: %s", path);
    return;
  }

  if (event & IN_DELETE || event & IN_MOVED_FROM) {
    output("DELETE\n%s\n", path);
    userlog(LOG_DEBUG, "DELETE: %s", path);
    return;
  }

  if (event & IN_UNMOUNT) {
    output("RESET\n");
    userlog(LOG_DEBUG, "RESET");
    return;
  }
}


static void output(const char* format, ...) {
  if (self_test) {
    return;
  }

  va_list ap;
  va_start(ap, format);
  vprintf(format, ap);
  va_end(ap);
}
