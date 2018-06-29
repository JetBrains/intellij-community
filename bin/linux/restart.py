#!/usr/bin/env python

# Waits for the parent process to terminate, then executes specified commands.

import os
import signal
import sys
import syslog
import time

if len(sys.argv) < 3:
    raise Exception('usage: restart.py <pid> <path> [optional command]')

try:
    signal.signal(signal.SIGHUP, signal.SIG_IGN)

    pid = int(sys.argv[1])
    while os.getppid() == pid:
        time.sleep(0.5)

    if len(sys.argv) > 3:
        to_launch = sys.argv[3:]
        ec = os.spawnv(os.P_WAIT, to_launch[0], to_launch)
        if ec != 0:
            syslog.syslog(syslog.LOG_ERR, str(to_launch) + ': ' + str(ec))

    to_launch = ['/usr/bin/open', sys.argv[2]] if sys.platform == 'darwin' else [sys.argv[2]]
    os.execv(to_launch[0], to_launch)
except:
    syslog.syslog(syslog.LOG_ERR, str(sys.exc_info()))
