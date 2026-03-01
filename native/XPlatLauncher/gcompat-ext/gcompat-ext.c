// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

#include <stdarg.h>

extern int fcntl(int fd, int cmd, ...);

int fcntl64(int fd, int cmd, ...) {
  va_list ap;
  va_start(ap, cmd);
  void *arg = va_arg(ap, void *);  // not quite correct, but [famous last words]
  va_end(ap);
  return fcntl(fd, cmd, arg);
}

extern int posix_fallocate(int fd, long offset, long len);

int posix_fallocate64(int fd, long offset, long len) {
  return posix_fallocate(fd, offset, len);
}
