#!/usr/bin/env bash
# Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
# Terminate all running ij-mcp-proxy processes on this host.
# Safe to run when none are alive — exits 0 with no output.
#
# Useful after rebuilding the bundle: MCP clients (Claude CLI, Codex, etc.) spawn
# ij-proxy via stdio and keep it alive for the whole session, so they don't pick
# up a new dist until the proxy is respawned.

set -u

pattern='ij-mcp-proxy\.mjs'
pids=$(pgrep -f "$pattern" || true)

if [[ -z "${pids// }" ]]; then
  echo "No ij-proxy processes running."
  exit 0
fi

echo "Killing ij-proxy processes:"
ps -o pid,lstart,command -p $pids

kill $pids 2>/dev/null || true

# Escalate to SIGKILL for anything still alive after a short grace period.
for _ in 1 2 3 4 5; do
  sleep 0.2
  remaining=$(pgrep -f "$pattern" || true)
  [[ -z "${remaining// }" ]] && break
done

remaining=$(pgrep -f "$pattern" || true)
if [[ -n "${remaining// }" ]]; then
  echo "Forcing SIGKILL on: $remaining"
  kill -9 $remaining 2>/dev/null || true
fi

survivors=$(pgrep -f "$pattern" || true)
if [[ -n "${survivors// }" ]]; then
  echo "Warning: some processes survived: $survivors" >&2
  exit 1
fi

echo "Done."
