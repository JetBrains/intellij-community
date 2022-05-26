// Copyright 2000-2021 JetBrains s.r.o. and contributors.
// Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

#include <stdio.h>

int main(int argc __attribute__((unused)),
  const char **argv __attribute__((unused)),
  const char **env) {

  const char **v = env;
  while (*v != NULL) {
    fputs(*v, stdout);
    putc('\0', stdout);
    v++;
  }

  return 0;
}
