#include <fcntl.h>
#include <stdio.h>
#include <unistd.h>
#include <sys/ioctl.h>

// Workaround for https://github.com/microsoft/WSL/issues/10701
// Provide command to execute along with args. Command will see 100x100 terminal
int main(int argc, char *argv[], char *envp[]) {
    if (argc < 2) {
        fprintf(stderr, "No command provided");
        return -1;
    }
    struct winsize w;

    int fd = open(ctermid(NULL), O_RDWR);
    if (fd != -1) { //No terminal, ignore

        const int size_ok = (ioctl(fd, TIOCGWINSZ, &w) == 0 && w.ws_col > 10 && w.ws_row > 10);
        if (!size_ok) {
            w.ws_col = 100;
            w.ws_row = 100;
            ioctl(fd, TIOCSWINSZ, &w); // Set window size
        }
        close(fd);
    }

    return execve(argv[1], argv + 1, envp); // Substitute self with provided command
}

