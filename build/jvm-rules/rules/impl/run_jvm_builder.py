#!/usr/bin/env python3

import filecmp
import hashlib
import os
import shutil
import sys
import tempfile


def main(argv):
    # args
    java_path, jvm_builder_path, jvm_opts, args = parse_args(argv)

    # copy to prevent locking on Windows
    copied_jar = copy_file_with_sha256_hash(jvm_builder_path) if os.name == "nt" else jvm_builder_path

    # run
    sys.exit(os.spawnv(os.P_WAIT, java_path, [java_path] + jvm_opts + ["-jar", copied_jar] + args))


def parse_args(argv):
    if len(argv) < 3:
        print("Usage: run_jvm_builder.py java_path jvm_builder_path [jvm_opts...] [-- args...]", file=sys.stderr)
        sys.exit(1)

    java_path = sys.argv[1]
    jvm_builder_path = sys.argv[2]
    jvm_opts_and_args = sys.argv[3:]
    try:
        args_index = jvm_opts_and_args.index("--")
        return java_path, jvm_builder_path, jvm_opts_and_args[:args_index], jvm_opts_and_args[args_index + 1:]
    except ValueError:  # no args
        return java_path, jvm_builder_path, jvm_opts_and_args, []


def copy_file_with_sha256_hash(src):
    with open(src, "rb") as fsrc:
        hash = hashlib.file_digest(fsrc, "sha256").hexdigest()

    dst = src + f"-{hash}"
    if not os.path.exists(dst):
        with tempfile.NamedTemporaryFile(dir=os.path.dirname(src)) as tmp:
            with open(src, "rb") as fsrc:
                shutil.copyfileobj(fsrc, tmp)
            tmp.flush()
            try:
                os.link(tmp.name, dst)  # Note(k15tfu): os.rename() fails if dst exists on Windows, otherwise overwrites it.
            except FileExistsError:  # no op
                pass

    assert filecmp.cmp(src, dst, shallow=False), f"File content mismatch: '{dst}' and '{src}' are different"
    return dst


if __name__ == "__main__":
    main(sys.argv)
