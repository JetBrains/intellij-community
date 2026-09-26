#!/usr/bin/env python3
"""Command-line client for the JetBrains YouTrack instance.

The base URL is pinned to https://youtrack.jetbrains.com and cannot be
overridden. Requests are built and sent in Python, so free text never transits a
shell command line and there is no escaping to get wrong.

The token is resolved from, in order of precedence:

  1. --token-op-path op://VAULT/ITEM/FIELD   (read via the 1Password CLI)
  2. $YOUTRACK_TOKEN
  3. $YOUTRACK_TOKEN_OP_PATH                 (read via the 1Password CLI)

The token is never printed, logged, or written to disk.

Exit codes: 0 ok, 1 error, 2 usage, 3 auth, 4 not found, 5 validation,
6 transient (rate limit / server error after retries).

Examples:
    yt.py auth check
    yt.py issue get JEWEL-1367
    yt.py issue search 'project: JEWEL #Unresolved' --top 10
    yt.py command apply 'State In Review' --issue JEWEL-1367 --dry-run
    yt.py attach list JEWEL-1367
"""
#  Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

from __future__ import annotations

import argparse
import datetime as _dt
import http.client
import json
import mimetypes
import os
import random
import re
import shlex
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any

# --------------------------------------------------------------------------
# Constants
# --------------------------------------------------------------------------

ALLOWED_HOST = "youtrack.jetbrains.com"
BASE_URL = f"https://{ALLOWED_HOST}"
API_ROOT = f"{BASE_URL}/api"

MAX_REDIRECTS = 3
MAX_RETRIES = 3
INITIAL_RETRY_DELAY = 1.0
TIMEOUT = 60
# `op read` blocks on an interactive approval prompt, but enforces its own limit:
# measured at ~60s, after which it exits 1 with "authorization timeout". So this
# is only a backstop against op not returning at all, not the real deadline.
OP_TIMEOUT = 90

ENV_TOKEN = "YOUTRACK_TOKEN"
ENV_TOKEN_OP_PATH = "YOUTRACK_TOKEN_OP_PATH"

EXIT_OK = 0
EXIT_ERROR = 1
EXIT_USAGE = 2
EXIT_AUTH = 3
EXIT_NOT_FOUND = 4
EXIT_VALIDATION = 5
EXIT_TRANSIENT = 6

# Default `fields` selections. YouTrack returns only what is asked for.
F_ISSUE = (
    "idReadable,summary,description,created,updated,project(shortName),"
    "reporter(login),customFields(name,value(name,login,presentation))"
)
F_ISSUE_SHORT = "idReadable,summary"
F_COMMENT = "id,text,author(login,name),created,updated"
F_ATTACH = "id,name,size,mimeType"
F_LINK = "id,linkType(name,sourceToTarget,targetToSource),issues(idReadable,summary)"
F_LINKTYPE = "id,name,sourceToTarget,targetToSource"
F_WORK = "id,author(login),date,duration(minutes,presentation),text,type(name)"
F_TAG = "id,name"
F_USER = "id,login,name,email"
F_PROJECT = "id,shortName,name"
F_CUSTOMFIELD = "id,name,value(name,login,presentation)"

# Custom-field $type inference. YouTrack rejects a payload whose $type does not
# match the field's actual type, and the error it returns is not always obvious.
FIELD_TYPES = {
    "state": ("StateIssueCustomField", "name"),
    "assignee": ("SingleUserIssueCustomField", "login"),
    "type": ("SingleEnumIssueCustomField", "name"),
    "priority": ("SingleEnumIssueCustomField", "name"),
    "subsystem": ("SingleEnumIssueCustomField", "name"),
}
DEFAULT_FIELD_TYPE = ("SingleEnumIssueCustomField", "name")

# Which key inside "value" a given $type expects. Consulted when --type overrides
# the inferred type, so the key follows the type rather than the field name.
VALUE_KEY_BY_TYPE = {
    "StateIssueCustomField": "name",
    "SingleEnumIssueCustomField": "name",
    "SingleVersionIssueCustomField": "name",
    "SingleBuildIssueCustomField": "name",
    "SingleOwnedIssueCustomField": "name",
    "SingleUserIssueCustomField": "login",
    "PeriodIssueCustomField": "presentation",
    "MultiEnumIssueCustomField": "name",
    "MultiVersionIssueCustomField": "name",
    "MultiBuildIssueCustomField": "name",
    "MultiOwnedIssueCustomField": "name",
    "MultiUserIssueCustomField": "login",
    "MultiGroupIssueCustomField": "name",
}

# Multi-value fields take a LIST of value objects; sending a bare object is
# rejected. Types outside these tables (date, integer, float, text) expect a
# scalar rather than an object and are not supported — use --raw-payload.
SCALAR_VALUE_TYPES = {
    "DateIssueCustomField",
    "SimpleIssueCustomField",
    "TextIssueCustomField",
}


def build_field_value(type_name: str, value_key: str, value: str) -> Any:
    """Shape a custom-field value the way its $type requires."""
    if type_name in SCALAR_VALUE_TYPES:
        raise UsageError(
            f"{type_name} expects a scalar value, which this CLI does not "
            "construct. Use `issue create --raw-payload` for that field type."
        )
    if type_name.startswith("Multi"):
        # Comma-separated on the command line, a list in the payload.
        return [{value_key: v.strip()} for v in value.split(",") if v.strip()]
    return {value_key: value}


# --------------------------------------------------------------------------
# Errors
# --------------------------------------------------------------------------


class YtError(Exception):
    """Base error. `exit_code` is what the process returns."""

    exit_code = EXIT_ERROR


class UsageError(YtError):
    exit_code = EXIT_USAGE


class AuthError(YtError):
    exit_code = EXIT_AUTH


class NotFoundError(YtError):
    exit_code = EXIT_NOT_FOUND


class ValidationError(YtError):
    exit_code = EXIT_VALIDATION


class TransientError(YtError):
    exit_code = EXIT_TRANSIENT


def error_for_status(status: int, message: str) -> YtError:
    """Map an HTTP status onto a typed error so callers can branch on exit code."""
    if status in (401, 403):
        return AuthError(message)
    if status == 404:
        return NotFoundError(message)
    if status == 400:
        return ValidationError(message)
    if status == 429 or 500 <= status < 600:
        return TransientError(message)
    return YtError(message)


# --------------------------------------------------------------------------
# Token resolution and redaction
# --------------------------------------------------------------------------

