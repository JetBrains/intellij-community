// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

#!/usr/bin/env node

import {createMcpServer} from './mcp-rpc.mjs'
import {bd, bdJson, escape} from './bd-client.mjs'

const tools = [
  {
    name: 'task_create_epic',
    description: 'Create a new epic for planning and tracking a multi-step task. Automatically sets status to in_progress. Use for new user requests that need exploration and decomposition.',
    inputSchema: {
      type: 'object',
      properties: {
        title: { type: 'string', description: 'Short summary of the epic (50 chars max)' },
        description: { type: 'string', description: 'WHAT to build and WHY - the user request and business context' },
        acceptance: { type: 'string', description: "Testable success criteria. Use 'PENDING' if unknown yet." },
        design: { type: 'string', description: "Technical approach (HOW). Use 'PENDING' if unknown yet." }
      },
      required: ['title', 'description']
    }
  },
  {
    name: 'task_update_progress',
    description: 'Update progress notes on an issue. Formats as COMPLETED/IN PROGRESS/NEXT sections. Call frequently to track work state.',
    inputSchema: {
      type: 'object',
      properties: {
        id: { type: 'string', description: 'Issue ID to update' },
        completed: {
          type: 'array',
          items: { type: 'string' },
          description: 'List of completed items to add to COMPLETED section'
        },
        in_progress: { type: 'string', description: 'Current work for IN PROGRESS section' },
        next: { type: 'string', description: 'Next action for NEXT section' },
        key_decision: { type: 'string', description: 'Important decision made (optional)' }
      },
      required: ['id']
    }
  },
  {
    name: 'task_decompose',
    description: 'Decompose an epic into sub-issues. Creates all sub-issues in one call with proper dependencies. Use after exploration when ready to break down work.',
    inputSchema: {
      type: 'object',
      properties: {
        epic_id: { type: 'string', description: 'Parent epic ID' },
        sub_issues: {
          type: 'array',
          items: {
            type: 'object',
            properties: {
              title: { type: 'string', description: "Sub-issue title (e.g., 'Step 1: Create API endpoint')" },
              description: { type: 'string', description: 'WHAT and WHY for this sub-issue' },
              acceptance: { type: 'string', description: 'Testable outcomes for this sub-issue' },
              design: { type: 'string', description: 'Technical approach (HOW)' },
              depends_on: {
                type: 'array',
                items: { type: 'integer' },
                description: 'Indices (0-based) of sub-issues this depends on'
              }
            },
            required: ['title', 'description', 'acceptance', 'design']
          },
          description: 'Array of sub-issues to create'
        },
        update_epic_acceptance: {
          type: 'string',
          description: "Update epic's acceptance criteria (now that we know the full scope)"
        }
      },
      required: ['epic_id', 'sub_issues']
    }
  },
  {
    name: 'task_list',
    description: "List issues filtered by status. Use status='in_progress' to find current work, status='ready' for available tasks.",
    inputSchema: {
      type: 'object',
      properties: {
        status: {
          type: 'string',
          enum: ['in_progress', 'ready', 'all'],
          description: 'Filter by status'
        },
        parent: { type: 'string', description: 'Filter by parent epic ID' }
      }
    }
  },
  {
    name: 'task_ready',
    description: 'Get issues ready to be worked on. Optionally filter by parent epic. Use to pick next task.',
    inputSchema: {
      type: 'object',
      properties: {
        parent: { type: 'string', description: 'Filter by parent epic ID' },
        limit: { type: 'integer', description: 'Max results to return (default 10)' }
      }
    }
  },
  {
    name: 'task_show',
    description: 'Get complete issue details including description, acceptance criteria, design, and notes. Use to understand task requirements before working.',
    inputSchema: {
      type: 'object',
      properties: {
        id: { type: 'string', description: 'Issue ID to retrieve' }
      },
      required: ['id']
    }
  },
  {
    name: 'task_close',
    description: 'Close an issue. Provide reason summarizing outcome. Use after completing work or when cancelling.',
    inputSchema: {
      type: 'object',
      properties: {
        id: { type: 'string', description: 'Issue ID to close' },
        reason: { type: 'string', description: 'Summary of what was done or why cancelled' }
      },
      required: ['id', 'reason']
    }
  },
  {
    name: 'task_add_comment',
    description: "Add comment to an issue. Use for scope changes, blockers, or important context that doesn't fit in notes.",
    inputSchema: {
      type: 'object',
      properties: {
        id: { type: 'string', description: 'Issue ID to comment on' },
        message: { type: 'string', description: 'Comment text' }
      },
      required: ['id', 'message']
    }
  }
]

const toolHandlers = {
  task_create_epic: (args) => {
    const acceptance = args.acceptance || 'PENDING: Define after exploration'
    const design = args.design || 'PENDING: Define after exploration'
    const id = bd(`create --title="${escape(args.title)}" --type=epic --description="${escape(args.description)}" --acceptance="${escape(acceptance)}" --design="${escape(design)}" --silent`)
    bd(`update ${id} --status=in_progress`)
    return { id, title: args.title }
  },

  task_update_progress: (args) => {
    const parts = []
    if (args.completed?.length) {
      parts.push(`COMPLETED: ${args.completed.join(', ')}`)
    }
    if (args.in_progress) {
      parts.push(`IN PROGRESS: ${args.in_progress}`)
    }
    if (args.next) {
      parts.push(`NEXT: ${args.next}`)
    }
    if (args.key_decision) {
      parts.push(`KEY DECISION: ${args.key_decision}`)
    }
    const notes = parts.join('\n')
    bd(`update ${args.id} --notes="${escape(notes)}"`)
    return { success: true }
  },

  task_decompose: (args) => {
    const ids = []
    for (const sub of args.sub_issues) {
      const id = bd(`create --title="${escape(sub.title)}" --parent=${args.epic_id} --type=task --description="${escape(sub.description)}" --acceptance="${escape(sub.acceptance)}" --design="${escape(sub.design)}" --silent`)
      ids.push(id)
    }
    // Add dependencies
    args.sub_issues.forEach((sub, i) => {
      if (sub.depends_on) {
        sub.depends_on.forEach(depIdx => {
          bd(`dep add ${ids[i]} ${ids[depIdx]}`)
        })
      }
    })
    // Update epic acceptance if provided
    if (args.update_epic_acceptance) {
      bd(`update ${args.epic_id} --acceptance="${escape(args.update_epic_acceptance)}"`)
    }
    return { ids, epic_id: args.epic_id }
  },

  task_list: (args) => {
    const statusArg = args.status ? `--status=${args.status}` : ''
    const parentArg = args.parent ? `--parent=${args.parent}` : ''
    return bdJson(`list ${statusArg} ${parentArg}`.trim())
  },

  task_ready: (args) => {
    const parentArg = args.parent ? `--parent=${args.parent}` : ''
    const limitArg = args.limit ? `--limit=${args.limit}` : ''
    return bdJson(`ready ${parentArg} ${limitArg}`.trim())
  },

  task_show: (args) => {
    return bdJson(`show ${args.id}`)
  },

  task_close: (args) => {
    bd(`close ${args.id} --reason="${escape(args.reason)}"`)
    return { success: true }
  },

  task_add_comment: (args) => {
    bd(`comments add ${args.id} "${escape(args.message)}"`)
    return { success: true }
  }
}

createMcpServer({
  serverInfo: {
    name: 'task',
    version: '1.0.0'
  },
  tools,
  toolHandlers
})
