# Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import argparse
import os
import queue
import select
import subprocess
import sys
import tempfile
import threading
import time
import unittest
from pathlib import Path


parser = argparse.ArgumentParser()
parser.add_argument("--executable", type=Path, required=True)
parser.add_argument("--bind-mounts", action="store_true")
parser.add_argument("--registration-race-hook", action="store_true")
arguments, unittest_arguments = parser.parse_known_args()
EXECUTABLE = arguments.executable.resolve()
TEST_BIND_MOUNTS = arguments.bind_mounts
TEST_REGISTRATION_RACE_HOOK = arguments.registration_race_hook
sys.argv = [sys.argv[0], *unittest_arguments]


class FsNotifier:
    def __init__(self, executable: Path, *, env=None, pass_fds=()):
        self._process = subprocess.Popen(
            [executable],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            bufsize=1,
            env=env,
            pass_fds=pass_fds,
        )
        self._stdout = queue.Queue()
        self._stderr = []
        self._stdout_reader = threading.Thread(target=self._read_stdout, daemon=True)
        self._stderr_reader = threading.Thread(target=self._read_stderr, daemon=True)
        self._stdout_reader.start()
        self._stderr_reader.start()

    def _read_stdout(self):
        for line in self._process.stdout:
            self._stdout.put(line.rstrip("\n"))
        self._stdout.put(None)

    def _read_stderr(self):
        self._stderr.extend(line.rstrip("\n") for line in self._process.stderr)

    def _next_line(self, timeout=5):
        try:
            line = self._stdout.get(timeout=timeout)
        except queue.Empty:
            self._fail("timed out waiting for output")
        if line is None:
            self._fail(f"terminated with exit code {self._process.poll()}")
        return line

    def _fail(self, message):
        stderr = "\n".join(self._stderr)
        raise AssertionError(f"fsnotifier {message}\nstderr:\n{stderr}")

    def set_roots(self, roots):
        self._write("ROOTS", *roots, "#")
        if self._next_line() != "UNWATCHEABLE":
            self._fail("did not acknowledge roots")
        while self._next_line() != "#":
            pass

    def expect_events(self, expected, timeout=5, unexpected_paths=()):
        expected = set(expected)
        remaining = set(expected)
        unexpected_paths = set(unexpected_paths)
        deadline = time.monotonic() + timeout
        while remaining:
            operation = self._next_line(max(0.01, deadline - time.monotonic()))
            path = self._next_line(max(0.01, deadline - time.monotonic()))
            event = operation, path
            if path in unexpected_paths and event not in expected:
                raise AssertionError(f"unexpected {operation} event for {path}")
            remaining.discard(event)

    def assert_no_event_for(self, path, timeout=0.5):
        self.assert_no_events_for({path}, timeout)

    def wait_for_quiet(self, timeout=0.5):
        self.assert_no_events_for(set(), timeout)

    def assert_no_events_for(self, paths, timeout=0.5):
        paths = set(paths)
        deadline = time.monotonic() + timeout
        while True:
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                return
            try:
                operation = self._next_line(remaining)
            except AssertionError as error:
                if "timed out waiting for output" in str(error):
                    return
                raise
            event_path = self._next_line(max(0.01, deadline - time.monotonic()))
            if event_path in paths:
                raise AssertionError(f"unexpected {operation} event for {event_path}")

    def stop(self):
        if self._process.poll() is None:
            self._write("EXIT")
        self._process.stdin.close()
        try:
            exit_code = self._process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            self._process.kill()
            self._process.wait()
            self._stdout_reader.join(timeout=1)
            self._stderr_reader.join(timeout=1)
            self._process.stdout.close()
            self._process.stderr.close()
            self._fail("did not exit")
        self._stdout_reader.join(timeout=1)
        self._stderr_reader.join(timeout=1)
        self._process.stdout.close()
        self._process.stderr.close()
        if exit_code != 0:
            self._fail(f"exited with code {exit_code}")
        if any("table error" in line for line in self._stderr):
            self._fail("reported a table error")

    def _write(self, *lines):
        try:
            self._process.stdin.write("".join(f"{line}\n" for line in lines))
            self._process.stdin.flush()
        except BrokenPipeError:
            self._fail(f"terminated with exit code {self._process.poll()}")


