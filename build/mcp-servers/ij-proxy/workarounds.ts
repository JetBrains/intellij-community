// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

type VersionParts = number[]

export interface ParsedIdeVersion {
  raw: string
  full?: VersionParts
  build?: VersionParts
}

type ParsedVersionSpec = {
  parts: VersionParts
  kind: 'full' | 'build'
}

const FULL_VERSION_RE = /\b\d{4}\.\d+(?:\.\d+){0,2}\b/
const BUILD_VERSION_RE = /\b\d{3}\.\d+(?:\.\d+)?\b/
const SNAPSHOT_BUILD_RE = /\b(\d{3})\.SNAPSHOT\b/i
const ANY_VERSION_RE = /\d+(?:\.\d+)+/
const DISABLE_ALL_ENV = 'JETBRAINS_MCP_PROXY_DISABLE_WORKAROUNDS'
const DISABLE_KEYS_ENV = 'JETBRAINS_MCP_PROXY_DISABLE_WORKAROUND_KEYS'
const DEBUG_ENV = 'JETBRAINS_MCP_PROXY_WORKAROUND_DEBUG'

// Keep in sync with WORKAROUND_FIXED_IN.
export enum WorkaroundKey {
  // Add new workaround keys here.
  SearchInFilesByRegexDirectoryScopeIgnored = 'search_in_files_by_regex_directory_scope_ignored'
}

// Map workaround key -> version when fixed (empty string means not fixed yet).
export const WORKAROUND_FIXED_IN: Record<WorkaroundKey, string> = {
  [WorkaroundKey.SearchInFilesByRegexDirectoryScopeIgnored]: '261.SNAPSHOT'
}

let currentIdeVersion: ParsedIdeVersion | null = null

export function setIdeVersion(rawVersion: string | null | undefined): void {
  if (!rawVersion) {
    currentIdeVersion = null
    return
  }
  currentIdeVersion = parseIdeVersion(rawVersion)
}

export function getIdeVersion(): ParsedIdeVersion | null {
  return currentIdeVersion
}

export function shouldApplyWorkaround(key: WorkaroundKey): boolean {
  if (isWorkaroundDisabled(key)) {
    logDebug(`Workaround ${key} not used (disabled by env)`)
    return false
  }
  const fixedInRaw = (WORKAROUND_FIXED_IN[key] ?? '').trim()
  if (!fixedInRaw) return true

  const ideVersion = currentIdeVersion
  if (!ideVersion) return true

  const fixedSpec = parseVersionSpec(fixedInRaw)
  if (!fixedSpec) return true

  const currentParts = fixedSpec.kind === 'build'
    ? (ideVersion.build ?? deriveBuildFromFull(ideVersion.full))
    : ideVersion.full
  if (!currentParts) return true

  const comparison = compareVersionParts(currentParts, fixedSpec.parts)
  if (comparison >= 0) {
    logDebug(`Workaround ${key} not used; fixed in ${fixedInRaw}, ide ${ideVersion.raw}`)
    return false
  }
  return true
}

function isWorkaroundDisabled(key: WorkaroundKey): boolean {
  const disabledAll = process.env[DISABLE_ALL_ENV]
  if (disabledAll && disabledAll !== 'false' && disabledAll !== '0') return true
  const disabledKeys = process.env[DISABLE_KEYS_ENV]
  if (!disabledKeys) return false
  return disabledKeys
    .split(',')
    .map((entry) => entry.trim())
    .filter((entry) => entry.length > 0)
    .includes(key)
}

function logDebug(message: string): void {
  const enabled = process.env[DEBUG_ENV]
  if (!enabled || enabled === '0' || enabled === 'false') return
  process.stderr.write(`[ij-mcp-proxy] ${message}\n`)
}

function parseIdeVersion(raw: string): ParsedIdeVersion {
  const full = extractVersionParts(raw, FULL_VERSION_RE)
  const build = extractVersionParts(raw, BUILD_VERSION_RE)
  return {
    raw,
    full: full ?? undefined,
    build: build ?? undefined
  }
}

function parseVersionSpec(version: string): ParsedVersionSpec | null {
  const snapshotMatch = version.match(SNAPSHOT_BUILD_RE)
  if (snapshotMatch) {
    const train = Number.parseInt(snapshotMatch[1], 10)
    if (!Number.isNaN(train)) {
      return {parts: [train], kind: 'build'}
    }
  }
  const match = version.match(ANY_VERSION_RE)
  if (!match) return null
  const parts = parseVersionParts(match[0])
  if (!parts) return null
  const kind: ParsedVersionSpec['kind'] = parts[0] >= 1000 ? 'full' : 'build'
  return {parts, kind}
}

function extractVersionParts(raw: string, regex: RegExp): VersionParts | null {
  const match = raw.match(regex)
  if (!match) return null
  return parseVersionParts(match[0])
}

function parseVersionParts(value: string): VersionParts | null {
  const parts = value.split('.').map((part) => Number.parseInt(part, 10))
  if (parts.some((part) => Number.isNaN(part))) return null
  return parts
}

function deriveBuildFromFull(full: VersionParts | undefined): VersionParts | null {
  if (!full || full.length < 2) return null
  const year = full[0]
  const minor = full[1]
  if (!Number.isFinite(year) || !Number.isFinite(minor)) return null
  if (year < 2000 || year > 2100) return null
  const train = (year - 2000) * 10 + minor
  return [train]
}

function compareVersionParts(left: VersionParts, right: VersionParts): number {
  const maxLength = Math.max(left.length, right.length)
  for (let i = 0; i < maxLength; i += 1) {
    const leftValue = left[i] ?? 0
    const rightValue = right[i] ?? 0
    if (leftValue !== rightValue) {
      return leftValue - rightValue
    }
  }
  return 0
}