# Held module-level purely so redact() can scrub it from error output. Never
# written anywhere else.
_RESOLVED_TOKEN: str | None = None


def redact(text: str) -> str:
    """Scrub the resolved token from arbitrary text. Defence in depth."""
    if _RESOLVED_TOKEN and _RESOLVED_TOKEN in text:
        text = text.replace(_RESOLVED_TOKEN, "***")
    return text


def _export_hint(op_path: str) -> str:
    """The one-off command that avoids a prompt per invocation."""
    return f"export {ENV_TOKEN}=$(op read {shlex.quote(op_path)})"


def read_token_from_op(op_path: str) -> str:
    """Read a secret via the 1Password CLI.

    The path is not secret, so passing it in argv is fine; the token it resolves
    to is only ever held in memory.
    """
    if not op_path.startswith("op://"):
        raise UsageError(
            f"1Password secret path must start with 'op://', got {op_path!r}"
        )
    try:
        proc = subprocess.run(
            ["op", "read", op_path],
            capture_output=True,
            text=True,
            timeout=OP_TIMEOUT,
        )
    except FileNotFoundError:
        raise AuthError(
            "The 1Password CLI ('op') was not found on PATH. It is a separate "
            "install from the 1Password app: https://www.1password.dev/cli/get-started\n"
            f"If you do not use 1Password, set ${ENV_TOKEN} instead."
        ) from None
    except subprocess.TimeoutExpired:
        raise AuthError(
            f"`op read` got no answer within {OP_TIMEOUT}s. 1Password is most "
            "likely still waiting for you to approve the request — approve it "
            "and re-run."
        ) from None

    if proc.returncode != 0:
        detail = (proc.stderr or "").strip() or f"exit code {proc.returncode}"
        lowered = detail.lower()
        # op's two interactive-approval failures both exit 1, so the message is
        # the only thing telling them apart. Match on the message, never on how
        # long the call took: that is the user's reaction time, not op's.
        if "authorization timeout" in lowered:
            raise AuthError(
                "1Password stopped waiting for approval before the request was "
                "authorized (its own ~60s limit). Re-run and approve the prompt, "
                f"or resolve the token once with: {_export_hint(op_path)}"
            )
        if "prompt dismissed" in lowered:
            raise AuthError(
                "The 1Password approval prompt was dismissed, so no token was "
                "read. If that was deliberate, stop here and ask the user. If "
                "not, re-run and approve it, or resolve the token once with: "
                f"{_export_hint(op_path)}"
            )
        if "no accounts configured" in lowered:
            raise AuthError(
                "The 1Password CLI reports no accounts. This does not mean you "
                "are signed out — `op account list` may well work in your own "
                "terminal while returning nothing here. Usually the grant has "
                "just lapsed: approve the 1Password prompt and run this again. "
                "If approving changes nothing and further calls fail without "
                "prompting, 1Password is stuck — restarting the agent has "
                f"cleared that. Failing both, export ${ENV_TOKEN} in the shell "
                "that launches the agent."
            )
        raise AuthError(
            f"`op read {op_path}` failed: {detail}\n"
            "If no prompt appeared at all, the agent sandbox may be blocking the "
            "1Password CLI from reaching the desktop app. Retry outside the "
            "sandbox if the harness allows it before asking the user."
        )

    token = proc.stdout.strip()
    if not token:
        raise AuthError(f"`op read {op_path}` returned an empty value.")
    return token


def resolve_token(op_path_flag: str | None) -> str:
    """Resolve the API token, most-explicit source first."""
    global _RESOLVED_TOKEN

    if op_path_flag:
        _RESOLVED_TOKEN = read_token_from_op(op_path_flag)
        return _RESOLVED_TOKEN

    env_token = os.environ.get(ENV_TOKEN, "").strip()
    if env_token:
        _RESOLVED_TOKEN = env_token
        return _RESOLVED_TOKEN

    env_op_path = os.environ.get(ENV_TOKEN_OP_PATH, "").strip()
    if env_op_path:
        _RESOLVED_TOKEN = read_token_from_op(env_op_path)
        return _RESOLVED_TOKEN

    raise AuthError(
        "No YouTrack token found. Provide one of:\n"
        "  --token-op-path op://VAULT/ITEM/FIELD   (needs the 1Password CLI)\n"
        f"  ${ENV_TOKEN}=<token>\n"
        f"  ${ENV_TOKEN_OP_PATH}=op://VAULT/ITEM/FIELD   (needs the 1Password CLI)\n"
        "To create a token: https://www.jetbrains.com/help/youtrack/cloud/"
        "manage-permanent-token.html#obtain-permanent-token"
    )


# --------------------------------------------------------------------------
# HTTP
# --------------------------------------------------------------------------


def check_pinned_origin(url: str, what: str = "request") -> None:
    """Reject anything that is not exactly https://<ALLOWED_HOST> (port 443).

    Checking the hostname alone is not enough: urllib forwards the Authorization
    header across allowed redirects, so an http:// downgrade would put the token
    on the wire in cleartext, and an odd port could reach a different service on
    the same host.
    """
    parts = urllib.parse.urlparse(url)
    if parts.scheme != "https":
        raise YtError(f"Refusing {what} to {parts.scheme!r}: only https is allowed.")
    if parts.hostname != ALLOWED_HOST:
        raise YtError(
            f"Refusing {what} to {parts.hostname!r}: only {ALLOWED_HOST} is allowed."
        )
    if parts.port not in (None, 443):
        raise YtError(f"Refusing {what} to port {parts.port}: only 443 is allowed.")


class PinnedRedirectHandler(urllib.request.HTTPRedirectHandler):
    """Allow redirects only within the pinned origin.

    The skill states URL pinning as a rule; this enforces it in code, so a
    redirect can never move an authenticated request to another origin.
    """

    max_redirections = MAX_REDIRECTS

    def redirect_request(self, req, fp, code, msg, headers, newurl):  # noqa: D102
        check_pinned_origin(newurl, "redirect")
        return super().redirect_request(req, fp, code, msg, headers, newurl)


def fetch_all(
    path: str, *, token: str, params: dict[str, Any], verbose: bool, page: int = 200
) -> list[Any]:
    """Page through a collection endpoint until it stops returning full pages.

    YouTrack caps collections server-side, so a single call silently truncates.
    """
    out: list[Any] = []
    skip = 0
    while True:
        batch = request(
            "GET",
            path,
            token=token,
            params={**params, "$top": page, "$skip": skip},
            verbose=verbose,
        )
        if not isinstance(batch, list):
            return batch
        out.extend(batch)
        if len(batch) < page:
            return out
        skip += page


