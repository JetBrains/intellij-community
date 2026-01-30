// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import type {SearchCapabilities, ToolInputSchema} from './types'

type JsonSchemaProperty = Record<string, unknown>

function objectSchema(properties: Record<string, JsonSchemaProperty>, required?: string[]): ToolInputSchema {
  return {
    type: 'object',
    properties,
    required: required && required.length > 0 ? required : undefined,
    additionalProperties: false
  }
}

function enumSchema(values: string[], description: string): JsonSchemaProperty {
  const unique = Array.from(new Set(values)).filter((value) => value)
  const schema: JsonSchemaProperty = {
    type: 'string',
    description
  }
  if (unique.length > 0) {
    schema.enum = unique
  }
  return schema
}

export function createReadSchema(includeIndentation: boolean): ToolInputSchema {
  const properties = {
    file_path: {
      type: 'string',
      description: 'Absolute or project-relative path to the file.'
    },
    offset: {
      type: 'number',
      description: 'The line number to start reading from. Must be 1 or greater.'
    },
    limit: {
      type: 'number',
      description: 'The maximum number of lines to return.'
    }
  }

  if (includeIndentation) {
    properties.mode = {
      type: 'string',
      description: 'Optional mode selector: "slice" for simple ranges (default) or "indentation" to expand around an anchor line.'
    }
    properties.indentation = objectSchema(
      {
        anchor_line: {
          type: 'number',
          description: 'Anchor line to center the indentation lookup on (defaults to offset).'
        },
        max_levels: {
          type: 'number',
          description: 'How many parent indentation levels (smaller indents) to include.'
        },
        include_siblings: {
          type: 'boolean',
          description: 'When true, include additional blocks that share the anchor indentation.'
        },
        include_header: {
          type: 'boolean',
          description: 'Include doc comments or attributes directly above the selected block.'
        },
        max_lines: {
          type: 'number',
          description: 'Hard cap on the number of lines returned when using indentation mode.'
        }
      },
      []
    )
  }

  return objectSchema(properties, ['file_path'])
}

export function createWriteSchema(): ToolInputSchema {
  return objectSchema(
    {
      file_path: {
        type: 'string',
        description: 'Absolute or project-relative path to the file.'
      },
      content: {
        type: 'string',
        description: 'The contents to write to the file.'
      }
    },
    ['file_path', 'content']
  )
}

export function createEditSchema(): ToolInputSchema {
  return objectSchema(
    {
      file_path: {
        type: 'string',
        description: 'Absolute or project-relative path to the file.'
      },
      old_string: {
        type: 'string',
        description: 'Text to replace.'
      },
      new_string: {
        type: 'string',
        description: 'Replacement text.'
      },
      replace_all: {
        type: 'boolean',
        description: 'When true, replace all occurrences. Otherwise replace only the first.'
      }
    },
    ['file_path', 'old_string', 'new_string']
  )
}


export function createListDirSchema(): ToolInputSchema {
  return objectSchema(
    {
      dir_path: {
        type: 'string',
        description: 'Absolute or project-relative path to the directory to list.'
      },
      offset: {
        type: 'number',
        description: 'The entry number to start listing from. Must be 1 or greater.'
      },
      limit: {
        type: 'number',
        description: 'The maximum number of entries to return.'
      },
      depth: {
        type: 'number',
        description: 'The maximum directory depth to traverse. Must be 1 or greater.'
      }
    },
    ['dir_path']
  )
}

export function createSearchSchema(capabilities: SearchCapabilities): ToolInputSchema {
  const targetValues = ['auto']
  if (capabilities.supportsSymbol) targetValues.push('symbol')
  if (capabilities.supportsFile) targetValues.push('file')
  if (capabilities.supportsText) targetValues.push('text')

  const queryTypes = ['text']
  if (capabilities.supportsRegex) queryTypes.push('regex')
  if (capabilities.supportsFileGlob) queryTypes.push('glob')

  const properties: Record<string, JsonSchemaProperty> = {
    query: {
      type: 'string',
      description: 'Search query text.'
    },
    target: enumSchema(
      targetValues,
      'Search target: "auto" (default), "symbol", "file", or "text".'
    ),
    path: {
      type: 'string',
      description: 'Optional base directory (absolute or project-relative).'
    },
    file_mask: {
      type: 'string',
      description: 'Optional filename mask (e.g. "*.kt") for text searches.'
    },
    case_sensitive: {
      type: 'boolean',
      description: 'Case-sensitive text search (default: true).'
    },
    max_results: {
      type: 'number',
      description: 'Maximum number of results to return.'
    },
    output: enumSchema(
      ['entries', 'files'],
      'Output mode: "entries" (default for text/symbol) or "files" (default for file searches).'
    )
  }

  if (queryTypes.length > 1) {
    properties.query_type = enumSchema(
      queryTypes,
      'Query type: "text" (default), "regex" (text searches), or "glob" (file searches).'
    )
  }

  return objectSchema(properties, ['query'])
}

export function createApplyPatchSchema(): ToolInputSchema {
  return objectSchema(
    {
      input: {
        type: 'string',
        description: 'Patch text in the apply_patch format, including Begin/End markers.'
      }
    },
    ['input']
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
