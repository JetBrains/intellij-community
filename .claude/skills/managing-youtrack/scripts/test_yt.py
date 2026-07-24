#!/usr/bin/env python3
"""Tests for yt.py.

The default run touches no network: everything here exercises pure logic, with
the 1Password CLI mocked out.

    python3 -m unittest discover -s scripts        # from the skill directory

Pass --live to additionally run read-only calls against the real instance. That
needs a token in the environment and never writes anything:

    python3 test_yt.py --live
"""
#  Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

from __future__ import annotations

import argparse
import contextlib
import datetime
import http.client
import io
import shlex
import subprocess
import sys
import tempfile
import unittest
import urllib.error
import urllib.request
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent))

import yt  # noqa: E402

OP_PATH = "op://Vault/Item/field"


def fake_op(stdout: str = "tok-from-op", returncode: int = 0, stderr: str = ""):
    """Build a patch for subprocess.run standing in for `op read`."""
    completed = subprocess.CompletedProcess(
        args=["op", "read", OP_PATH], returncode=returncode, stdout=stdout, stderr=stderr
    )
    return mock.patch.object(yt.subprocess, "run", return_value=completed)


class TokenResolutionTest(unittest.TestCase):
    """Precedence is: --token-op-path > $YOUTRACK_TOKEN > $YOUTRACK_TOKEN_OP_PATH."""

    def setUp(self):
        yt._RESOLVED_TOKEN = None

    def test_flag_wins_over_both_env_vars(self):
        env = {yt.ENV_TOKEN: "tok-from-env", yt.ENV_TOKEN_OP_PATH: "op://other/item/f"}
        with mock.patch.dict(yt.os.environ, env, clear=True), fake_op():
            self.assertEqual(yt.resolve_token(OP_PATH), "tok-from-op")

    def test_env_token_wins_over_env_op_path(self):
        env = {yt.ENV_TOKEN: "tok-from-env", yt.ENV_TOKEN_OP_PATH: OP_PATH}
        with mock.patch.dict(yt.os.environ, env, clear=True), fake_op():
            self.assertEqual(yt.resolve_token(None), "tok-from-env")

    def test_falls_back_to_env_op_path(self):
        with mock.patch.dict(
            yt.os.environ, {yt.ENV_TOKEN_OP_PATH: OP_PATH}, clear=True
        ), fake_op():
            self.assertEqual(yt.resolve_token(None), "tok-from-op")

    def test_blank_env_token_is_ignored(self):
        env = {yt.ENV_TOKEN: "   ", yt.ENV_TOKEN_OP_PATH: OP_PATH}
        with mock.patch.dict(yt.os.environ, env, clear=True), fake_op():
            self.assertEqual(yt.resolve_token(None), "tok-from-op")

    def test_no_source_raises_auth_error_naming_all_three(self):
        with mock.patch.dict(yt.os.environ, {}, clear=True):
            with self.assertRaises(yt.AuthError) as ctx:
                yt.resolve_token(None)
        message = str(ctx.exception)
        self.assertIn("--token-op-path", message)
        self.assertIn(yt.ENV_TOKEN, message)
        self.assertIn(yt.ENV_TOKEN_OP_PATH, message)
        self.assertEqual(ctx.exception.exit_code, yt.EXIT_AUTH)


