#!/usr/bin/env python

# Dumps environment variables into specified file.
# Format: zero-separated "name=value" pairs in platform encoding.

import os
import sys

if len(sys.argv) != 2:
    raise Exception('Exactly one argument expected')

f = open(sys.argv[1], 'w')
try:
    for key, value in os.environ.items():
        if isinstance(key, unicode): key = key.encode('utf-8')
        if isinstance(value, unicode): value = value.encode('utf-8')
        f.writelines([key, '=', value, '\0'])
finally:
    f.close()
