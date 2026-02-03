// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

export function buildError(message) {
  return {kind: 'error', next: 'await_user', message}
}

export function buildEmpty(message = 'No in-progress tasks found.', next = 'await_user') {
  return {kind: 'empty', next, message}
}

export function buildIssue(issue, {next = 'continue', memory} = {}) {
  const response = {kind: 'issue', next, issue}
  if (memory) response.memory = memory
  return response
}

export function buildSummary(issues, {next = 'await_user'} = {}) {
  const normalizedIssues = Array.isArray(issues) ? issues : (issues ? [issues] : [])
  return {kind: 'summary', next, issues: normalizedIssues}
}

export function buildProgress({memory, status, next = 'await_user'}) {
  const response = {kind: 'progress', next, status}
  if (memory) response.memory = memory
  return response
}

export function buildCreated(payload, next = 'continue') {
  return {kind: 'created', next, ...payload}
}

export function buildUpdated(payload, next = 'continue') {
  return {kind: 'updated', next, ...payload}
}

export function buildClosed(payload, nextOverride) {
  const next = nextOverride ?? (payload.next_ready ? 'start_next_ready' : 'await_user')
  return {kind: 'closed', next, ...payload}
}