class OpCliTest(unittest.TestCase):
    def test_rejects_path_without_op_scheme(self):
        with self.assertRaises(yt.UsageError):
            yt.read_token_from_op("/etc/passwd")

    def test_does_not_shell_out_for_invalid_path(self):
        with mock.patch.object(yt.subprocess, "run") as run:
            with self.assertRaises(yt.UsageError):
                yt.read_token_from_op("not-an-op-path")
        run.assert_not_called()

    def test_strips_surrounding_whitespace(self):
        with fake_op(stdout="  tok  \n"):
            self.assertEqual(yt.read_token_from_op(OP_PATH), "tok")

    def test_missing_op_binary_is_actionable(self):
        with mock.patch.object(yt.subprocess, "run", side_effect=FileNotFoundError):
            with self.assertRaises(yt.AuthError) as ctx:
                yt.read_token_from_op(OP_PATH)
        self.assertIn("not found", str(ctx.exception))

    def test_nonzero_exit_surfaces_stderr(self):
        with fake_op(returncode=1, stderr="not signed in"):
            with self.assertRaises(yt.AuthError) as ctx:
                yt.read_token_from_op(OP_PATH)
        self.assertIn("not signed in", str(ctx.exception))

    def test_authorization_timeout_gets_an_actionable_message(self):
        # op enforces its own ~60s approval deadline and exits 1. Measured, so
        # subprocess.TimeoutExpired is effectively unreachable for this case.
        stderr = (
            "[ERROR] could not read secret 'op://V/I/f': error initializing "
            "client: authorization timeout"
        )
        with fake_op(returncode=1, stderr=stderr):
            with self.assertRaises(yt.AuthError) as ctx:
                yt.read_token_from_op(OP_PATH)
        message = str(ctx.exception)
        self.assertIn("approve", message.lower())
        self.assertIn(yt.ENV_TOKEN, message)

    def test_dismissed_prompt_tells_the_agent_to_stop_and_ask(self):
        # Declining exits 1 with "authorization prompt dismissed" — the same
        # exit code as the timeout case, so only the message separates them.
        # How fast it returns is just how fast the human clicked; that is not a
        # property of op and must not be asserted on.
        stderr = (
            "[ERROR] could not read secret 'op://V/I/f': error initializing "
            "client: authorization prompt dismissed, please try again"
        )
        with fake_op(returncode=1, stderr=stderr):
            with self.assertRaises(yt.AuthError) as ctx:
                yt.read_token_from_op(OP_PATH)
        message = str(ctx.exception)
        self.assertIn("dismissed", message.lower())
        self.assertIn("ask the user", message.lower())

    def test_no_accounts_points_at_approval_first_then_restart(self):
        # Accounts live in the running desktop app, so this happens while the
        # user's own terminal works fine. The grant usually just lapsed and a
        # fresh approval renews it; a restart is only for the stuck case.
        with fake_op(returncode=1, stderr="No accounts configured for use with 1Password CLI."):
            with self.assertRaises(yt.AuthError) as ctx:
                yt.read_token_from_op(OP_PATH)
        message = str(ctx.exception)
        self.assertIn("does not mean you are signed out", message)
        self.assertIn("approve the 1Password prompt", message)
        self.assertIn("restarting the agent", message)
        self.assertIn(yt.ENV_TOKEN, message)
        self.assertNotIn("sandbox", message.lower())

    def test_unrecognised_failure_mentions_the_sandbox(self):
        with fake_op(returncode=1, stderr="connection refused"):
            with self.assertRaises(yt.AuthError) as ctx:
                yt.read_token_from_op(OP_PATH)
        self.assertIn("sandbox", str(ctx.exception).lower())

    def test_empty_output_is_an_error(self):
        with fake_op(stdout="\n"):
            with self.assertRaises(yt.AuthError):
                yt.read_token_from_op(OP_PATH)

    def test_timeout_is_actionable(self):
        with mock.patch.object(
            yt.subprocess, "run", side_effect=subprocess.TimeoutExpired("op", 60)
        ):
            with self.assertRaises(yt.AuthError) as ctx:
                yt.read_token_from_op(OP_PATH)
        self.assertIn("approve", str(ctx.exception))


class RedactionTest(unittest.TestCase):
    def tearDown(self):
        yt._RESOLVED_TOKEN = None

    def test_replaces_token_anywhere_in_text(self):
        yt._RESOLVED_TOKEN = "perm:secret"
        self.assertEqual(yt.redact("before perm:secret after"), "before *** after")

    def test_no_token_resolved_is_a_no_op(self):
        yt._RESOLVED_TOKEN = None
        self.assertEqual(yt.redact("nothing to hide"), "nothing to hide")


class UrlBuildingTest(unittest.TestCase):
    def test_relative_path(self):
        self.assertEqual(yt.build_url("issues/JEWEL-1367"), f"{yt.API_ROOT}/issues/JEWEL-1367")

    def test_leading_slash_and_api_prefix_are_normalised(self):
        expected = f"{yt.API_ROOT}/issues"
        self.assertEqual(yt.build_url("/issues"), expected)
        self.assertEqual(yt.build_url("api/issues"), expected)
        self.assertEqual(yt.build_url("/api/issues"), expected)

    def test_params_are_encoded(self):
        url = yt.build_url("issues", {"query": "project: JEWEL", "$top": 10})
        self.assertIn("query=project%3A+JEWEL", url)
        self.assertIn("%24top=10", url)

    def test_none_params_are_dropped(self):
        self.assertEqual(yt.build_url("issues", {"$skip": None}), f"{yt.API_ROOT}/issues")

    def test_always_targets_the_pinned_host(self):
        self.assertTrue(yt.build_url("issues").startswith(f"https://{yt.ALLOWED_HOST}/"))


class ErrorMappingTest(unittest.TestCase):
    def test_status_to_exit_code(self):
        cases = {
            400: yt.EXIT_VALIDATION,
            401: yt.EXIT_AUTH,
            403: yt.EXIT_AUTH,
            404: yt.EXIT_NOT_FOUND,
            429: yt.EXIT_TRANSIENT,
            500: yt.EXIT_TRANSIENT,
            503: yt.EXIT_TRANSIENT,
            418: yt.EXIT_ERROR,
        }
        for status, expected in cases.items():
            with self.subTest(status=status):
                self.assertEqual(yt.error_for_status(status, "x").exit_code, expected)

    def test_describe_error_prefers_error_description(self):
        payload = b'{"error":"Not Found","error_description":"No subresource for path commands"}'
        self.assertEqual(
            yt.describe_error(404, payload),
            "HTTP 404: No subresource for path commands",
        )

    def test_describe_error_falls_back_to_error(self):
        self.assertEqual(yt.describe_error(400, b'{"error":"Bad"}'), "HTTP 400: Bad")

    def test_describe_error_handles_non_json(self):
        self.assertEqual(yt.describe_error(500, b"boom"), "HTTP 500: boom")

    def test_describe_error_handles_empty_body(self):
        self.assertEqual(yt.describe_error(502, b""), "HTTP 502")


