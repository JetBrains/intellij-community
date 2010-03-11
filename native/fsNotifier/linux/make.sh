gcc -O2 -m32 -Wall -std=c99 -D_BSD_SOURCE -D_XOPEN_SOURCE=500 -o fsnotifier main.c inotify.c util.c
