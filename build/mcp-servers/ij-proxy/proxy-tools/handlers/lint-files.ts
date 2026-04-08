// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {extractStructuredContent, requireString, toPositiveInt} from '../shared'
import type {AnalysisCapabilities, ToolArgs, UpstreamToolCaller} from '../types'

interface LintProblem {
  severity: string
  description: string
  lineText: string
  line?: number
  column?: number
}

interface LintFileResult {
  filePath: string
  problems: LintProblem[]
  timedOut?: boolean
}

type MinSeverity = 'warning' | 'error'

export async function handleLintFilesTool(
  args: ToolArgs,
  callUpstreamTool: UpstreamToolCaller,
  capabilities: AnalysisCapabilities
): Promise<string> {
  const filePaths = normalizeFilePaths(args.file_paths)
  const minSeverity = normalizeMinSeverity(args.min_severity)
  const timeout = toPositiveInt(args.timeout, undefined, 'timeout')

  if (capabilities.hasLintFiles) {
    const result = await callUpstreamTool('lint_files', {
      file_paths: filePaths,
      min_severity: minSeverity,
      ...(timeout !== undefined ? {timeout} : {})
    })
    const structured = extractStructuredContent(result)
    if (structured == null) {
      throw new Error('Upstream lint_files returned unexpected result')
    }
    return JSON.stringify(structured)
  }

  if (!capabilities.supportsLintFiles) {
    throw new Error('lint_files is not supported by this IDE version')
  }

  return await lintFilesLegacy(filePaths, minSeverity, timeout, callUpstreamTool)
}

function normalizeFilePaths(value: unknown): string[] {
  if (!Array.isArray(value)) {
    throw new Error('file_paths must be an array of non-empty strings')
  }

  const result: string[] = []
  const seen = new Set<string>()
  for (const rawPath of value) {
    if (typeof rawPath !== 'string' || rawPath.trim().length === 0) {
      throw new Error('file_paths must contain non-empty strings')
    }
    const normalizedPath = rawPath.trim()
    if (seen.has(normalizedPath)) continue
    seen.add(normalizedPath)
    result.push(normalizedPath)
  }

  if (result.length === 0) {
    throw new Error('file_paths must contain at least one path')
  }
  return result
}

function normalizeMinSeverity(value: unknown): MinSeverity {
  if (value === undefined || value === null) return 'warning'
  const normalized = requireString(value, 'min_severity').trim().toLowerCase()
  if (normalized === 'warning' || normalized === 'error') {
    return normalized
  }
  throw new Error('min_severity must be one of: warning, error')
}

async function lintFilesLegacy(
  filePaths: string[],
  minSeverity: MinSeverity,
  timeout: number | undefined,
  callUpstreamTool: UpstreamToolCaller
): Promise<string> {
  const startedAt = Date.now()
  const items: LintFileResult[] = []
  let more = false

  for (const filePath of filePaths) {
    const remainingTimeout = timeout === undefined ? undefined : Math.max(0, timeout - (Date.now() - startedAt))
    if (remainingTimeout !== undefined && remainingTimeout <= 0) {
      more = true
      break
    }

    const result = await callUpstreamTool('get_file_problems', {
      filePath,
      errorsOnly: minSeverity === 'error',
      ...(remainingTimeout !== undefined ? {timeout: remainingTimeout} : {})
    })
    const item = parseLegacyLintFileResult(result, filePath)
    if (item.problems.length > 0) {
      items.push(item)
    }
    if (item.timedOut === true) {
      more = true
      break
    }
  }

  return JSON.stringify(more ? {items, more: true} : {items})
}

function parseLegacyLintFileResult(result: unknown, fallbackPath: string): LintFileResult {
  const structured = extractStructuredContent(result)
  if (!isRecord(structured)) {
    throw new Error('Upstream get_file_problems returned unexpected result')
  }

  const filePath = typeof structured.filePath === 'string' && structured.filePath.length > 0
    ? structured.filePath
    : fallbackPath
  const rawErrors = Array.isArray(structured.errors) ? structured.errors : []
  const problems = rawErrors.map(coerceLegacyProblem).filter((problem): problem is LintProblem => problem != null)
  const timedOut = structured.timedOut === true ? true : undefined

  return {
    filePath,
    problems,
    ...(timedOut ? {timedOut} : {})
  }
}

function coerceLegacyProblem(value: unknown): LintProblem | null {
  if (!isRecord(value)) return null
  const severity = typeof value.severity === 'string' ? value.severity : ''
  const description = typeof value.description === 'string' ? value.description : ''
  const lineText = typeof value.lineContent === 'string'
    ? value.lineContent
    : (typeof value.lineText === 'string' ? value.lineText : '')

  const problem: LintProblem = {
    severity,
    description,
    lineText
  }
  if (typeof value.line === 'number') {
    problem.line = value.line
  }
  if (typeof value.column === 'number') {
    problem.column = value.column
  }
  return problem
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}