class CustomFieldPayloadTest(unittest.TestCase):
    def test_state_uses_state_type(self):
        self.assertEqual(
            yt.parse_field_args(["State=Open"]),
            [{"name": "State", "$type": "StateIssueCustomField", "value": {"name": "Open"}}],
        )

    def test_assignee_keys_on_login(self):
        self.assertEqual(
            yt.parse_field_args(["Assignee=sebp"]),
            [
                {
                    "name": "Assignee",
                    "$type": "SingleUserIssueCustomField",
                    "value": {"login": "sebp"},
                }
            ],
        )

    def test_unknown_field_falls_back_to_enum(self):
        (field,) = yt.parse_field_args(["Whatever=Thing"])
        self.assertEqual(field["$type"], "SingleEnumIssueCustomField")
        self.assertEqual(field["value"], {"name": "Thing"})

    def test_inference_is_case_insensitive(self):
        (field,) = yt.parse_field_args(["state=Open"])
        self.assertEqual(field["$type"], "StateIssueCustomField")

    def test_value_may_contain_equals(self):
        (field,) = yt.parse_field_args(["Summary=a=b"])
        self.assertEqual(field["value"], {"name": "a=b"})

    def test_missing_separator_is_a_usage_error(self):
        with self.assertRaises(yt.UsageError):
            yt.parse_field_args(["NoSeparator"])

    def test_empty_name_is_a_usage_error(self):
        with self.assertRaises(yt.UsageError):
            yt.parse_field_args(["=value"])

    def test_empty_list_yields_nothing(self):
        self.assertEqual(yt.parse_field_args([]), [])


class TextArgTest(unittest.TestCase):
    def test_inline_text(self):
        self.assertEqual(yt.read_text_arg("hello", None, "text"), "hello")

    def test_file_text(self):
        with tempfile.NamedTemporaryFile("w", suffix=".txt", delete=False) as handle:
            handle.write("from file")
            path = handle.name
        self.addCleanup(Path(path).unlink)
        self.assertEqual(yt.read_text_arg(None, path, "text"), "from file")

    def test_binary_file_gives_a_usage_error_not_a_raw_decode_error(self):
        with tempfile.NamedTemporaryFile("wb", suffix=".bin", delete=False) as handle:
            handle.write(b"\x00\x98\xff not text")
            path = handle.name
        self.addCleanup(Path(path).unlink)
        with self.assertRaises(yt.UsageError) as ctx:
            yt.read_text_arg(None, path, "text")
        self.assertIn("UTF-8", str(ctx.exception))
        self.assertEqual(ctx.exception.exit_code, yt.EXIT_USAGE)

    def test_both_is_a_usage_error(self):
        with self.assertRaises(yt.UsageError):
            yt.read_text_arg("a", "b", "text")

    def test_neither_is_a_usage_error(self):
        with self.assertRaises(yt.UsageError):
            yt.read_text_arg(None, None, "text")


class MultipartTest(unittest.TestCase):
    def _temp_file(self, name: str, data: bytes) -> Path:
        directory = Path(tempfile.mkdtemp())
        path = directory / name
        path.write_bytes(data)
        return path

    def test_body_contains_boundary_filename_and_payload(self):
        path = self._temp_file("shot.png", b"\x89PNG-bytes")
        body, content_type = yt.encode_multipart([path])
        self.assertTrue(content_type.startswith("multipart/form-data; boundary="))
        boundary = content_type.split("boundary=", 1)[1]
        self.assertIn(boundary.encode(), body)
        self.assertIn(b'filename="shot.png"', body)
        self.assertIn(b"\x89PNG-bytes", body)
        self.assertTrue(body.rstrip().endswith(f"--{boundary}--".encode()))

    def test_quotes_in_filename_cannot_break_the_header(self):
        path = self._temp_file('ev"il.txt', b"x")
        body, _ = yt.encode_multipart([path])
        self.assertIn(b'filename="ev_il.txt"', body)

    def test_trailing_backslash_cannot_escape_the_closing_quote(self):
        # In a quoted header parameter a backslash escapes the next character.
        path = self._temp_file("evil\\", b"x")
        body, _ = yt.encode_multipart([path])
        self.assertIn(b'filename="evil_"', body)
        self.assertNotIn(b'filename="evil\\"', body)

    def test_embedded_backslash_does_not_alter_the_name(self):
        path = self._temp_file("a\\b.txt", b"x")
        body, _ = yt.encode_multipart([path])
        self.assertIn(b'filename="a_b.txt"', body)

    def test_content_type_is_guessed(self):
        path = self._temp_file("a.png", b"x")
        body, _ = yt.encode_multipart([path])
        self.assertIn(b"Content-Type: image/png", body)

    def test_unknown_extension_falls_back_to_octet_stream(self):
        path = self._temp_file("a.unknownext", b"x")
        body, _ = yt.encode_multipart([path])
        self.assertIn(b"Content-Type: application/octet-stream", body)

    def test_missing_file_is_a_usage_error(self):
        with self.assertRaises(yt.UsageError):
            yt.encode_multipart([Path("/nonexistent/nope.txt")])

    def test_each_boundary_is_unique(self):
        path = self._temp_file("a.txt", b"x")
        _, first = yt.encode_multipart([path])
        _, second = yt.encode_multipart([path])
        self.assertNotEqual(first, second)


