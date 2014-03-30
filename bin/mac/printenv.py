#!/usr/bin/python

# Dumps environment variables into specified file.
# Format: zero-separated "name=value" pairs in platform encoding.

import os
import sys

if len(sys.argv) != 2:
    raise Error('Exactly one argument expected')

with open(sys.argv[1], 'w') as f:
    for key, value in os.environ.items():
        f.writelines([key, '=', value, '\0'])
