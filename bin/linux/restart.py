#!/usr/bin/env python

# Waits for the parent process to terminate, then executes specified commands.

import os
import sys
import time

if len(sys.argv) < 2:
    raise Exception('At least one argument expected')

pid = os.getppid()
while os.getppid() == pid:
    time.sleep(0.5)

if len(sys.argv) > 2:
    os.spawnv(os.P_WAIT, sys.argv[2], sys.argv[2:])

to_launch = sys.argv[1]
if sys.platform == 'darwin':
    os.execv('/usr/bin/open', ['/usr/bin/open', to_launch])
else:
    os.execv(to_launch, [to_launch])
