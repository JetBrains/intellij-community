// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

function objectSchema(properties, required) {
  return {
    type: 'object',
    properties,
    required: required && required.length > 0 ? required : undefined,
    additionalProperties: false
  }
}

export function createReadSchema(includeIndentation) {
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

export function createWriteSchema() {
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

export function createEditSchema() {
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

export function createGlobSchema() {
  return objectSchema(
    {
      pattern: {
        type: 'string',
        description: 'Glob pattern to match.'
      },
      path: {
        type: 'string',
        description: 'Optional base directory (absolute or project-relative).'
      }
    },
    ['pattern']
  )
}

export function createGrepSchema() {
  return objectSchema(
    {
      pattern: {
        type: 'string',
        description: 'Regular expression to search for.'
      },
      path: {
        type: 'string',
        description: 'Optional base directory (absolute or project-relative).'
      },
      glob: {
        type: 'string',
        description: 'Optional glob filter for matched files.'
      },
      type: {
        type: 'string',
        description: 'Optional file extension filter (for example, "ts" for TypeScript files).'
      },
      output_mode: {
        type: 'string',
        description: 'Output mode: "files_with_matches", "content", or "count".'
      },
      '-i': {
        type: 'boolean',
        description: 'Case-insensitive search.'
      },
      '-n': {
        type: 'boolean',
        description: 'Include line numbers in output when in content mode.'
      },
      '-A': {
        type: 'number',
        description: 'Lines of context after each match (not currently supported).'
      },
      '-B': {
        type: 'number',
        description: 'Lines of context before each match (not currently supported).'
      },
      '-C': {
        type: 'number',
        description: 'Lines of context around each match (not currently supported).'
      },
      head_limit: {
        type: 'number',
        description: 'Maximum number of results to return.'
      },
      multiline: {
        type: 'boolean',
        description: 'Whether to search across line boundaries (not currently supported).'
      }
    },
    ['pattern']
  )
}

export function createGrepSchemaCodex() {
  return objectSchema(
    {
      pattern: {
        type: 'string',
        description: 'Regular expression pattern to search for.'
      },
      path: {
        type: 'string',
        description: 'Directory or file path to search. Defaults to the session working directory.'
      },
      include: {
        type: 'string',
        description: 'Optional glob that limits which files are searched.'
      },
      glob: {
        type: 'string',
        description: 'Optional glob filter for matched files.'
      },
      type: {
        type: 'string',
        description: 'Optional file extension filter (for example, "ts" for TypeScript files).'
      },
      output_mode: {
        type: 'string',
        description: 'Output mode: "files_with_matches", "content", or "count".'
      },
      '-i': {
        type: 'boolean',
        description: 'Case-insensitive search.'
      },
      '-n': {
        type: 'boolean',
        description: 'Include line numbers in output when in content mode.'
      },
      limit: {
        type: 'number',
        description: 'Maximum number of results to return.'
      }
    },
    ['pattern']
  )
}

export function createListDirSchema() {
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

export function createFindSchema() {
  return objectSchema(
    {
      pattern: {
        type: 'string',
        description: 'Filename substring or glob pattern to search for.'
      },
      path: {
        type: 'string',
        description: 'Optional base directory (absolute or project-relative).'
      },
      limit: {
        type: 'number',
        description: 'Maximum number of file paths to return.'
      },
      mode: {
        type: 'string',
        description: 'Optional mode: "auto" (default), "glob", or "name".'
      },
      add_excluded: {
        type: 'boolean',
        description: 'Whether to include excluded/ignored files when using glob mode.'
      }
    },
    ['pattern']
  )
}

export function createApplyPatchSchema() {
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

export function createRenameSchema() {
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