class RedirectPinningTest(unittest.TestCase):
    def setUp(self):
        self.handler = yt.PinnedRedirectHandler()
        self.request = urllib.request.Request(f"{yt.API_ROOT}/issues")

    def test_cross_host_redirect_is_refused(self):
        with self.assertRaises(yt.YtError) as ctx:
            self.handler.redirect_request(
                self.request, None, 302, "Found", {}, "https://evil.example.com/steal"
            )
        self.assertIn("evil.example.com", str(ctx.exception))

    def test_lookalike_host_is_refused(self):
        with self.assertRaises(yt.YtError):
            self.handler.redirect_request(
                self.request,
                None,
                302,
                "Found",
                {},
                "https://youtrack.jetbrains.com.evil.test/x",
            )

    def test_same_host_redirect_is_allowed(self):
        result = self.handler.redirect_request(
            self.request, None, 302, "Found", {}, f"{yt.API_ROOT}/issues/JEWEL-1367"
        )
        self.assertIsNotNone(result)

    def test_redirect_budget_is_capped(self):
        self.assertEqual(yt.PinnedRedirectHandler.max_redirections, yt.MAX_REDIRECTS)


class SafeFilenameTest(unittest.TestCase):
    """Attachment names are server-supplied, so they must not steer the path."""

    def test_strips_traversal(self):
        self.assertEqual(yt.safe_filename("../../.ssh/authorized_keys"), "authorized_keys")

    def test_strips_absolute_path(self):
        self.assertEqual(yt.safe_filename("/etc/passwd"), "passwd")

    def test_strips_windows_separators(self):
        self.assertEqual(yt.safe_filename(r"..\..\windows\system32\evil.dll"), "evil.dll")

    def test_dot_names_do_not_become_empty_or_hidden(self):
        self.assertEqual(yt.safe_filename(".."), "attachment")
        self.assertEqual(yt.safe_filename("."), "attachment")
        self.assertEqual(yt.safe_filename(""), "attachment")
        self.assertEqual(yt.safe_filename(".bashrc"), "_bashrc")

    def test_ordinary_name_is_untouched(self):
        self.assertEqual(yt.safe_filename("Screenshot 2026-01-01.png"), "Screenshot 2026-01-01.png")

    def test_result_never_escapes_the_output_directory(self):
        out = Path("/tmp/out")
        for hostile in ["../x", "/etc/passwd", r"..\..\x", "../../../../root/.ssh/id_rsa"]:
            with self.subTest(name=hostile):
                self.assertEqual((out / yt.safe_filename(hostile)).parent, out)


class MalformedResponseTest(unittest.TestCase):
    """A truncated 2xx body is transient for a safe method, not a generic error."""

    def _opener(self, payload: bytes):
        class Resp:
            def read(self_inner):
                return payload

            def __enter__(self_inner):
                return self_inner

            def __exit__(self_inner, *a):
                return False

        class Opener:
            def __init__(self):
                self.calls = 0

            def open(self, req, timeout=None):
                self.calls += 1
                return Resp()

        return Opener()

    def _run(self, method, payload):
        opener = self._opener(payload)
        with mock.patch.object(yt.urllib.request, "build_opener", return_value=opener), \
             mock.patch.object(yt.time, "sleep"):
            with self.assertRaises(yt.YtError) as ctx:
                yt.request(method, "issues", token="t")
        return ctx.exception, opener.calls

    def test_truncated_json_on_get_is_transient_and_retried(self):
        err, calls = self._run("GET", b'{"idReadable": "JEWEL-1')
        self.assertEqual(err.exit_code, yt.EXIT_TRANSIENT)
        self.assertEqual(calls, yt.MAX_RETRIES + 1)

    def test_truncated_json_on_post_is_not_retried_and_not_marked_transient(self):
        # Exit 6 would tell an agent "safe to retry", but the POST reached 2xx so
        # the write almost certainly landed; retrying would duplicate it.
        err, calls = self._run("POST", b"{oops")
        self.assertEqual(calls, 1)
        self.assertNotEqual(err.exit_code, yt.EXIT_TRANSIENT)
        self.assertEqual(err.exit_code, yt.EXIT_ERROR)
        self.assertIn("likely been applied", str(err))

    def test_truncated_json_on_delete_also_warns_rather_than_inviting_retry(self):
        err, _ = self._run("DELETE", b"{oops")
        self.assertNotEqual(err.exit_code, yt.EXIT_TRANSIENT)

    def test_incomplete_read_of_an_error_body_still_classifies_by_status(self):
        # IncompleteRead is an HTTPException, not an OSError.
        class Boom(urllib.error.HTTPError):
            def read(self_inner):
                raise http.client.IncompleteRead(b"")

        class Opener:
            def __init__(self):
                self.calls = 0

            def open(self, req, timeout=None):
                self.calls += 1
                raise Boom(req.full_url, 503, "boom", {}, io.BytesIO(b""))

        opener = Opener()
        with mock.patch.object(yt.urllib.request, "build_opener", return_value=opener), \
             mock.patch.object(yt.time, "sleep"):
            with self.assertRaises(yt.YtError) as ctx:
                yt.request("GET", "issues", token="t")
        self.assertEqual(ctx.exception.exit_code, yt.EXIT_TRANSIENT)
        self.assertEqual(opener.calls, yt.MAX_RETRIES + 1)

    def test_empty_body_is_still_an_empty_dict_not_an_error(self):
        opener = self._opener(b"")
        with mock.patch.object(yt.urllib.request, "build_opener", return_value=opener):
            self.assertEqual(yt.request("DELETE", "issues/X", token="t"), {})


