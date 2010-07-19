#include <stdlib.h>
#include <unistd.h>
#include <stdio.h>
#include <string.h>
#include <sys/types.h>
#include <fcntl.h>
#include <signal.h>

void print_usage() {
	printf("Usage: runneru <app> <args>\n");
	printf("where <app> is console application and <args> it's arguments.\n");
	printf("\n");
	printf(
			"Runner executes an application as a process with inherited input and output streams.\n");
	printf(
			"Input stream is scanned for presence of 2 char 255(IAC) and 243(BRK) sequence and generates Ctrl-C(SIGING) event in that case.\n");
	
	exit(0);
}

void sigint_handler(int sig)
{
	kill(0, SIGINT);
	signal(SIGINT, SIG_DFL);
	kill(getpid(), SIGINT);
}

void generate_break(int pid) {
	kill(0, SIGINT);
	exit(0);
}

char IAC = 255;
char BRK = 243;

int main(int argc, char **argv) {
	if (argc < 2) {
		print_usage();
	}

	char* argv2[argc];

	for (int i = 1; i < argc; i++) {
		argv2[i-1]=argv[i];
	}
	argv2[argc-1]=0;

	if (strlen(argv[0]) == 0) {
		print_usage();
	}

	int write_pipe[2]; /* Pipe toward child */

	signal(SIGINT, &sigint_handler);

	/* Open pipes */
	if (pipe(write_pipe) == -1) {
		perror("pipe");
		exit(1);
	}

	/* Fork child */
	int pid = fork();
	switch (pid) {
	case -1:
		perror("fork");
		exit(1);

	case 0:
		/* Child section */

		if (dup2(write_pipe[0], STDIN_FILENO) == -1) {
			perror("dup2");
			exit(1);
		}

		/* Close unused fildes. */
		close(write_pipe[0]);
		close(write_pipe[1]);

		/* Exec! */

		execv(argv[0], argv2);

		perror("execv");
		exit(1);

	default:

		close(write_pipe[0]);

		int is_iac = 0;
		char buf[1];
		while (1) {
			char c = fgetc(stdin);

			if (is_iac) {
				if (c == BRK) {
					generate_break(pid);
				} else {
					is_iac = 0;
				}
			}
			if (c == IAC) {
				is_iac = 1;
			}
			buf[0] = c;
			write(write_pipe[1], buf, 1);
		}

	}

	/* All done */
	return 0;
}
