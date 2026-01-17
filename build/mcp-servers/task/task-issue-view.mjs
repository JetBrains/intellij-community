// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

const DEFAULT_META_MAX_CHARS = 400

const VIEW_VALUES = new Set(['summary', 'meta'])

const SUMMARY_FIELDS = [
  {from: 'id', to: 'id'},
  {from: 'title', to: 'title'},
  {from: 'status', to: 'status'},
  {from: 'priority', to: 'priority'},
  {from: 'issue_type', to: 'type'},
  {from: 'assignee', to: 'assignee'},
  {from: 'parent', to: 'parent'},
  {from: 'ready_children', to: 'ready_children'},
  {from: 'is_new', to: 'is_new'}
]

const META_FIELDS = [
  {from: 'description', to: 'description'},
  {from: 'design', to: 'design'},
  {from: 'acceptance_criteria', to: 'acceptance'}
]

function normalizeView(view) {
  if (!view) return 'summary'
  const normalized = String(view).trim().toLowerCase()
  if (!VIEW_VALUES.has(normalized)) {
    throw new Error(`Invalid view: ${view}`)
  }
  return normalized
}

function normalizeMaxChars(maxChars) {
  if (maxChars === undefined || maxChars === null) return DEFAULT_META_MAX_CHARS
  const parsed = Number.parseInt(maxChars, 10)
  if (!Number.isFinite(parsed) || parsed <= 0) return null
  return parsed
}

function addField(target, source, fromKey, toKey) {
  if (!Object.prototype.hasOwnProperty.call(source, fromKey)) return
  const value = source[fromKey]
  if (value === undefined || value === null) return
  if (typeof value === 'string' && value.trim() === '') return
  if (Array.isArray(value) && value.length === 0) return
  target[toKey] = value
}

function applyTruncation(result, fields, maxChars) {
  if (!maxChars) return result
  const truncated = []
  for (const field of fields) {
    const value = result[field]
    if (typeof value !== 'string') continue
    if (value.length <= maxChars) continue
    result[field] = value.slice(0, maxChars) + '...'
    truncated.push(field)
  }
  if (truncated.length > 0) {
    result.meta_truncated = truncated
  }
  return result
}

export function buildIssueView(issue, {view, meta_max_chars} = {}) {
  const normalized = normalizeView(view)
  const result = {}

  for (const field of SUMMARY_FIELDS) {
    addField(result, issue, field.from, field.to)
  }

  if (normalized === 'meta') {
    for (const field of META_FIELDS) {
      addField(result, issue, field.from, field.to)
    }
    const maxChars = normalizeMaxChars(meta_max_chars)
    applyTruncation(result, META_FIELDS.map(field => field.to), maxChars)
  }

  return result
}
