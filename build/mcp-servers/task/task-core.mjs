// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {toolHandlers} from './task-handlers.mjs'

const tools = [
  {
    name: 'task_status',
    description: 'Get issue state or full details',
    inputSchema: {
      type: 'object',
      additionalProperties: false,
      properties: {
        id: {type: 'string', description: 'Issue ID for full details'},
        user_request: {type: 'string', description: 'Not supported for task_status; use task_start'},
        view: {type: 'string', enum: ['summary', 'meta'], default: 'summary', description: 'Issue view (default: summary)'},
        meta_max_chars: {type: 'integer', default: 400, description: 'Max chars for description/design/acceptance in meta view (default: 400)'},
        memory_limit: {type: 'integer', default: 0, description: 'Max entries per memory list in response (0 to omit memory)'}
      }
    }
  },
  {
    name: 'task_start',
    description: 'Start task workflow (status + optional epic creation)',
    inputSchema: {
      type: 'object',
      additionalProperties: false,
      properties: {
        id: {type: 'string', description: 'Issue ID for full details'},
        user_request: {type: 'string', description: 'User task description'},
        description: {type: 'string'},
        design: {type: 'string'},
        acceptance: {type: 'string'},
        view: {type: 'string', enum: ['summary', 'meta'], default: 'summary', description: 'Issue view (default: summary)'},
        meta_max_chars: {type: 'integer', default: 400, description: 'Max chars for description/design/acceptance in meta view (default: 400)'},
        memory_limit: {type: 'integer', default: 0, description: 'Max entries per memory list in response (0 to omit memory)'}
      }
    }
  },
  {
    name: 'task_progress',
    description: 'Update findings/decisions/status',
    inputSchema: {
      type: 'object',
      additionalProperties: false,
      properties: {
        id: {type: 'string', description: 'Issue ID'},
        findings: {type: 'array', items: {type: 'string'}, description: 'Discoveries'},
        decisions: {type: 'array', items: {type: 'string'}, description: 'Decisions made'},
        status: {type: 'string', enum: ['in_progress', 'blocked', 'deferred']},
        memory_limit: {type: 'integer', default: 0, description: 'Max entries per memory list in response (0 to omit memory)'}
      },
      required: ['id']
    }
  },
  {
    name: 'task_update_meta',
    description: 'Update description/design/acceptance',
    inputSchema: {
      type: 'object',
      additionalProperties: false,
      properties: {
        id: {type: 'string', description: 'Issue ID'},
        description: {type: 'string'},
        design: {type: 'string'},
        acceptance: {type: 'string'},
        view: {type: 'string', enum: ['summary', 'meta'], default: 'summary', description: 'Issue view (default: summary)'},
        meta_max_chars: {type: 'integer', default: 400, description: 'Max chars for description/design/acceptance in meta view (default: 400)'},
        memory_limit: {type: 'integer', default: 0, description: 'Max entries per memory list in response (0 to omit memory)'}
      },
      required: ['id']
    }
  },
  {
    name: 'task_decompose',
    description: 'Create sub-issues under epic (auto-starts single child)',
    inputSchema: {
      type: 'object',
      additionalProperties: false,
      properties: {
        epic_id: {type: 'string', description: 'Parent epic ID'},
        sub_issues: {
          type: 'array',
          items: {
            type: 'object',
            additionalProperties: false,
            properties: {
              title: {type: 'string'},
              description: {type: 'string'},
              acceptance: {type: 'string'},
              design: {type: 'string'},
              type: {type: 'string'},
              depends_on: {
                type: 'array',
                items: {
                  anyOf: [
                    {type: 'integer'},
                    {type: 'string'}
                  ]
                }
              },
              dep_type: {type: 'string'}
            },
            required: ['title', 'description', 'acceptance', 'design']
          }
        },
        update_epic_acceptance: {type: 'string'}
      },
      required: ['epic_id', 'sub_issues']
    }
  },
  {
    name: 'task_create',
    description: 'Create issue',
    inputSchema: {
      type: 'object',
      additionalProperties: false,
      properties: {
        title: {type: 'string'},
        description: {type: 'string'},
        type: {type: 'string', default: 'task'},
        parent: {type: 'string'},
        acceptance: {type: 'string'},
        design: {type: 'string'},
        priority: {type: 'string'},
        depends_on: {
          anyOf: [
            {type: 'string'},
            {type: 'array', items: {type: 'string'}}
          ]
        },
        dep_type: {type: 'string'}
      },
      required: ['title', 'description', 'design', 'acceptance']
    }
  },
  {
    name: 'task_link',
    description: 'Add dependencies between existing issues',
    inputSchema: {
      type: 'object',
      additionalProperties: false,
      properties: {
        id: {type: 'string'},
        depends_on: {
          anyOf: [
            {type: 'string'},
            {type: 'array', items: {type: 'string'}}
          ]
        },
        dep_type: {type: 'string'}
      },
      required: ['id', 'depends_on']
    }
  },
  {
    name: 'task_done',
    description: 'Close issue',
    inputSchema: {
      type: 'object',
      additionalProperties: false,
      properties: {
        id: {type: 'string'},
        reason: {type: 'string'},
        findings: {type: 'array', items: {type: 'string'}},
        decisions: {type: 'array', items: {type: 'string'}}
      },
      required: ['id', 'reason']
    }
  },
  {
    name: 'task_reopen',
    description: 'Reopen closed issue',
    inputSchema: {
      type: 'object',
      additionalProperties: false,
      properties: {
        id: {type: 'string', description: 'Issue ID'},
        reason: {type: 'string', description: 'Reason for reopening'},
        view: {type: 'string', enum: ['summary', 'meta'], default: 'summary', description: 'Issue view (default: summary)'},
        meta_max_chars: {type: 'integer', default: 400, description: 'Max chars for description/design/acceptance in meta view (default: 400)'},
        memory_limit: {type: 'integer', default: 0, description: 'Max entries per memory list in response (0 to omit memory)'}
      },
      required: ['id', 'reason']
    }
  }
]

export {tools, toolHandlers}
