// Copyright 2000-2021 JetBrains s.r.o. and contributors.
// Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

#include <stdio.h>

int main(int argc, const char **argv, const char **env) {
  if (argc != 2) {
    printf("usage: %s output_file\n", argv[0]);
    return 1;
  }

  FILE *f = fopen(argv[1], "w");
  if (f == NULL) {
    perror("fopen");
    return 2;
  }

  const char **v = env;
  while (*v != NULL) {
    fputs(*v, f);
    fputc('\0', f);
    v++;
  }

  fclose(f);

  return 0;
}
