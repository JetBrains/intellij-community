#  Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

import subprocess
import threading


def read_stderr(stderr):
    while not stderr.closed:
        text = stderr.readline()
        if text:
            print(f"STDERR: {text}")


process = subprocess.Popen("./wslproxy", shell=False,
                           stdin=subprocess.PIPE,
                           stdout=subprocess.PIPE,
                           stderr=subprocess.PIPE)
threading.Thread(target=read_stderr, args=[process.stderr], daemon=True).start()
egress_ip_addr = ".".join([str(byte) for byte in process.stdout.read(4)])
ingress_port = int.from_bytes(process.stdout.read(2), 'little')
print(f"Please go to WSL and connect to 127.0.0.1:{ingress_port}")
while True:
    egress_port = int.from_bytes(process.stdout.read(2), 'little')
    print(f"Please go to Windows and connect to {egress_ip_addr}:{egress_port}")
