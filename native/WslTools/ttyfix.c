#include <fcntl.h>
#include <stdio.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <stdlib.h>
#include <string.h>
#include <pwd.h>


// Find appropriate shell
// command_to_execute is a first command provided to ttyfix
static char *detect_shell(char *command_to_execute) {
    struct passwd *passwd_entry = getpwuid(geteuid());
    char *user_shell = (passwd_entry != NULL) ? passwd_entry->pw_shell : NULL; //shell from /etc/passwd
    int user_shell_ok = 0;
    int command_ok = 0;

    char *valid_shell;
    while ((valid_shell = getusershell()) != NULL) { //List all valid shells from /etc/shells
        if ((!user_shell_ok) && user_shell != NULL && (strcmp(user_shell, valid_shell) == 0)) {
            user_shell_ok = 1; // User shell is ok, but we prefer to check command
        }
        if ((strcmp(valid_shell, command_to_execute) == 0)) { // First command is a valid shell, use it
            command_ok = 1;
            break; // Since command is the best choice, no need to check other shells
        }
    }
    endusershell();
    if (command_ok) {
        return command_to_execute; // Command provided by user
    } else if (user_shell_ok) {
        return user_shell; // Use shell from /etc/passwd
    }
    return NULL;
}

// Workaround for https://github.com/microsoft/WSL/issues/10701
// Provide command to execute along with args. Command will see 100x100 terminal
int main(int argc, char *argv[]) {
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

    // WSL sets SHELL to ttyfix: https://github.com/microsoft/WSL/issues/10718
    char *shell_env = getenv("SHELL");
    if (shell_env != NULL && (strcmp(argv[0], shell_env) == 0)) { // If SHELL == ttyfix
        unsetenv("SHELL");
        // Do our best to guess shell, or unset it otherwise
        char *correct_shell = detect_shell(argv[1]);
        if (correct_shell != NULL) {
            setenv("SHELL", correct_shell, 1);
        }
    }
    return execv(argv[1], argv + 1); // Substitute self with provided command
}