class FsNotifierTest(unittest.TestCase):
    @unittest.skipUnless(TEST_REGISTRATION_RACE_HOOK, "requires --registration-race-hook")
    def test_replaced_during_registration(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            file = root / "file.txt"
            replacement = root / "replacement.txt"
            file.write_text("old inode")
            replacement.write_text("replacement inode")

            ready_read, ready_write = os.pipe()
            resume_read, resume_write = os.pipe()
            env = os.environ.copy()
            env["FSNOTIFIER_TEST_REGISTRATION_FDS"] = f"{ready_write}:{resume_read}"
            watcher = FsNotifier(EXECUTABLE, env=env, pass_fds=(ready_write, resume_read))
            os.close(ready_write)
            os.close(resume_read)

            errors = []

            def register_root():
                try:
                    watcher.set_roots([f"|{file}"])
                except BaseException as error:
                    errors.append(error)

            registration = threading.Thread(target=register_root)
            resumed = False
            try:
                registration.start()
                readable, _, _ = select.select([ready_read], [], [], 5)
                self.assertTrue(readable, "fsnotifier did not pause during registration")
                self.assertEqual(b"\0", os.read(ready_read, 1))

                os.replace(replacement, file)
                self.assertEqual(1, os.write(resume_write, b"\0"))
                resumed = True

                registration.join(timeout=5)
                self.assertFalse(registration.is_alive(), "root registration did not finish")
                if errors:
                    raise errors[0]

                file.write_text("replacement updated")
                watcher.expect_events({("CHANGE", str(file))})
            finally:
                if registration.is_alive():
                    if not resumed:
                        os.write(resume_write, b"\0")
                    registration.join(timeout=5)
                watcher.stop()
                os.close(ready_read)
                os.close(resume_write)

    @unittest.skipUnless(TEST_REGISTRATION_RACE_HOOK, "requires --registration-race-hook")
    def test_descendant_removed_during_registration(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            disappearing = root / "disappearing"
            disappearing.mkdir()
            survivor = root / "survivor.txt"
            survivor.write_text("initial")

            ready_read, ready_write = os.pipe()
            resume_read, resume_write = os.pipe()
            env = os.environ.copy()
            env["FSNOTIFIER_TEST_REGISTRATION_FDS"] = f"{ready_write}:{resume_read}"
            env["FSNOTIFIER_TEST_REGISTRATION_PATH"] = str(disappearing)
            watcher = FsNotifier(EXECUTABLE, env=env, pass_fds=(ready_write, resume_read))
            os.close(ready_write)
            os.close(resume_read)

            errors = []

            def register_root():
                try:
                    watcher.set_roots([str(root)])
                except BaseException as error:
                    errors.append(error)

            registration = threading.Thread(target=register_root)
            resumed = False
            try:
                registration.start()
                readable, _, _ = select.select([ready_read], [], [], 5)
                self.assertTrue(readable, "fsnotifier did not pause at the descendant")
                self.assertEqual(b"\0", os.read(ready_read, 1))

                disappearing.rmdir()
                self.assertEqual(1, os.write(resume_write, b"\0"))
                resumed = True

                registration.join(timeout=5)
                self.assertFalse(registration.is_alive(), "root registration did not finish")
                if errors:
                    raise errors[0]

                survivor.write_text("updated")
                watcher.expect_events(
                    {("CHANGE", str(survivor))},
                    unexpected_paths={str(root)},
                )
            finally:
                if registration.is_alive():
                    if not resumed:
                        os.write(resume_write, b"\0")
                    registration.join(timeout=5)
                watcher.stop()
                os.close(ready_read)
                os.close(resume_write)

    def test_hard_link_roots(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            file, link = create_hard_link_roots(Path(temp_dir))

            watcher = FsNotifier(EXECUTABLE)
            try:
                watcher.set_roots([f"|{file}", f"|{link}"])
                file.write_text("updated")
                watcher.expect_events({("CHANGE", str(file)), ("CHANGE", str(link))})

                watcher.set_roots([f"|{file}"])
                file.write_text("updated again")
                watcher.expect_events({("CHANGE", str(file))})
                watcher.assert_no_event_for(str(link))
            finally:
                watcher.stop()

    def test_unlinked_hard_link_root(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            file, link = create_hard_link_roots(Path(temp_dir))

            watcher = FsNotifier(EXECUTABLE)
            try:
                watcher.set_roots([f"|{file}", f"|{link}"])
                file.unlink()
                watcher.expect_events({("DELETE", str(file))})

                link.write_text("survivor updated")
                watcher.expect_events({("CHANGE", str(link))})
                watcher.assert_no_event_for(str(file))

                file.write_text("recreated")
                watcher.expect_events({("CREATE", str(file)), ("CHANGE", str(file))})
                file.write_text("recreated updated")
                watcher.expect_events({("CHANGE", str(file))})
                watcher.assert_no_event_for(str(link))
            finally:
                watcher.stop()

    def test_renamed_hard_link_root(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            file = root / "head.txt"
            link = root / "tail.txt"
            file.write_text("initial")
            os.link(file, link)

            watcher = FsNotifier(EXECUTABLE)
            try:
                watcher.set_roots([f"|{file}", f"|{link}"])
                file.rename(file.with_name("renamed.txt"))
                watcher.expect_events({("DELETE", str(file))})
                watcher.assert_no_event_for(str(link))

                link.write_text("survivor updated")
                watcher.expect_events({("CHANGE", str(link))})
                watcher.assert_no_event_for(str(file))

                file.write_text("recreated")
                watcher.expect_events({("CREATE", str(file)), ("CHANGE", str(file))})
                file.write_text("recreated updated")
                watcher.expect_events({("CHANGE", str(file))})
                watcher.assert_no_event_for(str(link))
            finally:
                watcher.stop()

    def test_distinct_file_roots_become_hard_links(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            file, other = create_file_root_paths(Path(temp_dir))
            file.write_text("old inode")
            other.write_text("shared inode")
            old_inode_link = file.with_name("old-inode.txt")
            replacement = file.with_name("replacement.txt")
            os.link(file, old_inode_link)
            os.link(other, replacement)

            watcher = FsNotifier(EXECUTABLE)
            try:
                watcher.set_roots([f"|{file}", f"|{other}"])
                os.replace(replacement, file)
                watcher.expect_events({
                    ("DELETE", str(file)),
                    ("CREATE", str(file)),
                    ("CHANGE", str(file)),
                }, unexpected_paths={str(other)})
                watcher.assert_no_event_for(str(other))

                other.write_text("shared inode updated")
                watcher.expect_events({("CHANGE", str(file)), ("CHANGE", str(other))})
                watcher.wait_for_quiet()

                old_inode_link.write_text("old inode updated")
                watcher.assert_no_events_for({str(file), str(other)})
            finally:
                watcher.stop()

    def test_hard_link_root_becomes_unique_file(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            file, link = create_hard_link_roots(Path(temp_dir))
            replacement = file.with_name("replacement.txt")
            replacement.write_text("unique replacement")

            watcher = FsNotifier(EXECUTABLE)
            try:
                watcher.set_roots([f"|{file}", f"|{link}"])
                os.replace(replacement, file)
                watcher.expect_events({
                    ("DELETE", str(file)),
                    ("CREATE", str(file)),
                    ("CHANGE", str(file)),
                })

                file.write_text("unique replacement updated")
                watcher.expect_events({("CHANGE", str(file))}, unexpected_paths={str(link)})
                watcher.assert_no_event_for(str(link))

                link.write_text("surviving old inode updated")
                watcher.expect_events({("CHANGE", str(link))}, unexpected_paths={str(file)})
                watcher.assert_no_event_for(str(file))
            finally:
                watcher.stop()

    def test_symlink_intersection(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            target = root / "target"
            target.mkdir()
            file = target / "file.txt"
            file.write_text("initial")
            first_link = root / "first"
            second_link = root / "second"
            first_link.symlink_to(target, target_is_directory=True)
            second_link.symlink_to(target, target_is_directory=True)

            watcher = FsNotifier(EXECUTABLE)
            try:
                watcher.set_roots([str(first_link), str(second_link)])
                file.write_text("updated")
                watcher.expect_events({("CHANGE", str(first_link / file.name))})
            finally:
                watcher.stop()

    @unittest.skipUnless(os.geteuid() == 0 and TEST_BIND_MOUNTS, "requires root and --bind-mounts")
    def test_bind_mount_roots(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            source = root / "source"
            alias = root / "alias"
            nested = source / "nested"
            nested.mkdir(parents=True)
            alias.mkdir()
            file = nested / "file.txt"
            file.write_text("initial")
            subprocess.run(["mount", "--bind", source, alias], check=True)
            try:
                watcher = FsNotifier(EXECUTABLE)
                try:
                    watcher.set_roots([str(source), str(alias)])
                    file.write_text("updated")
                    watcher.expect_events({
                        ("CHANGE", str(file)),
                        ("CHANGE", str(alias / "nested" / file.name)),
                    })

                    watcher.set_roots([str(source)])
                    file.write_text("updated again")
                    watcher.expect_events({("CHANGE", str(file))})
                    watcher.assert_no_event_for(str(alias / "nested" / file.name))
                finally:
                    watcher.stop()
            finally:
                subprocess.run(["umount", alias], check=True)

    @unittest.skipUnless(os.geteuid() == 0 and TEST_BIND_MOUNTS, "requires root and --bind-mounts")
    def test_unmounted_bind_mount_root(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            source = root / "source"
            alias = root / "alias"
            nested = source / "nested"
            nested.mkdir(parents=True)
            alias.mkdir()
            source_file = nested / "file.txt"
            source_file.write_text("initial")
            subprocess.run(["mount", "--bind", source, alias], check=True)
            mounted = True
            watcher = FsNotifier(EXECUTABLE)
            try:
                watcher.set_roots([str(source), str(alias)])
                subprocess.run(["umount", alias], check=True)
                mounted = False
                watcher.expect_events({
                    ("DELETE", str(alias)),
                    ("CREATE", str(alias)),
                    ("CHANGE", str(alias)),
                }, unexpected_paths={str(source)})

                source_file.write_text("source updated")
                watcher.expect_events(
                    {("CHANGE", str(source_file))},
                    unexpected_paths={str(alias / "nested" / source_file.name)},
                )
                watcher.assert_no_event_for(str(alias / "nested" / source_file.name))

                exposed_file = alias / "exposed.txt"
                exposed_file.write_text("exposed mountpoint updated")
                watcher.expect_events({
                    ("CREATE", str(exposed_file)),
                    ("CHANGE", str(exposed_file)),
                })
            finally:
                watcher.stop()
                if mounted:
                    subprocess.run(["umount", alias], check=True)

    @unittest.skipUnless(os.geteuid() == 0 and TEST_BIND_MOUNTS, "requires root and --bind-mounts")
    def test_unmounted_nested_bind_mount(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            source = root / "source"
            alias = root / "alias"
            nested = source / "nested"
            nested.mkdir(parents=True)
            alias.mkdir()
            source_file = nested / "source.txt"
            source_file.write_text("initial")
            exposed_file = alias / "exposed.txt"
            exposed_file.write_text("hidden by bind mount")
            stale_alias_file = alias / "nested" / source_file.name
            subprocess.run(["mount", "--bind", source, alias], check=True)
            mounted = True
            watcher = FsNotifier(EXECUTABLE)
            try:
                watcher.set_roots([str(root)])
                subprocess.run(["umount", alias], check=True)
                mounted = False
                watcher.expect_events({
                    ("DELETE", str(root)),
                    ("CREATE", str(root)),
                    ("CHANGE", str(root)),
                })

                source_file.write_text("source updated")
                watcher.expect_events(
                    {("CHANGE", str(source_file))},
                    unexpected_paths={str(stale_alias_file)},
                )
                watcher.assert_no_event_for(str(stale_alias_file))

                exposed_file.write_text("exposed mountpoint updated")
                watcher.expect_events({("CHANGE", str(exposed_file))})
            finally:
                watcher.stop()
                if mounted:
                    subprocess.run(["umount", alias], check=True)


def create_file_root_paths(root: Path):
    first = root / "first"
    second = root / "second"
    first.mkdir()
    second.mkdir()
    file = first / "file.txt"
    link = second / "link.txt"
    return file, link


def create_hard_link_roots(root: Path):
    file, link = create_file_root_paths(root)
    file.write_text("initial")
    os.link(file, link)
    return file, link


if __name__ == "__main__":
    unittest.main()