class NoTransientOnUnsafeMethodsTest(unittest.TestCase):
    """The invariant: exit 6 means "safe to retry", so an unsafe method must
    never report it. Three separate bugs reached this through three different
    doors — connection failure, unreadable body, and unparseable body — so the
    rule is asserted over all of them at once rather than case by case."""

    def _opener_raising_on_connect(self, exc):
        class Opener:
            def open(self, req, timeout=None):
                raise exc

        return Opener()

    def _opener_raising_on_read(self, exc):
        class Resp:
            def read(self_inner):
                raise exc

            def __enter__(self_inner):
                return self_inner

            def __exit__(self_inner, *a):
                return False

        class Opener:
            def open(self, req, timeout=None):
                return Resp()

        return Opener()

    def _opener_returning(self, payload):
        class Resp:
            def read(self_inner):
                return payload

            def __enter__(self_inner):
                return self_inner

            def __exit__(self_inner, *a):
                return False

        class Opener:
            def open(self, req, timeout=None):
                return Resp()

        return Opener()

    def _exit_code(self, opener, method):
        with mock.patch.object(yt.urllib.request, "build_opener", return_value=opener), \
             mock.patch.object(yt.time, "sleep"):
            with self.assertRaises(yt.YtError) as ctx:
                yt.request(method, "issues", token="t")
        return ctx.exception.exit_code

    def test_no_failure_mode_reports_transient_for_an_unsafe_method(self):
        openers = {
            "connection dies": self._opener_raising_on_connect(ConnectionResetError("x")),
            "ssl failure": self._opener_raising_on_connect(__import__("ssl").SSLError("x")),
            "body unreadable": self._opener_raising_on_read(http.client.IncompleteRead(b"")),
            "body unparseable": self._opener_returning(b"{truncated"),
        }
        for method in ("POST", "DELETE", "PUT"):
            for label, opener in openers.items():
                with self.subTest(method=method, failure=label):
                    self.assertNotEqual(
                        self._exit_code(opener, method),
                        yt.EXIT_TRANSIENT,
                        f"{method} after {label} must not tell the caller to retry",
                    )

    def test_the_same_failures_do_stay_transient_for_a_safe_method(self):
        openers = [
            self._opener_raising_on_connect(ConnectionResetError("x")),
            self._opener_raising_on_read(http.client.IncompleteRead(b"")),
            self._opener_returning(b"{truncated"),
        ]
        for i, opener in enumerate(openers):
            with self.subTest(case=i):
                self.assertEqual(self._exit_code(opener, "GET"), yt.EXIT_TRANSIENT)


class ResponseReadFailureTest(unittest.TestCase):
    """A 2xx arrived, then the body died. For a write that is ambiguous, not
    transient — exit 6 would invite a duplicate."""

    def _opener(self, exc):
        class Resp:
            def read(self_inner):
                raise exc

            def __enter__(self_inner):
                return self_inner

            def __exit__(self_inner, *a):
                return False

        class Opener:
            def __init__(self):
                self.calls = 0

            def open(self, req, timeout=None):
                self.calls += 1
                return Resp()

        return Opener()

    def _run(self, method, exc):
        opener = self._opener(exc)
        with mock.patch.object(yt.urllib.request, "build_opener", return_value=opener), \
             mock.patch.object(yt.time, "sleep"):
            with self.assertRaises(yt.YtError) as ctx:
                yt.request(method, "issues", token="t")
        return ctx.exception, opener.calls

    def test_post_body_read_failure_is_not_marked_transient(self):
        err, calls = self._run("POST", http.client.IncompleteRead(b""))
        self.assertNotEqual(err.exit_code, yt.EXIT_TRANSIENT)
        self.assertIn("likely been applied", str(err))
        self.assertEqual(calls, 1)

    def test_delete_body_read_failure_is_not_marked_transient(self):
        err, _ = self._run("DELETE", ConnectionResetError("reset"))
        self.assertNotEqual(err.exit_code, yt.EXIT_TRANSIENT)

    def test_get_body_read_failure_is_transient_and_retried(self):
        err, calls = self._run("GET", ConnectionResetError("reset"))
        self.assertEqual(err.exit_code, yt.EXIT_TRANSIENT)
        self.assertEqual(calls, yt.MAX_RETRIES + 1)


class RawPayloadConflictTest(unittest.TestCase):
    """--raw-payload must not silently discard builder flags on a mutating call."""

    def _args(self, **over):
        base = dict(
            raw_payload="/tmp/x.json", project=None, summary=None, description=None,
            description_file=None, field=[], dry_run=True, verbose=False, format="json",
        )
        base.update(over)
        return argparse.Namespace(**base)

    def test_summary_alongside_raw_payload_is_rejected(self):
        with self.assertRaises(yt.UsageError) as ctx:
            yt.cmd_issue_create(self._args(summary="Title"), "token")
        self.assertIn("--summary", str(ctx.exception))

    def test_field_alongside_raw_payload_is_rejected(self):
        with self.assertRaises(yt.UsageError) as ctx:
            yt.cmd_issue_create(self._args(field=["Type=Task"]), "token")
        self.assertIn("--field", str(ctx.exception))

    def test_every_conflicting_flag_is_named(self):
        with self.assertRaises(yt.UsageError) as ctx:
            yt.cmd_issue_create(self._args(project="JEWEL", summary="T"), "token")
        message = str(ctx.exception)
        self.assertIn("--project", message)
        self.assertIn("--summary", message)


