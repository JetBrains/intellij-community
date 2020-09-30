#pragma once
/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Shared file for elevator and launcher

// Author: Ilya Kazakevich


// So called "descriptors". Used as arguments in many macros and can also be used as binary flags
#define ELEV_DESCR_STDOUT  1
#define ELEV_DESCR_STDERR  2
#define ELEV_DESCR_STDIN   4
#define ELEV_DESCR_ENVVAR  8

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

