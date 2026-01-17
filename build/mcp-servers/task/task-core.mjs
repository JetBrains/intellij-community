// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {toolHandlers} from './task-handlers.mjs'

const tools = [
  {
    name: 'task_status',
    description: 'Get issue state or full details',
    inputSchema: {
      type: 'object',
      properties: {
        id: {type: 'string', description: 'Issue ID for full details'},
        view: {type: 'string', enum: ['summary', 'meta'], description: 'Issue view (default: summary)'},
        meta_max_chars: {type: 'integer', description: 'Max chars for description/design/acceptance in meta view (default: 400)'},
        memory_limit: {type: 'integer', description: 'Max entries per memory list in response (0 to omit memory)'}
      }
    }
  },
  {
    name: 'task_start',
    description: 'Start task workflow (status + optional epic creation)',
    inputSchema: {
      type: 'object',
      properties: {
        id: {type: 'string', description: 'Issue ID for full details'},
        user_request: {type: 'string', description: 'User task description'},
        view: {type: 'string', enum: ['summary', 'meta'], description: 'Issue view (default: summary)'},
        meta_max_chars: {type: 'integer', description: 'Max chars for description/design/acceptance in meta view (default: 400)'},
        memory_limit: {type: 'integer', description: 'Max entries per memory list in response (0 to omit memory)'}
      }
    }
  },
  {
    name: 'task_progress',
    description: 'Update findings/decisions/status',
    inputSchema: {
      type: 'object',
      properties: {
        id: {type: 'string', description: 'Issue ID'},
        findings: {type: 'array', items: {type: 'string'}, description: 'Discoveries'},
        decisions: {type: 'array', items: {type: 'string'}, description: 'Decisions made'},
        completed: {type: 'string', description: 'What was completed'},
        status: {type: 'string', enum: ['in_progress', 'blocked', 'deferred']},
        memory_limit: {type: 'integer', description: 'Max entries per memory list in response (0 to omit memory)'}
      },
      required: ['id']
    }
  },
  {
    name: 'task_decompose',
    description: 'Create sub-issues under epic',
    inputSchema: {
      type: 'object',
      properties: {
        epic_id: {type: 'string', description: 'Parent epic ID'},
        sub_issues: {
          type: 'array',
          items: {
            type: 'object',
            properties: {
              title: {type: 'string'},
              description: {type: 'string'},
              acceptance: {type: 'string'},
              design: {type: 'string'},
              type: {type: 'string'},
              depends_on: {type: 'array', items: {type: 'integer'}}
            },
            required: ['title', 'description', 'acceptance', 'design']
          }
        },
        start_child_index: {type: 'integer', description: 'Index in sub_issues to set in_progress after creation'},
        update_epic_acceptance: {type: 'string'}
      },
      required: ['epic_id', 'sub_issues']
    }
  },
  {
    name: 'task_create',
    description: 'Create non-epic issue',
    inputSchema: {
      type: 'object',
      properties: {
        title: {type: 'string'},
        description: {type: 'string'},
        type: {type: 'string', default: 'task'},
        parent: {type: 'string'},
        acceptance: {type: 'string'},
        design: {type: 'string'},
        priority: {type: 'string'},
        depends_on: {type: 'string'},
        dep_type: {type: 'string'}
      },
      required: ['title']
    }
  },
  {
    name: 'task_done',
    description: 'Close issue',
    inputSchema: {
      type: 'object',
      properties: {
        id: {type: 'string'},
        summary: {type: 'string'},
        findings: {type: 'array', items: {type: 'string'}},
        decisions: {type: 'array', items: {type: 'string'}},
        confirmed: {type: 'boolean', description: 'Confirm closure after review prompt'}
      },
      required: ['id']
    }
  },
  {
    name: 'task_reopen',
    description: 'Reopen closed issue',
    inputSchema: {
      type: 'object',
      properties: {
        id: {type: 'string', description: 'Issue ID'},
        reason: {type: 'string', description: 'Reason for reopening'},
        view: {type: 'string', enum: ['summary', 'meta'], description: 'Issue view (default: summary)'},
        meta_max_chars: {type: 'integer', description: 'Max chars for description/design/acceptance in meta view (default: 400)'},
        memory_limit: {type: 'integer', description: 'Max entries per memory list in response (0 to omit memory)'}
      },
      required: ['id', 'reason']
    }
  }
]

export {tools, toolHandlers}