class WriteNewFileTest(unittest.TestCase):
    """Lexical safety is not enough — an existing symlink must not be followed."""

    def setUp(self):
        self.dir = Path(tempfile.mkdtemp())

    def test_writes_a_new_file(self):
        target = self.dir / "a.txt"
        yt.write_new_file(target, b"hello")
        self.assertEqual(target.read_bytes(), b"hello")

    def test_refuses_to_clobber_an_existing_file(self):
        target = self.dir / "a.txt"
        target.write_bytes(b"original")
        with self.assertRaises(yt.UsageError):
            yt.write_new_file(target, b"replacement")
        self.assertEqual(target.read_bytes(), b"original")

    def test_refuses_to_follow_a_symlink_out_of_the_directory(self):
        outside = self.dir / "outside.txt"
        outside.write_bytes(b"untouched")
        link = self.dir / "link.txt"
        link.symlink_to(outside)
        with self.assertRaises(yt.UsageError):
            yt.write_new_file(link, b"pwned")
        self.assertEqual(outside.read_bytes(), b"untouched")

    def test_refuses_a_dangling_symlink(self):
        link = self.dir / "dangling.txt"
        link.symlink_to(self.dir / "does-not-exist")
        with self.assertRaises(yt.UsageError):
            yt.write_new_file(link, b"nope")


class UniqueNameOnDiskTest(unittest.TestCase):
    def setUp(self):
        self.dir = Path(tempfile.mkdtemp())

    def test_avoids_a_name_that_already_exists_on_disk(self):
        (self.dir / "a.png").write_bytes(b"x")
        self.assertEqual(yt.unique_name("a.png", set(), self.dir), "a-2.png")

    def test_avoids_an_existing_symlink_even_if_dangling(self):
        (self.dir / "a.png").symlink_to(self.dir / "missing")
        self.assertEqual(yt.unique_name("a.png", set(), self.dir), "a-2.png")

    def test_without_a_directory_only_the_batch_matters(self):
        self.assertEqual(yt.unique_name("a.png", set()), "a.png")


class FieldValueShapeTest(unittest.TestCase):
    def test_single_value_is_an_object(self):
        self.assertEqual(
            yt.build_field_value("StateIssueCustomField", "name", "Open"), {"name": "Open"}
        )

    def test_multi_value_is_a_list(self):
        self.assertEqual(
            yt.build_field_value("MultiEnumIssueCustomField", "name", "UI, Core"),
            [{"name": "UI"}, {"name": "Core"}],
        )

    def test_multi_value_ignores_empty_entries(self):
        self.assertEqual(
            yt.build_field_value("MultiUserIssueCustomField", "login", "a,,b,"),
            [{"login": "a"}, {"login": "b"}],
        )

    def test_scalar_types_are_refused_with_a_pointer_to_raw_payload(self):
        with self.assertRaises(yt.UsageError) as ctx:
            yt.build_field_value("DateIssueCustomField", "name", "2026-01-01")
        self.assertIn("raw-payload", str(ctx.exception))


class TransientExceptionTest(unittest.TestCase):
    """ConnectionResetError and ssl.SSLError are not URLError subclasses."""

    def _run(self, exc):
        class Opener:
            def __init__(self):
                self.calls = 0

            def open(self, req, timeout=None):
                self.calls += 1
                raise exc

        opener = Opener()
        with mock.patch.object(yt.urllib.request, "build_opener", return_value=opener), \
             mock.patch.object(yt.time, "sleep"):
            with self.assertRaises(yt.YtError) as ctx:
                yt.request("GET", "issues", token="t")
        return ctx.exception, opener.calls

    def test_connection_reset_is_transient_and_retried(self):
        err, calls = self._run(ConnectionResetError("reset by peer"))
        self.assertEqual(err.exit_code, yt.EXIT_TRANSIENT)
        self.assertEqual(calls, yt.MAX_RETRIES + 1)

    def test_ssl_error_is_transient(self):
        import ssl

        err, _ = self._run(ssl.SSLError("handshake failed"))
        self.assertEqual(err.exit_code, yt.EXIT_TRANSIENT)

    def test_timeout_is_transient(self):
        err, _ = self._run(TimeoutError("timed out"))
        self.assertEqual(err.exit_code, yt.EXIT_TRANSIENT)


class EmitTableTest(unittest.TestCase):
    """YouTrack omits null fields, so rows genuinely differ in shape."""

    def _table(self, data):
        buf = io.StringIO()
        with contextlib.redirect_stdout(buf):
            yt.emit(data, "table")
        return buf.getvalue()

    def test_column_missing_from_the_first_row_is_still_shown(self):
        out = self._table([{"id": "1"}, {"id": "2", "summary": "s"}])
        self.assertIn("summary", out)
        self.assertIn("s", out)

    def test_column_order_is_first_seen(self):
        out = self._table([{"b": 1}, {"a": 2}])
        self.assertLess(out.index("b"), out.index("a"))

    def test_internal_dollar_keys_are_hidden(self):
        self.assertNotIn("$type", self._table([{"id": "1", "$type": "Issue"}]))

    def test_empty_list_prints_nothing(self):
        self.assertEqual(self._table([]), "")

    def test_rows_with_only_dollar_keys_print_nothing(self):
        self.assertEqual(self._table([{"$type": "Issue"}]), "")


