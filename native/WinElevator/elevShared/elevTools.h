#pragma once

// Shared file for elevator and launcher

// Author: Ilya Kazakevich


// So called "descriptors". Used as arguments in many macros and can also be used as binary flags
#define ELEV_DESCR_STDOUT  1
#define ELEV_DESCR_STDERR  2
#define ELEV_DESCR_STDIN  4

// Rules to generate pipe name
#define ELEV_GEN_PIPE_NAME(sDest, nPid, nDescriptor) wsprintf(sDest, L"\\\\.\\pipe\\_jetbrains%ld_%d", nPid, nDescriptor)

#define ELEV_BUF_SIZE 1024 //Buf to read/write between processes

// Convert descriptor to Win32API handler
#define ELEV_DESCR_GET_HANDLE(nDescriptor) (nDescriptor == ELEV_DESCR_STDOUT ? STD_OUTPUT_HANDLE : \
	(nDescriptor == ELEV_DESCR_STDERR ? STD_ERROR_HANDLE : STD_INPUT_HANDLE))

// Pipe name
typedef wchar_t ELEV_PIPE_NAME[32];

// Separates arguments provided to elevator and user command line
#define ELEV_COMMAND_LINE_SEPARATOR L"--::--"

