// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import type {ToolInputSchema} from './types'

type JsonSchemaProperty = Record<string, unknown>

function objectSchema(properties: Record<string, JsonSchemaProperty>, required?: string[]): ToolInputSchema {
  return {
    type: 'object',
    properties,
    required: required && required.length > 0 ? required : undefined,
    additionalProperties: false
  }
}

function createSearchSchema(qDescription: string): ToolInputSchema {
  return objectSchema(
    {
      q: {
        type: 'string',
        description: qDescription
      },
      paths: {
        type: 'array',
        description: 'Optional list of project-relative glob patterns (supports ! excludes).',
        items: {
          type: 'string'
        }
      },
      limit: {
        type: 'number',
        description: 'Maximum number of results to return.'
      }
    },
    ['q']
  )
}

export function createSearchTextSchema(): ToolInputSchema {
  return createSearchSchema('Text substring to search for.')
}

export function createSearchRegexSchema(): ToolInputSchema {
  return createSearchSchema('Regular expression pattern to search for.')
}

export function createSearchFileSchema(): ToolInputSchema {
  const base = createSearchSchema('Glob pattern to match file paths.')
  return objectSchema(
    {
      ...base.properties,
      includeExcluded: {
        type: 'boolean',
        description: 'Whether to include excluded/ignored files in results.'
      }
    },
    base.required
  )
}

export function createSearchSymbolSchema(): ToolInputSchema {
  return createSearchSchema('Symbol query text (class, method, field, etc.).')
}

export function createLintFilesSchema(): ToolInputSchema {
  return objectSchema(
    {
      files: {
        type: 'array',
        description: 'List of project-relative file paths to analyze. Duplicate paths are ignored after normalization.',
        items: {
          type: 'string'
        }
      },
      min_severity: {
        type: 'string',
        description: 'Minimum severity to include: warning or error. Defaults to warning.'
      },
      timeout: {
        type: 'number',
        description: 'Timeout in milliseconds for the full batch.'
      }
    },
    ['files']
  )
}

export function createReformatFileSchema(): ToolInputSchema {
  return objectSchema(
    {
      files: {
        type: 'array',
        description: 'List of project-relative file paths to reformat. Duplicate paths are ignored after normalization.',
        items: {
          type: 'string'
        }
      }
    },
    ['files']
  )
}

export function createRenameSchema(): ToolInputSchema {
  return objectSchema(
    {
      pathInProject: {
        type: 'string',
        description: 'Absolute or project-relative path to the file containing the symbol (for example, src/app.ts).'
      },
      symbolName: {
        type: 'string',
        description: 'Exact, case-sensitive name of the symbol to rename.'
      },
      newName: {
        type: 'string',
        description: 'New, case-sensitive name for the symbol.'
      }
    },
    ['pathInProject', 'symbolName', 'newName']
  )
}