class FieldTypeOverrideTest(unittest.TestCase):
    """--type must also decide which key goes inside "value"."""

    def _payload(self, name, value, type_override):
        args = argparse.Namespace(
            issue="JEWEL-1", name=name, value=value, type=type_override,
            dry_run=True, verbose=False,
        )
        with mock.patch.object(yt, "request", return_value=[{"id": "1-1", "name": name}]):
            return yt.cmd_issue_field_set(args, "token")["payload"]

    def test_user_type_override_switches_key_to_login(self):
        payload = self._payload("Reviewer", "sebp", "SingleUserIssueCustomField")
        self.assertEqual(payload["$type"], "SingleUserIssueCustomField")
        self.assertEqual(payload["value"], {"login": "sebp"})

    def test_period_type_override_uses_presentation(self):
        payload = self._payload("Estimation", "2h", "PeriodIssueCustomField")
        self.assertEqual(payload["value"], {"presentation": "2h"})

    def test_without_override_inference_still_applies(self):
        self.assertEqual(
            self._payload("State", "Open", None),
            {"$type": "StateIssueCustomField", "value": {"name": "Open"}},
        )

    def test_assignee_infers_login_without_override(self):
        self.assertEqual(self._payload("Assignee", "sebp", None)["value"], {"login": "sebp"})

    def test_unknown_type_override_keeps_the_inferred_key(self):
        payload = self._payload("State", "Open", "SomeExoticIssueCustomField")
        self.assertEqual(payload["$type"], "SomeExoticIssueCustomField")
        self.assertEqual(payload["value"], {"name": "Open"})


class WorkDateTest(unittest.TestCase):
    """--date means that calendar day where the user is, not in UTC."""

    def _payload(self, date_str):
        args = argparse.Namespace(
            issue="JEWEL-1", duration="1h", date=date_str, text=None,
            dry_run=True, verbose=False,
        )
        return yt.cmd_work_log(args, "token")["payload"]

    def test_date_round_trips_to_the_same_calendar_day_locally(self):
        payload = self._payload("2026-07-20")
        landed = datetime.datetime.fromtimestamp(payload["date"] / 1000).date()
        self.assertEqual(landed, datetime.date(2026, 7, 20))

    def test_bad_date_format_is_a_usage_error(self):
        with self.assertRaises(yt.UsageError):
            self._payload("20-07-2026")

    def test_date_is_omitted_when_not_given(self):
        self.assertNotIn("date", self._payload(None))


class LoggableUrlTest(unittest.TestCase):
    """Attachment URLs carry a `sign` capability token; it must not reach logs."""

    def test_sign_parameter_is_redacted(self):
        url = f"{yt.BASE_URL}/api/files/74-1?sign=SECRETCAP&updated=123"
        out = yt.loggable_url(url)
        self.assertNotIn("SECRETCAP", out)
        self.assertIn("sign=%2A%2A%2A", out)
        self.assertIn("updated=123", out)

    def test_url_without_query_is_unchanged(self):
        url = f"{yt.API_ROOT}/issues/JEWEL-1367"
        self.assertEqual(yt.loggable_url(url), url)

    def test_ordinary_params_survive(self):
        out = yt.loggable_url(f"{yt.API_ROOT}/issues?query=project%3A+JEWEL")
        self.assertIn("project", out)


class UniqueNameTest(unittest.TestCase):
    def test_first_use_is_unchanged(self):
        used = set()
        self.assertEqual(yt.unique_name("a.png", used), "a.png")

    def test_collision_gets_a_suffix_before_the_extension(self):
        used = set()
        names = [yt.unique_name("a.png", used) for _ in range(3)]
        self.assertEqual(names, ["a.png", "a-2.png", "a-3.png"])

    def test_extensionless_names_collide_safely(self):
        used = set()
        self.assertEqual([yt.unique_name("x", used) for _ in range(2)], ["x", "x-2"])

    def test_no_two_results_are_equal(self):
        used = set()
        names = [yt.unique_name("dup.txt", used) for _ in range(5)]
        self.assertEqual(len(set(names)), 5)


class PinnedOriginTest(unittest.TestCase):
    """Hostname alone is not enough: urllib forwards Authorization on redirect."""

    def test_https_to_pinned_host_is_allowed(self):
        yt.check_pinned_origin(f"https://{yt.ALLOWED_HOST}/api/issues")

    def test_http_downgrade_is_refused(self):
        with self.assertRaises(yt.YtError) as ctx:
            yt.check_pinned_origin(f"http://{yt.ALLOWED_HOST}/api/issues")
        self.assertIn("https", str(ctx.exception))

    def test_other_host_is_refused(self):
        with self.assertRaises(yt.YtError):
            yt.check_pinned_origin("https://evil.example.com/steal")

    def test_lookalike_suffix_host_is_refused(self):
        with self.assertRaises(yt.YtError):
            yt.check_pinned_origin(f"https://{yt.ALLOWED_HOST}.evil.test/x")

    def test_odd_port_is_refused(self):
        with self.assertRaises(yt.YtError) as ctx:
            yt.check_pinned_origin(f"https://{yt.ALLOWED_HOST}:444/x")
        self.assertIn("444", str(ctx.exception))

    def test_explicit_443_is_allowed(self):
        yt.check_pinned_origin(f"https://{yt.ALLOWED_HOST}:443/api/issues")

    def test_redirect_handler_uses_the_same_check(self):
        handler = yt.PinnedRedirectHandler()
        req = urllib.request.Request(f"{yt.API_ROOT}/issues")
        with self.assertRaises(yt.YtError):
            handler.redirect_request(req, None, 302, "Found", {}, f"http://{yt.ALLOWED_HOST}/x")


