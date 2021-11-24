To avoid connecting from WSL to Windows (such connections may be blocked by firewall) we connect from Windows to WSL instead.
This proxy accepts two clients: one for external (eth0) and one for local (loopback). It then passes data between them with two threads.
Client disconnection, signal or any byte written to the stdin kills process.

To build tool use Makefile. We link it statically because WSL may lack glibc. Kernel ABI is backward compatible, so use some old Linux

We use musl libc: https://musl.libc.org/
Not only it produces smaller binaries, but also it is MIT licenced, and we can't link statically with LGPL

1. Download .tar.gz from here: https://musl.libc.org/
2. unpack to "musl": ``tar xfz musl-1.2.2.tar.gz && mv musl-1.2.2 musl``
3. Open project in CLion if you want
3. run "make"

See https://wiki.musl-libc.org/getting-started.html for more info

Q: I got error opening project in CLion:
A: Make sure you did steps 1 and 2. Then, tools->Makefile->Reload