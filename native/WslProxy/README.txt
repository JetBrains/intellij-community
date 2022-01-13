To avoid connecting from WSL to Windows (such connections may be blocked by firewall) we connect from Windows to WSL instead.
This proxy accepts two clients: one for external (eth0) and one for local (loopback). It then passes data between them with two threads.
Client disconnection, signal or any byte written to the stdin kills process.

To build tool use Makefile. We link it statically because WSL may lack glibc. Kernel ABI is backward compatible, so use some old Linux