class RetrySafetyTest(unittest.TestCase):
    """Only safe methods may be replayed; a retried POST could double-apply."""

    def _failing_opener(self, status=503):
        calls = []

        class Opener:
            def open(self, req, timeout=None):
                calls.append(req.get_method())
                raise urllib.error.HTTPError(req.full_url, status, "boom", {}, io.BytesIO(b"{}"))

        return Opener(), calls

    def _run(self, method, status=503):
        opener, calls = self._failing_opener(status)
        with mock.patch.object(yt.urllib.request, "build_opener", return_value=opener), \
             mock.patch.object(yt.time, "sleep"):
            with self.assertRaises(yt.YtError):
                yt.request(method, "issues", token="t")
        return calls

    def test_get_is_retried(self):
        self.assertEqual(len(self._run("GET")), yt.MAX_RETRIES + 1)

    def test_post_is_not_retried(self):
        self.assertEqual(len(self._run("POST")), 1)

    def test_delete_is_not_retried(self):
        self.assertEqual(len(self._run("DELETE")), 1)

    def test_non_transient_status_is_not_retried_even_for_get(self):
        self.assertEqual(len(self._run("GET", status=404)), 1)


class PaginationTest(unittest.TestCase):
    """A single call silently truncates; fetch_all must page to the end."""

    def test_pages_until_a_short_batch(self):
        pages = [[{"id": i} for i in range(200)], [{"id": 200}]]
        seen = []

        def fake(method, path, **kw):
            seen.append(kw["params"]["$skip"])
            return pages.pop(0)

        with mock.patch.object(yt, "request", side_effect=fake):
            result = yt.fetch_all("tags", token="t", params={}, verbose=False)
        self.assertEqual(len(result), 201)
        self.assertEqual(seen, [0, 200])

    def test_single_short_page_makes_one_call(self):
        with mock.patch.object(yt, "request", return_value=[{"id": 1}]) as req:
            self.assertEqual(len(yt.fetch_all("tags", token="t", params={}, verbose=False)), 1)
        self.assertEqual(req.call_count, 1)


class ExportHintTest(unittest.TestCase):
    def test_hostile_path_survives_shell_parsing_as_one_token(self):
        # The hint is a command a human or agent may paste, so the path must not
        # be able to break out of its quoting.
        hostile = "op://v/i/f'; rm -rf ~; echo '"
        hint = yt._export_hint(hostile)
        inner = hint[hint.index("$(op read ") + len("$(op read "):hint.rindex(")")]
        self.assertEqual(shlex.split(inner), [hostile])

    def test_ordinary_path_round_trips(self):
        path = "op://Vault/Item/field"
        hint = yt._export_hint(path)
        inner = hint[hint.index("$(op read ") + len("$(op read "):hint.rindex(")")]
        self.assertEqual(shlex.split(inner), [path])


class ScalarFormattingTest(unittest.TestCase):
    def test_prefers_presentation_then_name_then_login(self):
        self.assertEqual(yt._scalar({"presentation": "2h", "name": "x"}), "2h")
        self.assertEqual(yt._scalar({"name": "Open"}), "Open")
        self.assertEqual(yt._scalar({"login": "sebp"}), "sebp")

    def test_none_renders_empty(self):
        self.assertEqual(yt._scalar(None), "")

    def test_list_is_joined(self):
        self.assertEqual(yt._scalar([{"name": "a"}, {"name": "b"}]), "a, b")


# --------------------------------------------------------------------------
# Optional live smoke check (read-only)
# --------------------------------------------------------------------------

LIVE_CHECKS = [
    ("auth check", ["auth", "check"]),
    ("issue get", ["issue", "get", "JEWEL-1367", "--fields", "idReadable"]),
    ("attach list", ["attach", "list", "JEWEL-1367"]),
    ("project get", ["project", "get", "JEWEL"]),
]


def run_live() -> int:
    """Run read-only calls against the real instance. Never writes."""
    import os

    if not (os.environ.get(yt.ENV_TOKEN) or os.environ.get(yt.ENV_TOKEN_OP_PATH)):
        print(
            f"--live needs ${yt.ENV_TOKEN} or ${yt.ENV_TOKEN_OP_PATH} set.",
            file=sys.stderr,
        )
        return yt.EXIT_AUTH

    failures = 0
    for label, argv in LIVE_CHECKS:
        code = yt.main(argv)
        status = "ok" if code == yt.EXIT_OK else f"FAILED (exit {code})"
        print(f"[live] {label}: {status}", file=sys.stderr)
        if code != yt.EXIT_OK:
            failures += 1
    return yt.EXIT_OK if failures == 0 else yt.EXIT_ERROR


if __name__ == "__main__":
    if "--live" in sys.argv:
        sys.argv.remove("--live")
        sys.exit(run_live())
    unittest.main()
