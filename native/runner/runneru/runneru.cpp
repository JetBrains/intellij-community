#include <stdlib.h>
#include <unistd.h>
#include <stdio.h>
#include <sys/types.h>
#include <fcntl.h>
#include <string>
#include <signal.h>

void PrintUsage() {
	printf("Usage: runnerw.exe <app> <args>\n");
	printf("where <app> is console application and <args> it's arguments.\n");
	printf("\n");
	printf(
			"Runner invokes console application as a process with inherited input and output streams.\n");
	printf(
			"Input stream is scanned for presence of 2 char 255(IAC) and 243(BRK) sequence and generates Ctrl-Break event in that case.\n");
	printf(
			"Also in case of all type of event(Ctrl-C, Close, Shutdown etc) Ctrl-Break event is generated.");

	exit(0);
}

void SigintHandler(int sig)
{
	kill(0, SIGINT);
	signal(SIGINT, SIG_DFL);
	kill(getpid(), SIGINT);
}

void Break(int pid) {
	kill(0, SIGINT);
//	kill(pid, SIGTERM);
	exit(0);
}

char IAC = 255;
char BRK = 243;

int main(int argc, char **argv) {
	if (argc < 2) {
		PrintUsage();
	}

	std::string app(argv[1]);
	std::string args("");

	char* argv2[argc];

	for (int i = 1; i < argc; i++) {
		argv2[i-1]=argv[i];
	}
	argv2[argc-1]=0;

	if (app.length() == 0) {
		PrintUsage();
	}

	int par2child[2]; /* Pipe toward child */

	signal(SIGINT, &SigintHandler);

	/* Open pipes */
	if (pipe(par2child) == -1) {
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


		if (dup2(par2child[0], STDIN_FILENO) == -1
				) {
			perror("dup2");
			exit(1);
		}

		/* Close unused fildes. */
		close(par2child[0]);
		close(par2child[1]);

		/* Exec! */

		execv(app.c_str(), argv2);

		perror("execv");
		exit(1);

	default:

		close(par2child[0]);

		int is_iac = 0;
		char buf[1];
		while (1) {
			char c = fgetc(stdin);

			if (is_iac) {
				if (c == BRK) {
					Break(pid);
				} else {
					is_iac = 0;
				}
			}
			if (c == IAC) {
				is_iac = 1;
			}
			buf[0] = c;
			write(par2child[1], buf, 1);
		}

	}

	/* All done */
	return 0;
}