def build_url(path: str, params: dict[str, Any] | None = None) -> str:
    """Build an absolute API URL. `path` is relative to /api."""
    clean = path.lstrip("/")
    if clean.startswith("api/"):
        clean = clean[len("api/") :]
    url = f"{API_ROOT}/{clean}"
    if params:
        pairs = [(k, str(v)) for k, v in params.items() if v is not None]
        if pairs:
            url = f"{url}?{urllib.parse.urlencode(pairs)}"
    return url


SENSITIVE_QUERY_KEYS = {"sign", "token", "access_token"}


def loggable_url(url: str) -> str:
    """Strip capability tokens out of a URL before it is logged.

    Attachment URLs carry a `sign` parameter that grants access to the file, so
    it is a credential in its own right and must not reach the logs.
    """
    parts = urllib.parse.urlsplit(url)
    if not parts.query:
        return url
    pairs = urllib.parse.parse_qsl(parts.query, keep_blank_values=True)
    scrubbed = [
        (k, "***" if k.lower() in SENSITIVE_QUERY_KEYS else v) for k, v in pairs
    ]
    return urllib.parse.urlunsplit(
        parts._replace(query=urllib.parse.urlencode(scrubbed))
    )


def describe_error(status: int, payload: bytes) -> str:
    """Turn a YouTrack error body into a one-line message."""
    try:
        data = json.loads(payload)
        if isinstance(data, dict):
            detail = data.get("error_description") or data.get("error")
            if detail:
                return f"HTTP {status}: {detail}"
    except (ValueError, TypeError):
        pass
    text = payload.decode("utf-8", "replace").strip()
    return f"HTTP {status}: {text}" if text else f"HTTP {status}"


def _sleep_backoff(attempt: int) -> None:
    delay = INITIAL_RETRY_DELAY * (2**attempt) * (0.5 + 0.5 * random.random())
    time.sleep(delay)


