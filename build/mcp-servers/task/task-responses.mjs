// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

export function buildError(message) {
  return {kind: 'error', next: 'await_user', message}
}

export function buildEmpty(message = 'No ready tasks found.', next = 'provide_request') {
  return {kind: 'empty', next, message}
}

export function buildIssue(issue, {next = 'continue', memory} = {}) {
  const response = {kind: 'issue', next, issue}
  if (memory) response.memory = memory
  return response
}

export function buildSummary(issue, next = 'await_user') {
  return {kind: 'summary', next, issue}
}

export function buildProgress({memory, status, next = 'await_user'}) {
  const response = {kind: 'progress', next, status}
  if (memory) response.memory = memory
  return response
}

export function buildCreated(payload, next = 'await_user') {
  return {kind: 'created', next, ...payload}
}

export function buildClosed(payload, nextOverride) {
  const next = nextOverride ?? (payload.next_ready ? 'start_next_ready' : 'await_user')
  return {kind: 'closed', next, ...payload}
}

export function buildNeedUser({question, header, choices, next}) {
  return {
    kind: 'need_user',
    next,
    question,
    header,
    choices,
    multiSelect: false
  }
}

export function buildTaskChoice(issue) {
  return {label: issue.title, description: issue.id, action: 'select_task', id: issue.id}
}

export function buildCreateSubTaskChoice(parentEpic) {
  return {label: 'Create sub-task', description: `Under ${parentEpic}`, action: 'create_sub_task', parent: parentEpic}
}

export function buildStartNewTaskChoice() {
  return {label: 'Start new task', description: 'Create a new epic', action: 'start_new_task'}
}
