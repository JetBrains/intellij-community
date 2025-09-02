// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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
#include <sys/stat.h>
#include <unistd.h>


#define USAGE_MSG \
    "fsnotifier - IntelliJ Platform companion program for watching and reporting file and directory structure modifications.\n\n" \
    "Use 'fsnotifier --selftest' to perform some self-diagnostics (output will be printed to console).\n"

#define HELP_MSG \
    "Try 'fsnotifier --help' for more information.\n"

#define MISSING_ROOT_TIMEOUT 1

#define UNFLATTEN(root) (root[0] == '|' ? root + 1 : root)

typedef struct {
  char* path;
  int id;  // negative value means missing root
} watch_root;

static array* roots = NULL;

static bool self_test = false;

static void run_self_test(void);
static bool main_loop(void);
static int read_input(void);
static bool update_roots(array* new_roots);
static void unregister_roots(void);
static bool register_roots(array* new_roots, array* unwatchable, array* mounts);
static array* unwatchable_mounts(void);
static void inotify_callback(const char* path, uint32_t event);
static void report_event(const char* event, const char* path);
static void output(const char* line, bool flush);
static void check_missing_roots(void);
static void check_root_removal(const char*);


int main(int argc, char** argv) {
  if (argc > 1) {
    if (strcmp(argv[1], "--help") == 0) {
      printf(USAGE_MSG);
      return 0;
    }
    else if (strcmp(argv[1], "--version") == 0) {
      printf("fsnotifier " VERSION "\n");
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

  userlog(LOG_INFO, "fsnotifier self-test mode (v." VERSION ")");

  setvbuf(stdin, NULL, _IONBF, 0);

  int rv = 0;
  roots = array_create(20);
  if (roots != NULL && init_inotify()) {
    set_inotify_callback(&inotify_callback);

    if (self_test) {
      run_self_test();
    }
    else if (!main_loop()) {
      rv = 3;
    }

    unregister_roots();
  }
  else {
    output("GIVEUP", true);
    rv = 2;
  }
  close_inotify();
  array_delete(roots);

  userlog(LOG_INFO, "finished (%d)", rv);
  return rv;
}


void message(const char *text) {
  output("MESSAGE", false);
  output(text, true);
}


void userlog(int level, const char* format, ...) {
  va_list ap;
  if (self_test) {
    fputs(level == LOG_ERR ? "[E] " : level == LOG_WARNING ? "[W] " : "[I] ", stdout);
    va_start(ap, format);
    vfprintf(stdout, format, ap);
    va_end(ap);
    fputc('\n', stdout);
  }
  else if (level <= LOG_WARNING) {
    va_start(ap, format);
    vfprintf(stderr, format, ap);
    va_end(ap);
    fputc('\n', stderr);
  }
}


static void run_self_test(void) {
  array* test_roots = array_create(1);
  char* cwd = malloc(PATH_MAX);
  if (getcwd(cwd, PATH_MAX) == NULL) {
    strncpy(cwd, ".", PATH_MAX);
  }
  array_push(test_roots, cwd);
  update_roots(test_roots);
}


static bool main_loop(void) {
  int input_fd = fileno(stdin), inotify_fd = get_inotify_fd();
  int nfds = (inotify_fd > input_fd ? inotify_fd : input_fd) + 1;
  fd_set rfds;
  struct timeval timeout;

  while (true) {
    usleep(50000);

    FD_ZERO(&rfds);
    FD_SET(input_fd, &rfds);
    FD_SET(inotify_fd, &rfds);
    timeout = (struct timeval){MISSING_ROOT_TIMEOUT, 0};

    if (select(nfds, &rfds, NULL, NULL, &timeout) < 0) {
      if (errno != EINTR) {
        userlog(LOG_ERR, "select: %s", strerror(errno));
        return false;
      }
    }
    else if (FD_ISSET(input_fd, &rfds)) {
      int result = read_input();
      if (result == 0) return true;
      else if (result != ERR_CONTINUE) return false;
    }
    else if (FD_ISSET(inotify_fd, &rfds)) {
      if (!process_inotify_input()) return false;
    }
    else {
      check_missing_roots();
    }
  }
}


static int read_input(void) {
  char* line = read_line(stdin);
  if (line == NULL || strcmp(line, "EXIT") == 0) {
    return 0;
  }
  else if (strcmp(line, "ROOTS") == 0) {
    array* new_roots = array_create(20);
    CHECK_NULL(new_roots, ERR_ABORT)

    while (true) {
      line = read_line(stdin);
      if (line == NULL || strlen(line) == 0) {
        return 0;
      }
      else if (strcmp(line, "#") == 0) {
        break;
      }
      else {
        size_t l = strlen(line);
        if (l > 1 && line[l-1] == '/')  line[l-1] = '\0';
        CHECK_NULL(array_push(new_roots, strdup(line)), ERR_ABORT)
      }
    }

    return update_roots(new_roots) ? ERR_CONTINUE : ERR_ABORT;
  }
  else {
    userlog(LOG_WARNING, "unrecognised command: '%s'", line);
    return ERR_CONTINUE;
  }
}


static bool update_roots(array* new_roots) {
  userlog(LOG_INFO, "updating roots (curr:%d, new:%d)", array_size(roots), array_size(new_roots));

  unregister_roots();

  if (array_size(new_roots) == 0) {
    output("UNWATCHEABLE\n#", true);
    array_delete(new_roots);
    return true;
  }
  if (array_size(new_roots) == 1 && strcmp(array_get(new_roots, 0), "/") == 0) {  // refusing to watch the entire tree
    output("UNWATCHEABLE\n/\n#", true);
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

  output("UNWATCHEABLE", false);
  for (int i = 0; i < array_size(unwatchable); i++) {
    output(array_get(unwatchable, i), false);
  }
  output("#", true);

  array_delete_vs_data(unwatchable);
  array_delete_vs_data(mounts);
  array_delete_vs_data(new_roots);

  return true;
}


static void unregister_roots(void) {
  watch_root* root;
  while ((root = array_pop(roots)) != NULL) {
    userlog(LOG_INFO, "unregistering root: %s", root->path);
    unwatch(root->id);
    free(root->path);
    free(root);
  }
}


static bool register_roots(array* new_roots, array* unwatchable, array* mounts) {
  for (int i = 0; i < array_size(new_roots); i++) {
    char* new_root = array_get(new_roots, i);
    char* unflattened = UNFLATTEN(new_root);
    userlog(LOG_INFO, "registering root: %s", new_root);

    if (unflattened[0] != '/') {
      userlog(LOG_WARNING, "invalid root: %s", new_root);
      continue;
    }

    array* inner_mounts = array_create(5);
    CHECK_NULL(inner_mounts, false)

    bool skip = false;
    for (int j = 0; j < array_size(mounts); j++) {
      char* mount = array_get(mounts, j);
      if (is_parent_path(mount, unflattened)) {
        userlog(LOG_INFO, "watch root '%s' is under mount point '%s' - skipping", unflattened, mount);
        CHECK_NULL(array_push(unwatchable, strdup(unflattened)), false)
        skip = true;
        break;
      }
      else if (is_parent_path(unflattened, mount)) {
        userlog(LOG_INFO, "watch root '%s' contains mount point '%s' - partial watch", unflattened, mount);
        char* copy = strdup(mount);
        CHECK_NULL(array_push(unwatchable, copy), false)
        CHECK_NULL(array_push(inner_mounts, copy), false)
      }
    }
    if (skip) {
      continue;
    }

    int id = watch(new_root, inner_mounts);
    array_delete(inner_mounts);

    if (id >= 0 || id == ERR_MISSING) {
      watch_root* root = malloc(sizeof(watch_root));
      CHECK_NULL(root, false)
      root->id = id;
      root->path = strdup(new_root);
      CHECK_NULL(root->path, false)
      CHECK_NULL(array_push(roots, root), false)
    }
    else if (id == ERR_ABORT) {
      return false;
    }
    else if (id != ERR_IGNORE) {
      userlog(LOG_WARNING, "watch root '%s' cannot be watched: %d", unflattened, id);
      CHECK_NULL(array_push(unwatchable, strdup(unflattened)), false)
    }
  }

  return true;
}


static bool is_watchable(const char* fs) {
  // do not watch special and network filesystems
  return !(strncmp(fs, "dev", 3) == 0 || strcmp(fs, "proc") == 0 || strcmp(fs, "sysfs") == 0 || strcmp(fs, MNTTYPE_SWAP) == 0 ||
           strcmp(fs, "cifs") == 0 || strcmp(fs, MNTTYPE_NFS) == 0 || strcmp(fs, "9p") == 0 ||
           (strncmp(fs, "fuse", 4) == 0 && strcmp(fs + 4, "blk") != 0 && strcmp(fs + 4, ".osxfs") != 0));
}

static array* unwatchable_mounts(void) {
  FILE* mtab = setmntent(_PATH_MOUNTED, "r");
  if (mtab == NULL && errno == ENOENT) {
    mtab = setmntent("/proc/mounts", "r");
  }
  if (mtab == NULL) {
    userlog(LOG_ERR, "cannot open " _PATH_MOUNTED);
    return NULL;
  }

  array* mounts = array_create(20);
  CHECK_NULL(mounts, NULL)

  struct mntent* ent;
  while ((ent = getmntent(mtab)) != NULL) {
    userlog(LOG_INFO, "mtab: %s : %s", ent->mnt_dir, ent->mnt_type);
    if (strcmp(ent->mnt_type, MNTTYPE_IGNORE) != 0 && !is_watchable(ent->mnt_type)) {
      CHECK_NULL(array_push(mounts, strdup(ent->mnt_dir)), NULL)
    }
  }

  endmntent(mtab);
  return mounts;
}


static void inotify_callback(const char* path, uint32_t event) {
  if (event & (IN_CREATE | IN_MOVED_TO)) {
    report_event("CREATE", path);
    report_event("CHANGE", path);
  }
  else if (event & IN_MODIFY) {
    report_event("CHANGE", path);
  }
  else if (event & IN_ATTRIB) {
    report_event("STATS", path);
  }
  else if (event & (IN_DELETE | IN_MOVED_FROM)) {
    report_event("DELETE", path);
  }
  if (event & (IN_DELETE_SELF | IN_MOVE_SELF)) {
    check_root_removal(path);
  }
  else if (event & IN_UNMOUNT) {
    output("RESET", true);
  }
}

static void report_event(const char* event, const char* path) {
  char* copy = (char*)path, *p;
  for (p = copy; *p != '\0'; ++p) {
    if (*p == '\n') {
      if (copy == path) {
        copy = strdup(path);
        p = copy + (p - path);
      }
      *p = '\0';
    }
  }

  fputs(event, stdout);
  fputc('\n', stdout);
  fwrite(copy, (p - copy), 1, stdout);
  fputc('\n', stdout);
  fflush(stdout);

  if (copy != path) {
    free(copy);
  }
}


static void output(const char* line, bool flush) {
  fputs(line, stdout);
  fputc('\n', stdout);
  if (flush) {
    fflush(stdout);
  }
}


static void check_missing_roots(void) {
  struct stat st;
  for (int i = 0; i < array_size(roots); i++) {
    watch_root* root = array_get(roots, i);
    if (root->id < 0) {
      char* unflattened = UNFLATTEN(root->path);
      if (stat(unflattened, &st) == 0) {
        root->id = watch(root->path, NULL);
        userlog(LOG_INFO, "root restored: %s\n", root->path);
        report_event("CREATE", unflattened);
        report_event("CHANGE", unflattened);
      }
    }
  }
}

static void check_root_removal(const char* path) {
  for (int i = 0; i < array_size(roots); i++) {
    watch_root* root = array_get(roots, i);
    if (root->id >= 0 && strcmp(path, UNFLATTEN(root->path)) == 0) {
      unwatch(root->id);
      root->id = -1;
      userlog(LOG_INFO, "root deleted: %s\n", root->path);
      report_event("DELETE", path);
    }
  }
}