def request(
    method: str,
    path: str,
    *,
    token: str,
    params: dict[str, Any] | None = None,
    json_body: Any | None = None,
    raw_body: bytes | None = None,
    content_type: str | None = None,
    accept: str = "application/json",
    expect_bytes: bool = False,
    absolute_url: str | None = None,
    verbose: bool = False,
) -> Any:
    """Perform an API request, retrying only transient failures."""
    url = absolute_url or build_url(path, params)

    if absolute_url:
        check_pinned_origin(absolute_url)

    body = raw_body
    headers = {"Accept": accept}
    if json_body is not None:
        body = json.dumps(json_body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    elif content_type:
        headers["Content-Type"] = content_type
    headers["Authorization"] = f"Bearer {token}"

    if verbose:
        # Deliberately logs method/URL/body but never headers, which carry the token.
        print(f"[yt] {method} {loggable_url(url)}", file=sys.stderr)
        if json_body is not None:
            print(f"[yt] body: {json.dumps(json_body)}", file=sys.stderr)

    # Only safe methods are retried. Replaying a POST that YouTrack already
    # committed would double-post a comment or re-apply a command.
    retryable = method.upper() in ("GET", "HEAD")

    opener = urllib.request.build_opener(PinnedRedirectHandler)
    last: YtError | None = None

    for attempt in range(MAX_RETRIES + 1):
        req = urllib.request.Request(url, data=body, headers=headers, method=method)
        try:
            with opener.open(req, timeout=TIMEOUT) as resp:
                try:
                    payload = resp.read()
                except (OSError, http.client.HTTPException) as exc:
                    # The server already returned a success status, so an unsafe
                    # request has almost certainly been applied. Reporting this
                    # as transient would invite a retry that duplicates it.
                    if not retryable:
                        raise YtError(
                            f"{method} reached {ALLOWED_HOST} and got a success "
                            f"status, but the body could not be read: {exc}. The "
                            "change has most likely been applied — check before "
                            "retrying."
                        ) from None
                    raise
            if expect_bytes:
                return payload
            if not payload.strip():
                # DELETE and some POSTs return an empty 2xx body.
                return {}
            try:
                return json.loads(payload)
            except (json.JSONDecodeError, UnicodeDecodeError) as exc:
                if retryable:
                    # Safe method: a truncated body just means the response was
                    # cut short, so retrying is harmless.
                    err = TransientError(
                        f"Malformed JSON in response from {ALLOWED_HOST}: {exc}"
                    )
                    if attempt < MAX_RETRIES:
                        last = err
                        _sleep_backoff(attempt)
                        continue
                    raise err from None
                # Unsafe method: the server returned 2xx, so the write almost
                # certainly landed — we just cannot parse the confirmation.
                # Reporting this as transient would invite a retry and duplicate
                # the issue, comment or work item.
                raise YtError(
                    f"{method} succeeded ({ALLOWED_HOST} returned 2xx) but its "
                    f"response could not be parsed: {exc}. The change has most "
                    "likely been applied — check before retrying."
                ) from None
        except urllib.error.HTTPError as exc:
            # HTTPError wraps a live file object; read it, then close it or the
            # underlying connection is leaked until GC gets round to it. The read
            # itself can fail on a dropped socket, so it must not escape here.
            try:
                payload = exc.read()
            except (OSError, http.client.HTTPException):
                # IncompleteRead is an HTTPException, not an OSError, so catching
                # OSError alone let a truncated error body escape unclassified.
                payload = b""
            finally:
                exc.close()
            err = error_for_status(exc.code, describe_error(exc.code, payload))
            if isinstance(err, TransientError) and retryable and attempt < MAX_RETRIES:
                last = err
                if verbose:
                    print(f"[yt] transient {exc.code}, retrying", file=sys.stderr)
                _sleep_backoff(attempt)
                continue
            raise err from None
        except (OSError, http.client.HTTPException) as exc:
            # OSError covers URLError, ConnectionResetError, socket timeouts and
            # ssl.SSLError — none of which are URLError subclasses, so catching
            # URLError alone let a dropped connection escape as a generic exit 1.
            reason = getattr(exc, "reason", exc)
            if not retryable:
                # We cannot tell a connection refused before the request was sent
                # from one dropped after the server applied it. Exit 6 would tell
                # the caller to retry, which duplicates the write in the second
                # case, so unsafe methods never report transient.
                raise YtError(
                    f"{method} to {ALLOWED_HOST} failed with the outcome unknown: "
                    f"{reason}. It may or may not have been applied — verify "
                    "before retrying."
                ) from None
            err = TransientError(f"Network error contacting {ALLOWED_HOST}: {reason}")
            if attempt < MAX_RETRIES:
                last = err
                _sleep_backoff(attempt)
                continue
            raise err from None

    raise last or TransientError("Request failed after retries.")


def safe_filename(name: str) -> str:
    """Reduce a server-supplied attachment name to a bare, safe filename.

    Attachment names are untrusted user content. Without this, a name like
    '../../.ssh/authorized_keys' would escape the --out directory.
    """
    # Treat both separators as such regardless of platform: a Windows-style name
    # would otherwise survive on POSIX and vice versa.
    base = re.split(r"[\\/]", name)[-1].strip()
    if base in ("", ".", ".."):
        return "attachment"
    if base.startswith("."):
        # Not unsafe by itself, but avoid silently writing hidden files.
        base = "_" + base.lstrip(".")
    return base


def unique_name(name: str, used: set[str], directory: Path | None = None) -> str:
    """Return `name`, suffixed so it collides with neither `used` nor the disk.

    Checking `used` alone is not enough: an existing file would be truncated and
    an existing symlink would be followed, letting a download land outside the
    output directory.
    """

    def taken(candidate: str) -> bool:
        if candidate in used:
            return True
        if directory is None:
            return False
        target = directory / candidate
        # lexists, not exists: a dangling symlink still must not be written to.
        return target.is_symlink() or target.exists()

    if not taken(name):
        used.add(name)
        return name
    stem, dot, ext = name.partition(".")
    n = 2
    while taken(f"{stem}-{n}{dot}{ext}"):
        n += 1
    picked = f"{stem}-{n}{dot}{ext}"
    used.add(picked)
    return picked


def write_new_file(target: Path, data: bytes) -> None:
    """Write `data` to `target`, refusing to follow a symlink or clobber a file."""
    flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL
    flags |= getattr(os, "O_NOFOLLOW", 0)
    try:
        fd = os.open(target, flags, 0o600)
    except FileExistsError:
        raise UsageError(f"Refusing to overwrite existing path: {target}") from None
    except OSError as exc:
        raise UsageError(f"Cannot write {target}: {exc}") from None
    try:
        with os.fdopen(fd, "wb") as handle:
            handle.write(data)
    except OSError as exc:
        # Leaving a truncated file behind would also block the retry, since we
        # refuse to overwrite an existing destination.
        target.unlink(missing_ok=True)
        raise YtError(f"Failed writing {target}: {exc}") from None


def encode_multipart(paths: list[Path]) -> tuple[bytes, str]:
    """Encode files as multipart/form-data. Returns (body, content_type)."""
    boundary = f"----ytcli{os.urandom(12).hex()}"
    # Accumulate into one buffer rather than a list plus a join, which would hold
    # two full copies of every attachment at once.
    body = bytearray()
    for path in paths:
        if not path.is_file():
            raise UsageError(f"Not a file: {path}")
        # In a quoted header parameter a backslash escapes the next character,
        # so a name ending in one would swallow the closing quote. Quotes and
        # newlines would break the header outright.
        safe = (
            path.name.replace("\\", "_")
            .replace('"', "_")
            .replace("\r", "_")
            .replace("\n", "_")
        )
        ctype = mimetypes.guess_type(safe)[0] or "application/octet-stream"
        body += f"--{boundary}\r\n".encode()
        body += (
            f'Content-Disposition: form-data; name="{safe}"; filename="{safe}"\r\n'.encode()
        )
        body += f"Content-Type: {ctype}\r\n\r\n".encode()
        body += path.read_bytes()
        body += b"\r\n"
    body += f"--{boundary}--\r\n".encode()
    return bytes(body), f"multipart/form-data; boundary={boundary}"


# --------------------------------------------------------------------------
# Output
# --------------------------------------------------------------------------


def _scalar(value: Any) -> str:
    if value is None:
        return ""
    if isinstance(value, dict):
        for key in ("presentation", "name", "login", "idReadable", "id"):
            if key in value:
                return str(value[key])
        return json.dumps(value, ensure_ascii=False)
    if isinstance(value, list):
        return ", ".join(_scalar(v) for v in value)
    return str(value)


def emit(data: Any, fmt: str) -> None:
    """Print a result in the requested format."""
    if fmt == "json":
        print(json.dumps(data, indent=2, ensure_ascii=False))
        return

    rows = data if isinstance(data, list) else [data]
    rows = [r for r in rows if isinstance(r, dict)]

    if fmt == "ids":
        for row in rows:
            ident = row.get("idReadable") or row.get("id")
            if ident:
                print(ident)
        return

    if not rows:
        return
    # Union of keys, first-seen order. YouTrack omits null fields, so taking the
    # columns from rows[0] alone would silently drop data other rows do have.
    columns: list[str] = []
    for row in rows:
        for key in row:
            if not key.startswith("$") and key not in columns:
                columns.append(key)
    if not columns:
        return
    widths = {
        c: max(len(c), *(len(_scalar(r.get(c))) for r in rows)) for c in columns
    }
    print("  ".join(c.ljust(widths[c]) for c in columns))
    print("  ".join("-" * widths[c] for c in columns))
    for row in rows:
        print("  ".join(_scalar(row.get(c)).ljust(widths[c]) for c in columns))


def read_text_arg(inline: str | None, file_path: str | None, what: str) -> str:
    """Read free text from --x or --x-file.

    Preferring a file keeps untrusted or multi-line content off the command line
    entirely.
    """
    if inline is not None and file_path is not None:
        raise UsageError(f"Pass either --{what} or --{what}-file, not both.")
    if file_path is not None:
        try:
            return Path(file_path).read_text(encoding="utf-8")
        except UnicodeDecodeError as exc:
            raise UsageError(
                f"{file_path} is not valid UTF-8 text ({exc.reason}). "
                f"--{what}-file expects a text file."
            ) from None
    if inline is not None:
        return inline
    raise UsageError(f"One of --{what} or --{what}-file is required.")


# --------------------------------------------------------------------------
# Helpers shared by commands
# --------------------------------------------------------------------------


def parse_field_args(field_args: list[str]) -> list[dict[str, Any]]:
    """Turn repeated --field 'Name=Value' into a customFields payload."""
    fields = []
    for raw in field_args or []:
        if "=" not in raw:
            raise UsageError(f"--field expects Name=Value, got {raw!r}")
        name, value = raw.split("=", 1)
        name, value = name.strip(), value.strip()
        if not name:
            raise UsageError(f"--field has an empty name: {raw!r}")
        type_name, value_key = FIELD_TYPES.get(name.lower(), DEFAULT_FIELD_TYPE)
        fields.append(
            {
                "name": name,
                "$type": type_name,
                "value": build_field_value(type_name, value_key, value),
            }
        )
    return fields


def resolve_project(token: str, short_name: str, verbose: bool) -> dict[str, Any]:
    """Look a project up by short name.

    Uses /api/admin/projects rather than fetching `<PROJECT>-1`: issue 1 is often
    deleted (JEWEL-1 does not exist), which makes that trick unreliable.
    """
    projects = request(
        "GET",
        "admin/projects",
        token=token,
        params={"query": short_name, "fields": F_PROJECT, "$top": 50},
        verbose=verbose,
    )
    for project in projects:
        if project.get("shortName", "").upper() == short_name.upper():
            return project
    raise NotFoundError(f"No project with short name {short_name!r}.")


def resolve_tag(token: str, name_or_id: str, verbose: bool) -> str:
    """Return a tag's internal id, accepting either an id or a name."""
    tags = fetch_all("tags", token=token, params={"fields": F_TAG}, verbose=verbose)
    for tag in tags:
        if tag.get("id") == name_or_id:
            return name_or_id
    for tag in tags:
        if tag.get("name", "").lower() == name_or_id.lower():
            return tag["id"]
    raise NotFoundError(f"No tag named {name_or_id!r}.")


def apply_command(
    token: str,
    query: str,
    issues: list[str],
    dry_run: bool,
    verbose: bool,
) -> Any:
    """Apply a YouTrack command to one or more issues.

    Commands live at the global /api/commands with the targets in the body.
    There is no per-issue subresource: POST /api/issues/<ID>/commands returns
    404 'No subresource for path commands'.
    """
    body = {"query": query, "issues": [{"idReadable": i} for i in issues]}
    if dry_run:
        # /assist parses and validates without applying anything.
        return request(
            "POST",
            "commands/assist",
            token=token,
            params={"fields": "commands(description,error),issues(idReadable)"},
            json_body=body,
            verbose=verbose,
        )
    return request("POST", "commands", token=token, json_body=body, verbose=verbose)


def require_yes(args: argparse.Namespace, what: str) -> None:
    if not args.yes:
        raise UsageError(f"{what} is destructive; pass --yes to confirm.")


# --------------------------------------------------------------------------
# Commands
# --------------------------------------------------------------------------


def cmd_auth_check(args, token):
    me = request(
        "GET", "users/me", token=token, params={"fields": F_USER}, verbose=args.verbose
    )
    # Returned rather than printed, so --format applies here exactly as it does
    # everywhere else. Contains the login only — never the token.
    return {"login": me.get("login"), "url": BASE_URL, "authenticated": True}


def cmd_issue_get(args, token):
    return request(
        "GET",
        f"issues/{args.issue}",
        token=token,
        params={"fields": args.fields or F_ISSUE},
        verbose=args.verbose,
    )


def cmd_issue_search(args, token):
    return request(
        "GET",
        "issues",
        token=token,
        params={
            "query": args.query,
            "fields": args.fields or F_ISSUE_SHORT,
            "$top": args.top,
            "$skip": args.skip or None,
        },
        verbose=args.verbose,
    )


def cmd_issue_create(args, token):
    if args.raw_payload:
        conflicting = [
            name
            for name, value in (
                ("--project", args.project),
                ("--summary", args.summary),
                ("--description", args.description),
                ("--description-file", args.description_file),
            )
            if value is not None
        ] + (["--field"] if args.field else [])
        if conflicting:
            raise UsageError(
                "--raw-payload sends the file verbatim, so it cannot be combined "
                f"with {', '.join(conflicting)}. Remove either side."
            )
        raw = Path(args.raw_payload).read_text(encoding="utf-8")
        try:
            payload = json.loads(raw)
        except json.JSONDecodeError as exc:
            raise UsageError(
                f"{args.raw_payload} is not valid JSON: {exc.msg} "
                f"(line {exc.lineno}, column {exc.colno})"
            ) from None
    else:
        if not args.project or not args.summary:
            raise UsageError(
                "issue create needs --project and --summary (or --raw-payload)."
            )
        project = resolve_project(token, args.project, args.verbose)
        payload = {
            "summary": args.summary,
            "project": {"id": project["id"]},
        }
        description = None
        if args.description is not None or args.description_file is not None:
            description = read_text_arg(
                args.description, args.description_file, "description"
            )
        if description is not None:
            payload["description"] = description
        custom = parse_field_args(args.field)
        if custom:
            payload["customFields"] = custom

    if args.dry_run:
        return {"dry_run": True, "endpoint": "POST /api/issues", "payload": payload}

    return request(
        "POST",
        "issues",
        token=token,
        params={"fields": "idReadable,summary"},
        json_body=payload,
        verbose=args.verbose,
    )


def cmd_issue_update(args, token):
    payload: dict[str, Any] = {}
    if args.summary is not None:
        payload["summary"] = args.summary
    if args.description is not None or args.description_file is not None:
        payload["description"] = read_text_arg(
            args.description, args.description_file, "description"
        )
    if not payload:
        raise UsageError("Nothing to update: pass --summary and/or --description.")
    if args.dry_run:
        return {
            "dry_run": True,
            "endpoint": f"POST /api/issues/{args.issue}",
            "payload": payload,
        }
    return request(
        "POST",
        f"issues/{args.issue}",
        token=token,
        params={"fields": "idReadable,summary"},
        json_body=payload,
        verbose=args.verbose,
    )


def cmd_issue_field_list(args, token):
    return request(
        "GET",
        f"issues/{args.issue}/customFields",
        token=token,
        params={"fields": F_CUSTOMFIELD, "$top": 100},
        verbose=args.verbose,
    )


def cmd_issue_field_set(args, token):
    fields = request(
        "GET",
        f"issues/{args.issue}/customFields",
        token=token,
        params={"fields": "id,name", "$top": 100},
        verbose=args.verbose,
    )
    match = next(
        (f for f in fields if f.get("name", "").lower() == args.name.lower()), None
    )
    if match is None:
        available = ", ".join(sorted(f.get("name", "?") for f in fields))
        raise NotFoundError(
            f"Issue {args.issue} has no custom field {args.name!r}. Available: {available}"
        )
    type_name, value_key = FIELD_TYPES.get(args.name.lower(), DEFAULT_FIELD_TYPE)
    if args.type:
        # The value key must follow the overridden type, not the field name, or
        # e.g. --type SingleUserIssueCustomField would still send {"name": ...}.
        type_name = args.type
        value_key = VALUE_KEY_BY_TYPE.get(type_name, value_key)
    payload = {
        "$type": type_name,
        "value": build_field_value(type_name, value_key, args.value),
    }
    if args.dry_run:
        return {
            "dry_run": True,
            "endpoint": f"POST /api/issues/{args.issue}/customFields/{match['id']}",
            "payload": payload,
        }
    return request(
        "POST",
        f"issues/{args.issue}/customFields/{match['id']}",
        token=token,
        params={"fields": F_CUSTOMFIELD},
        json_body=payload,
        verbose=args.verbose,
    )


def cmd_command_apply(args, token):
    return apply_command(token, args.query, args.issue, args.dry_run, args.verbose)


def cmd_comment_list(args, token):
    return request(
        "GET",
        f"issues/{args.issue}/comments",
        token=token,
        params={"fields": F_COMMENT, "$top": args.top},
        verbose=args.verbose,
    )


def cmd_comment_add(args, token):
    text = read_text_arg(args.text, args.text_file, "text")
    payload = {"text": text}
    if args.dry_run:
        return {
            "dry_run": True,
            "endpoint": f"POST /api/issues/{args.issue}/comments",
            "payload": payload,
        }
    return request(
        "POST",
        f"issues/{args.issue}/comments",
        token=token,
        params={"fields": "id,text"},
        json_body=payload,
        verbose=args.verbose,
    )


def cmd_comment_update(args, token):
    text = read_text_arg(args.text, args.text_file, "text")
    if args.dry_run:
        return {
            "dry_run": True,
            "endpoint": f"POST /api/issues/{args.issue}/comments/{args.comment}",
            "payload": {"text": text},
        }
    return request(
        "POST",
        f"issues/{args.issue}/comments/{args.comment}",
        token=token,
        params={"fields": "id,text"},
        json_body={"text": text},
        verbose=args.verbose,
    )


def cmd_comment_delete(args, token):
    if args.dry_run:
        return {
            "dry_run": True,
            "endpoint": f"DELETE /api/issues/{args.issue}/comments/{args.comment}",
        }
    require_yes(args, "Deleting a comment")
    return request(
        "DELETE",
        f"issues/{args.issue}/comments/{args.comment}",
        token=token,
        verbose=args.verbose,
    )


def cmd_tag_list(args, token):
    if args.issue:
        return request(
            "GET",
            f"issues/{args.issue}/tags",
            token=token,
            params={"fields": F_TAG},
            verbose=args.verbose,
        )
    return request(
        "GET",
        "tags",
        token=token,
        params={"fields": F_TAG, "$top": args.top},
        verbose=args.verbose,
    )


def cmd_tag_add(args, token):
    tag_id = resolve_tag(token, args.tag, args.verbose)
    if args.dry_run:
        return {
            "dry_run": True,
            "endpoint": f"POST /api/issues/{args.issue}/tags",
            "payload": {"id": tag_id},
        }
    return request(
        "POST",
        f"issues/{args.issue}/tags",
        token=token,
        params={"fields": F_TAG},
        json_body={"id": tag_id},
        verbose=args.verbose,
    )


def cmd_tag_remove(args, token):
    tag_id = resolve_tag(token, args.tag, args.verbose)
    if args.dry_run:
        return {
            "dry_run": True,
            "endpoint": f"DELETE /api/issues/{args.issue}/tags/{tag_id}",
        }
    require_yes(args, "Removing a tag")
    return request(
        "DELETE", f"issues/{args.issue}/tags/{tag_id}", token=token, verbose=args.verbose
    )


def cmd_link_list(args, token):
    links = request(
        "GET",
        f"issues/{args.issue}/links",
        token=token,
        params={"fields": F_LINK},
        verbose=args.verbose,
    )
    # The API returns every link type, most of them empty. Only the populated
    # ones are interesting.
    return [link for link in links if link.get("issues")]


def cmd_link_add(args, token):
    query = f"{args.type} {args.target}"
    return apply_command(token, query, [args.issue], args.dry_run, args.verbose)


def cmd_link_types(args, token):
    return request(
        "GET",
        "issueLinkTypes",
        token=token,
        params={"fields": F_LINKTYPE, "$top": args.top},
        verbose=args.verbose,
    )


def cmd_work_list(args, token):
    return request(
        "GET",
        f"issues/{args.issue}/timeTracking/workItems",
        token=token,
        params={"fields": F_WORK, "$top": args.top},
        verbose=args.verbose,
    )


def cmd_work_log(args, token):
    payload: dict[str, Any] = {"duration": {"presentation": args.duration}}
    if args.date:
        try:
            parsed = _dt.datetime.strptime(args.date, "%Y-%m-%d")
        except ValueError:
            raise UsageError(f"--date must be YYYY-MM-DD, got {args.date!r}") from None
        # Local midnight, not UTC midnight: the user means "that day where they
        # are". UTC midnight lands on the previous day for anyone west of UTC.
        payload["date"] = int(parsed.timestamp() * 1000)
    if args.text:
        payload["text"] = args.text
    if args.dry_run:
        return {
            "dry_run": True,
            "endpoint": f"POST /api/issues/{args.issue}/timeTracking/workItems",
            "payload": payload,
        }
    return request(
        "POST",
        f"issues/{args.issue}/timeTracking/workItems",
        token=token,
        params={"fields": F_WORK},
        json_body=payload,
        verbose=args.verbose,
    )


def cmd_user_me(args, token):
    return request(
        "GET", "users/me", token=token, params={"fields": F_USER}, verbose=args.verbose
    )


def cmd_user_search(args, token):
    return request(
        "GET",
        "users",
        token=token,
        params={"query": args.query, "fields": F_USER, "$top": args.top},
        verbose=args.verbose,
    )


def cmd_project_get(args, token):
    return resolve_project(token, args.project, args.verbose)


def cmd_project_fields(args, token):
    project = resolve_project(token, args.project, args.verbose)
    return request(
        "GET",
        f"admin/projects/{project['id']}/customFields",
        token=token,
        params={
            "fields": "field(name,fieldType(id)),canBeEmpty,emptyFieldText",
            "$top": 100,
        },
        verbose=args.verbose,
    )


def cmd_saved_queries(args, token):
    return request(
        "GET",
        "savedQueries",
        token=token,
        params={"fields": "id,name,query", "$top": args.top},
        verbose=args.verbose,
    )


def cmd_attach_list(args, token):
    return request(
        "GET",
        f"issues/{args.issue}/attachments",
        token=token,
        params={"fields": F_ATTACH, "$top": args.top},
        verbose=args.verbose,
    )


def cmd_attach_upload(args, token):
    paths = [Path(p) for p in args.file]
    # Validate up front so --dry-run rejects exactly what the real upload would;
    # p.stat() alone would happily accept a directory.
    for path in paths:
        if not path.is_file():
            raise UsageError(f"Not a file: {path}")
    if args.dry_run:
        return {
            "dry_run": True,
            "endpoint": f"POST /api/issues/{args.issue}/attachments",
            "files": [{"name": p.name, "size": p.stat().st_size} for p in paths],
        }
    body, content_type = encode_multipart(paths)
    return request(
        "POST",
        f"issues/{args.issue}/attachments",
        token=token,
        params={"fields": F_ATTACH},
        raw_body=body,
        content_type=content_type,
        verbose=args.verbose,
    )


def cmd_attach_download(args, token):
    attachments = fetch_all(
        f"issues/{args.issue}/attachments",
        token=token,
        params={"fields": "id,name,size,url"},
        verbose=args.verbose,
    )
    if args.attachment:
        attachments = [a for a in attachments if a.get("id") == args.attachment]
        if not attachments:
            raise NotFoundError(
                f"Issue {args.issue} has no attachment {args.attachment!r}."
            )

    out = Path(args.out)
    multiple = len(attachments) > 1 or args.all
    if multiple:
        out.mkdir(parents=True, exist_ok=True)

    written = []
    used: set[str] = set()
    for attachment in attachments:
        # `url` is relative and carries a `sign` capability token, so it is
        # resolved against the pinned base and never printed unless --verbose.
        url = urllib.parse.urljoin(BASE_URL, attachment["url"])
        data = request(
            "GET",
            "",
            token=token,
            absolute_url=url,
            accept="application/octet-stream",
            expect_bytes=True,
            verbose=args.verbose,
        )
        if multiple:
            # YouTrack allows two attachments to share a name; without this the
            # second would silently overwrite the first.
            target = out / unique_name(safe_filename(attachment["name"]), used, out)
        else:
            target = out
        target.parent.mkdir(parents=True, exist_ok=True)
        write_new_file(target, data)
        written.append({"id": attachment["id"], "path": str(target), "bytes": len(data)})
    return written


def cmd_attach_delete(args, token):
    if args.dry_run:
        return {
            "dry_run": True,
            "endpoint": f"DELETE /api/issues/{args.issue}/attachments/{args.attachment}",
        }
    require_yes(args, "Deleting an attachment")
    return request(
        "DELETE",
        f"issues/{args.issue}/attachments/{args.attachment}",
        token=token,
        verbose=args.verbose,
    )


# --------------------------------------------------------------------------
# Argument parsing
# --------------------------------------------------------------------------


def build_parser() -> argparse.ArgumentParser:
    common = argparse.ArgumentParser(add_help=False)
    common.add_argument(
        "--token-op-path",
        metavar="op://VAULT/ITEM/FIELD",
        help="Read the API token from 1Password. Takes precedence over "
        f"${ENV_TOKEN} and ${ENV_TOKEN_OP_PATH}.",
    )
    common.add_argument(
        "--format",
        choices=("json", "table", "ids"),
        default="json",
        help="Output format (default: json).",
    )
    common.add_argument(
        "--verbose",
        action="store_true",
        help="Log requests to stderr. Never logs headers, which carry the token.",
    )

    # `common` is attached to every leaf parser AND to the root, so both
    # `yt.py --verbose issue get X` and `yt.py issue get X --verbose` work.
    # argparse would otherwise only accept the trailing form.
    parser = argparse.ArgumentParser(
        prog="yt.py",
        parents=[common],
        description=f"Command-line client for {BASE_URL}.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    top = parser.add_subparsers(dest="group", required=True)

    def leaf(sub, name, func, help_text, *, dry_run=False, yes=False):
        p = sub.add_parser(name, parents=[common], help=help_text)
        p.set_defaults(func=func)
        if dry_run:
            p.add_argument(
                "--dry-run",
                action="store_true",
                help="Show what would be sent without sending it.",
            )
        if yes:
            p.add_argument("--yes", action="store_true", help="Confirm a destructive action.")
        return p

    # auth
    auth = top.add_parser("auth", help="Authentication.").add_subparsers(
        dest="cmd", required=True
    )
    leaf(auth, "check", cmd_auth_check, "Verify the token works (prints login only).")

    # issue
    issue = top.add_parser("issue", help="Issues.").add_subparsers(dest="cmd", required=True)

    p = leaf(issue, "get", cmd_issue_get, "Fetch one issue.")
    p.add_argument("issue", help="Issue id, e.g. JEWEL-1367.")
    p.add_argument("--fields", help="Override the field selection.")

    p = leaf(issue, "search", cmd_issue_search, "Search issues.")
    p.add_argument("query", help="YouTrack query, e.g. 'project: JEWEL #Unresolved'.")
    p.add_argument("--top", type=int, default=50, help="Max results (default 50).")
    p.add_argument("--skip", type=int, default=0, help="Results to skip.")
    p.add_argument("--fields", help="Override the field selection.")

    p = leaf(issue, "create", cmd_issue_create, "Create an issue.", dry_run=True)
    p.add_argument("--project", help="Project short name, e.g. JEWEL.")
    p.add_argument("--summary", help="Issue summary.")
    p.add_argument("--description", help="Issue description.")
    p.add_argument("--description-file", help="Read the description from a file.")
    p.add_argument(
        "--field",
        action="append",
        default=[],
        metavar="Name=Value",
        help="Custom field, repeatable. E.g. --field Type=Task --field State=Open.",
    )
    p.add_argument("--raw-payload", help="Send this JSON file verbatim instead.")

    p = leaf(issue, "update", cmd_issue_update, "Update summary/description.", dry_run=True)
    p.add_argument("issue")
    p.add_argument("--summary")
    p.add_argument("--description")
    p.add_argument("--description-file")

    field = issue.add_parser("field", help="Custom fields.").add_subparsers(
        dest="subcmd", required=True
    )
    p = leaf(field, "list", cmd_issue_field_list, "List an issue's custom fields.")
    p.add_argument("issue")
    p = leaf(field, "set", cmd_issue_field_set, "Set a custom field.", dry_run=True)
    p.add_argument("issue")
    p.add_argument("name", help="Field name, e.g. State.")
    p.add_argument("value", help="Field value, e.g. 'In Review'.")
    p.add_argument("--type", help="Override the inferred $type.")

    # command
    command = top.add_parser("command", help="YouTrack commands.").add_subparsers(
        dest="cmd", required=True
    )
    p = leaf(
        command,
        "apply",
        cmd_command_apply,
        "Apply a command to one or more issues.",
        dry_run=True,
    )
    p.add_argument("query", help="Command text, e.g. 'State In Review'.")
    p.add_argument(
        "--issue",
        action="append",
        required=True,
        help="Target issue, repeatable for a bulk apply.",
    )

    # comment
    comment = top.add_parser("comment", help="Comments.").add_subparsers(
        dest="cmd", required=True
    )
    p = leaf(comment, "list", cmd_comment_list, "List comments.")
    p.add_argument("issue")
    p.add_argument("--top", type=int, default=50)
    p = leaf(comment, "add", cmd_comment_add, "Add a comment.", dry_run=True)
    p.add_argument("issue")
    p.add_argument("--text")
    p.add_argument("--text-file")
    p = leaf(comment, "update", cmd_comment_update, "Update a comment.", dry_run=True)
    p.add_argument("issue")
    p.add_argument("comment")
    p.add_argument("--text")
    p.add_argument("--text-file")
    p = leaf(comment, "delete", cmd_comment_delete, "Delete a comment.", dry_run=True, yes=True)
    p.add_argument("issue")
    p.add_argument("comment")

    # tag
    tag = top.add_parser("tag", help="Tags.").add_subparsers(dest="cmd", required=True)
    p = leaf(tag, "list", cmd_tag_list, "List tags, instance-wide or on an issue.")
    p.add_argument("--issue")
    p.add_argument("--top", type=int, default=100)
    p = leaf(tag, "add", cmd_tag_add, "Add a tag to an issue.", dry_run=True)
    p.add_argument("issue")
    p.add_argument("tag", help="Tag name or internal id.")
    p = leaf(tag, "remove", cmd_tag_remove, "Remove a tag from an issue.", dry_run=True, yes=True)
    p.add_argument("issue")
    p.add_argument("tag")

    # link
    link = top.add_parser("link", help="Issue links.").add_subparsers(
        dest="cmd", required=True
    )
    p = leaf(link, "list", cmd_link_list, "List an issue's populated links.")
    p.add_argument("issue")
    p = leaf(link, "add", cmd_link_add, "Link two issues.", dry_run=True)
    p.add_argument("issue", help="Issue the link starts from.")
    p.add_argument("--type", required=True, help="e.g. 'relates to', 'depends on'.")
    p.add_argument("--target", required=True, help="Issue the link points at.")
    p = leaf(link, "types", cmd_link_types, "List available link types.")
    p.add_argument("--top", type=int, default=50)

    # work
    work = top.add_parser("work", help="Time tracking.").add_subparsers(
        dest="cmd", required=True
    )
    p = leaf(work, "list", cmd_work_list, "List work items.")
    p.add_argument("issue")
    p.add_argument("--top", type=int, default=50)
    p = leaf(work, "log", cmd_work_log, "Log work.", dry_run=True)
    p.add_argument("issue")
    p.add_argument("--duration", required=True, help="e.g. '2h 30m'.")
    p.add_argument("--date", help="YYYY-MM-DD (default: today).")
    p.add_argument("--text", help="Work description.")

    # user
    user = top.add_parser("user", help="Users.").add_subparsers(dest="cmd", required=True)
    leaf(user, "me", cmd_user_me, "Show the authenticated user.")
    p = leaf(user, "search", cmd_user_search, "Search users.")
    p.add_argument("query")
    p.add_argument("--top", type=int, default=10)

    # project
    project = top.add_parser("project", help="Projects.").add_subparsers(
        dest="cmd", required=True
    )
    p = leaf(project, "get", cmd_project_get, "Look up a project by short name.")
    p.add_argument("project")
    p = leaf(project, "fields", cmd_project_fields, "List a project's custom fields.")
    p.add_argument("project")

    # saved queries
    sq = top.add_parser("saved-queries", parents=[common], help="List saved queries.")
    sq.set_defaults(func=cmd_saved_queries, group="saved-queries", cmd=None)
    sq.add_argument("--top", type=int, default=50)

    # attachments
    attach = top.add_parser("attach", help="Attachments.").add_subparsers(
        dest="cmd", required=True
    )
    p = leaf(attach, "list", cmd_attach_list, "List attachments.")
    p.add_argument("issue")
    p.add_argument("--top", type=int, default=100)
    p = leaf(attach, "upload", cmd_attach_upload, "Upload files.", dry_run=True)
    p.add_argument("issue")
    p.add_argument("file", nargs="+")
    p = leaf(attach, "download", cmd_attach_download, "Download attachments.")
    p.add_argument("issue")
    which = p.add_mutually_exclusive_group(required=True)
    which.add_argument("--attachment", help="Attachment id.")
    which.add_argument("--all", action="store_true", help="Download every attachment.")
    p.add_argument("--out", required=True, help="Output file, or directory with --all.")
    p = leaf(attach, "delete", cmd_attach_delete, "Delete an attachment.", dry_run=True, yes=True)
    p.add_argument("issue")
    p.add_argument("attachment")

    return parser


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)

    try:
        token = resolve_token(args.token_op_path)
        result = args.func(args, token)
        if result is not None:
            emit(result, args.format)
        return EXIT_OK
    except YtError as exc:
        print(f"error: {redact(str(exc))}", file=sys.stderr)
        return exc.exit_code
    except FileNotFoundError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return EXIT_USAGE
    except KeyboardInterrupt:
        return 130
    except Exception as exc:  # noqa: BLE001 - last resort, still redacted
        print(f"error: {redact(f'{type(exc).__name__}: {exc}')}", file=sys.stderr)
        return EXIT_ERROR


if __name__ == "__main__":
    sys.exit(main())
