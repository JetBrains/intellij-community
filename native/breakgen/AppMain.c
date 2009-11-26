/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

#include <jni.h>
#if defined(WIN32)
#include <windows.h>
#else
#include <signal.h>
#include <unistd.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>

int isKernel26OrHigher();
#endif

JNIEXPORT void JNICALL Java_com_intellij_rt_execution_application_AppMain_triggerControlBreak
  (JNIEnv *env, jclass clazz) {
#if defined(WIN32)
  GenerateConsoleCtrlEvent(CTRL_BREAK_EVENT, 0);
#else
  if (isKernel26OrHigher()) {
    kill (getpid(), SIGQUIT);
  } else {
    int ppid = getppid();
    char buffer[1024];
    sprintf(buffer, "/proc/%d/status", ppid);
    FILE * fp;
    if ( (fp = fopen(buffer, "r")) != NULL )
    {
      char s[124];
      char * ppid_name = "PPid:";
      while (fscanf (fp, "%s\n", s) > 0) {
        if (strcmp(s, ppid_name) == 0) {
          int pppid;
          fscanf(fp, "%d", &pppid);
          kill (pppid, SIGQUIT);
          break;
        }
      }

      fclose (fp);
    }
  }
#endif
}

#ifndef WIN32

int isKernel26OrHigher() {
  char buffer[1024];
  FILE * fp;
  if ( (fp = fopen("/proc/version", "r")) != NULL )
  {
     int major;
     int minor;
     fscanf(fp, "Linux version %d.%d", &major, &minor);
     fclose (fp);
     if (major < 2) return 0;
     if (major == 2) return minor >= 6;
     return 1;  
  }

  return 0;
}
#endif