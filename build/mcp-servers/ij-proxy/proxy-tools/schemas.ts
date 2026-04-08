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

export function createReadSchema(includeIndentation: boolean): ToolInputSchema {
  const properties: Record<string, JsonSchemaProperty> = {
    file_path: {
      type: 'string',
      description: 'Path relative to the project root.'
    },
    mode: {
      type: 'string',
      description: "Read mode: 'slice', 'lines', 'line_columns', 'offsets', or 'indentation'."
    },
    start_line: {
      type: 'number',
      description: '1-based line number to start reading from.'
    },
    max_lines: {
      type: 'number',
      description: 'Maximum number of lines to return (slice uses as line count; all modes cap output).'
    },
    end_line: {
      type: 'number',
      description: '1-based end line for lines/line_columns mode (inclusive for lines; exclusive for line_columns).'
    },
    start_column: {
      type: 'number',
      description: '1-based start column for line_columns mode.'
    },
    end_column: {
      type: 'number',
      description: '1-based end column for range read (exclusive).'
    },
    start_offset: {
      type: 'number',
      description: '0-based start offset for offsets mode (requires end_offset).'
    },
    end_offset: {
      type: 'number',
      description: '0-based end offset for offsets mode (exclusive).'
    },
    context_lines: {
      type: 'number',
      description: 'Number of context lines to include around the range (per side).'
    }
  }

  if (includeIndentation) {
    properties.max_levels = {
      type: 'number',
      description: 'Indentation mode: maximum indentation levels to include (0 = only anchor block).'
    }
    properties.include_siblings = {
      type: 'boolean',
      description: 'Indentation mode: include sibling blocks at the same indentation level.'
    }
    properties.include_header = {
      type: 'boolean',
      description: 'Indentation mode: include header comments/annotations directly above anchor.'
    }
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
      file_paths: {
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
    ['file_paths']
  )
}

export function createApplyPatchSchema(): ToolInputSchema {
  return objectSchema(
    {
      input: {
        type: 'string',
        description: 'Patch text in the apply_patch format or unified git diff format.'
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
