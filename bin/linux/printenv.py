#!/usr/bin/env python

# Dumps environment variables into specified file.
# Format: zero-separated "name=value" pairs in platform encoding.
# The script can work with any version of Python from 2.3 to at least 3.9

import os
import sys

if len(sys.argv) != 2:
    raise Exception('Exactly one argument expected')

PY2 = sys.version_info < (3,)

if PY2:
    environ = os.environ
else:
    environ = os.environb


def b(s):
    if PY2:
        return s
    else:
        return s.encode('utf-8')


fd = open(sys.argv[1], 'wb')
try:
    for key, value in environ.items():
        fd.writelines([key, b('='), value, b('\0')])
finally:
    fd.close()
