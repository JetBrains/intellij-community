#!/usr/bin/env python

# Waits for the parent process to terminate, then executes specified commands.

import os
import sys
import time

if len(sys.argv) < 3:
    raise Exception('usage: restart.py <pid> <path> [optional command]')

pid = int(sys.argv[1])
while os.getppid() == pid:
    time.sleep(0.5)

if len(sys.argv) > 3:
    os.spawnv(os.P_WAIT, sys.argv[3], sys.argv[3:])

to_launch = sys.argv[2]
if sys.platform == 'darwin':
    os.execv('/usr/bin/open', ['/usr/bin/open', to_launch])
else:
    os.execv(to_launch, [to_launch])
