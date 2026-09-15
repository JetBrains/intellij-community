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

/**
 * Search schemas mirror the IDE's own `search_*` tools so the container-mode replacements
 * are drop-in from the agent's point of view. Outside container mode the IDE's tools are
 * passed through and these are unused.
 */
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

/**
 * Mirrors the IDE's `rename_refactoring` parameters. The schema sets `additionalProperties: false`,
 * so a parameter missing here is rejected before it reaches the IDE.
 */
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
      },
      contextSnippet: {
        type: 'string',
        description: 'Preferred way to point at the symbol when the name is not unique in the file. An exact fragment of the file that shows the symbol; it must match the file once and contain symbolName. Copy it from the file text you just read.'
      },
      line: {
        type: 'number',
        description: '1-based line of the symbol. Copy it from search_symbol output; never count lines. Needs column too.'
      },
      column: {
        type: 'number',
        description: '1-based column of the symbol. Copy it from search_symbol output. Needs line too.'
      },
      targetIndex: {
        type: 'number',
        description: '1-based pick from the candidates list a previous call returned.'
      },
      preview: {
        type: 'boolean',
        description: 'Analyze only: report affects and conflicts without writing anything.'
      }
    },
    ['pathInProject', 'symbolName', 'newName']
  )
}
