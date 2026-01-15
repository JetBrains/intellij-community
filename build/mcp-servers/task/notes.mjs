// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {bdAddComment} from './bd-client.mjs'

export const FINDING_PREFIX = 'FINDING:'
export const DECISION_PREFIX = 'KEY DECISION:'

function normalizeEntry(entry) {
  if (typeof entry !== 'string') return null
  const normalized = entry.trim()
  return normalized ? normalized : null
}

function normalizePrefixedEntry(entry, prefix) {
  const normalized = normalizeEntry(entry)
  if (!normalized) return null
  if (prefix && normalized.startsWith(prefix)) {
    const stripped = normalized.slice(prefix.length).trim()
    return stripped || null
  }
  return normalized
}

function normalizeEntries(entries, prefix) {
  if (!Array.isArray(entries)) return []
  const normalized = []
  for (const entry of entries) {
    const value = normalizePrefixedEntry(entry, prefix)
    if (value) normalized.push(value)
  }
  return normalized
}

function mergeEntries(primary, secondary) {
  const result = []
  const seen = new Set()
  for (const entry of [...primary, ...secondary]) {
    const normalized = normalizeEntry(entry)
    if (!normalized || seen.has(normalized)) continue
    seen.add(normalized)
    result.push(normalized)
  }
  return result
}

// Parse notes into structured sections (legacy JSON + text) for backward compatibility.
function parseNotesOnly(notes) {
  const sections = {findings: [], decisions: []}
  if (!notes) return sections

  const trimmed = notes.trim()
  if (trimmed.startsWith('{')) {
    try {
      const parsed = JSON.parse(trimmed)
      sections.findings = normalizeEntries(parsed.findings, FINDING_PREFIX)
      sections.decisions = normalizeEntries(parsed.decisions, DECISION_PREFIX)
      if (parsed.pending_close) {
        sections.pending_close = parsed.pending_close
      }
      return sections
    } catch (e) {
      // Fall through to text parsing
    }
  }

  for (const line of notes.split('\n')) {
    const trimmedLine = line.trim()
    if (trimmedLine.startsWith(FINDING_PREFIX)) {
      const value = normalizePrefixedEntry(trimmedLine, FINDING_PREFIX)
      if (value) sections.findings.push(value)
    } else if (trimmedLine.startsWith(DECISION_PREFIX)) {
      const value = normalizePrefixedEntry(trimmedLine, DECISION_PREFIX)
      if (value) sections.decisions.push(value)
    }
  }
  return sections
}

function parseComments(comments) {
  const sections = {findings: [], decisions: []}
  if (!Array.isArray(comments)) return sections

  for (const comment of comments) {
    const text = typeof comment?.text === 'string' ? comment.text.trim() : ''
    if (!text) continue
    if (text.startsWith(FINDING_PREFIX)) {
      const value = normalizePrefixedEntry(text, FINDING_PREFIX)
      if (value) sections.findings.push(value)
    } else if (text.startsWith(DECISION_PREFIX)) {
      const value = normalizePrefixedEntry(text, DECISION_PREFIX)
      if (value) sections.decisions.push(value)
    }
  }
  return sections
}

// Build notes from comments + legacy notes for API compatibility.
export function parseNotes(notes, comments) {
  const hasNotes = typeof notes === 'string' && notes.length > 0
  const notesSections = parseNotesOnly(notes)
  const commentSections = parseComments(comments)
  const findings = mergeEntries(commentSections.findings, notesSections.findings)
  const decisions = mergeEntries(commentSections.decisions, notesSections.decisions)
  const result = {findings, decisions}
  if (notesSections.pending_close) result.pending_close = notesSections.pending_close

  if (findings.length > 0 || decisions.length > 0 || result.pending_close || hasNotes) {
    return result
  }
  return null
}

export function buildPendingNotes(pendingClose) {
  if (!pendingClose) return ''
  return JSON.stringify({pending_close: pendingClose}, null, 2)
}

function filterNewEntries(entries, existingSet) {
  const result = []
  if (!Array.isArray(entries)) return result

  for (const entry of entries) {
    const normalized = normalizeEntry(entry)
    if (!normalized || existingSet.has(normalized)) continue
    existingSet.add(normalized)
    result.push(normalized)
  }
  return result
}

export async function addSectionComments(issueId, findings, decisions) {
  for (const finding of findings) {
    await bdAddComment(issueId, `${FINDING_PREFIX} ${finding}`)
  }
  for (const decision of decisions) {
    await bdAddComment(issueId, `${DECISION_PREFIX} ${decision}`)
  }
}

export function prepareSectionUpdates(issue, incomingFindings, incomingDecisions) {
  const notesSections = parseNotesOnly(issue.notes)
  const commentSections = parseComments(issue['comments'])

  const existingFindings = mergeEntries(commentSections.findings, notesSections.findings)
  const existingDecisions = mergeEntries(commentSections.decisions, notesSections.decisions)

  const normalizedFindings = normalizeEntries(incomingFindings, FINDING_PREFIX)
  const normalizedDecisions = normalizeEntries(incomingDecisions, DECISION_PREFIX)

  const newFindings = filterNewEntries(normalizedFindings, new Set(existingFindings))
  const newDecisions = filterNewEntries(normalizedDecisions, new Set(existingDecisions))

  const migrateFindings = filterNewEntries(notesSections.findings, new Set(commentSections.findings))
  const migrateDecisions = filterNewEntries(notesSections.decisions, new Set(commentSections.decisions))

  const findingsToAdd = mergeEntries(migrateFindings, newFindings)
  const decisionsToAdd = mergeEntries(migrateDecisions, newDecisions)

  const finalFindings = mergeEntries(existingFindings, newFindings)
  const finalDecisions = mergeEntries(existingDecisions, newDecisions)

  const shouldStripNotes = notesSections.findings.length > 0 || notesSections.decisions.length > 0

  return {
    notesSections,
    finalFindings,
    finalDecisions,
    findingsToAdd,
    decisionsToAdd,
    shouldStripNotes
  }
}
