#!/usr/bin/env node
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {createMcpServer} from './mcp-rpc.mjs'
import {bd, bdJson} from './bd-client.mjs'

// 5 smart tools: task_status, task_epic, task_progress, task_decompose, task_done
const tools = [
  {
    name: 'task_status',
    description: 'Get current task state. No args = overview (in-progress + ready). With id = full issue details.',
    inputSchema: {
      type: 'object',
      properties: {
        id: {type: 'string', description: 'Optional: specific issue ID for full details'}
      }
    }
  },
  {
    name: 'task_epic',
    description: 'Create new epic or resume existing. Returns epic details.',
    inputSchema: {
      type: 'object',
      properties: {
        title: {type: 'string', description: 'Epic title (required for new)'},
        description: {type: 'string', description: 'WHAT and WHY (required for new)'},
        resume: {type: 'string', description: 'Resume existing epic by ID instead of creating'}
      }
    }
  },
  {
    name: 'task_progress',
    description: 'Update progress notes. Auto-appends to existing notes.',
    inputSchema: {
      type: 'object',
      properties: {
        id: {type: 'string', description: 'Issue ID to update'},
        completed: {type: 'string', description: 'What was completed (appends to COMPLETED)'},
        working_on: {type: 'string', description: 'Current work (replaces IN PROGRESS)'},
        next: {type: 'string', description: 'Next action (replaces NEXT)'},
        decision: {type: 'string', description: 'Key decision made (appends to KEY DECISIONS)'}
      },
      required: ['id']
    }
  },
  {
    name: 'task_decompose',
    description: 'Decompose epic into sub-issues. Creates all sub-issues with dependencies in one call.',
    inputSchema: {
      type: 'object',
      properties: {
        epic_id: {type: 'string', description: 'Parent epic ID'},
        sub_issues: {
          type: 'array',
          items: {
            type: 'object',
            properties: {
              title: {type: 'string', description: 'Sub-issue title'},
              description: {type: 'string', description: 'WHAT and WHY'},
              acceptance: {type: 'string', description: 'Testable outcomes'},
              design: {type: 'string', description: 'Technical approach'},
              depends_on: {
                type: 'array',
                items: {type: 'integer'},
                description: 'Indices (0-based) of sub-issues this depends on'
              }
            },
            required: ['title', 'description', 'acceptance', 'design']
          }
        },
        update_epic_acceptance: {type: 'string', description: 'Update epic acceptance criteria'}
      },
      required: ['epic_id', 'sub_issues']
    }
  },
  {
    name: 'task_done',
    description: 'Complete issue and get next ready. Auto-updates epic progress.',
    inputSchema: {
      type: 'object',
      properties: {
        id: {type: 'string', description: 'Issue to close'},
        summary: {type: 'string', description: 'What was accomplished'}
      },
      required: ['id', 'summary']
    }
  }
]

const toolHandlers = {
  task_status: (args) => {
    if (args.id) {
      // Full details for specific issue
      return bdJson(['show', args.id])
    }

    // Overview: in-progress + ready
    const inProgressList = bdJson(['list', '--status', 'in_progress'])
    const readyList = bdJson(['ready', '--limit', '10'])

    let suggestion
    if (inProgressList.issues?.length > 0) {
      const current = inProgressList.issues[0]
      suggestion = `Continue ${current.id}: ${current.title}`
    }
    else if (readyList.issues?.length > 0) {
      suggestion = 'Pick from ready list'
    }
    else {
      suggestion = 'Create epic'
    }

    return {
      in_progress: inProgressList.issues?.[0] || null,
      ready: readyList.issues || [],
      suggestion
    }
  },

  task_epic: (args) => {
    if (args.resume) {
      // Resume existing issue (any type - epic, task, etc. are all valid)
      const issue = bdJson(['show', args.resume])
      bd(['update', args.resume, '--status', 'in_progress'])
      return {id: args.resume, title: issue.title, type: issue.type, is_new: false}
    }

    // Create new epic
    if (!args.title || !args.description) {
      throw new Error('title and description required for new epic')
    }
    const id = bd(['create', '--title', args.title, '--type', 'epic', '--description', args.description, '--acceptance', 'PENDING', '--design', 'PENDING', '--silent'])
    bd(['update', id, '--status', 'in_progress'])
    return {id, title: args.title, is_new: true}
  },

  task_progress: (args) => {
    // Get existing notes
    const issue = bdJson(['show', args.id])
    const existingNotes = issue.notes || ''

    // Parse existing sections
    const sections = {
      completed: [],
      in_progress: '',
      next: '',
      decisions: []
    }

    for (const line of existingNotes.split('\n')) {
      if (line.startsWith('COMPLETED:')) {
        sections.completed.push(line.replace('COMPLETED:', '').trim())
      }
      else if (line.startsWith('IN PROGRESS:')) {
        sections.in_progress = line.replace('IN PROGRESS:', '').trim()
      }
      else if (line.startsWith('NEXT:')) {
        sections.next = line.replace('NEXT:', '').trim()
      }
      else if (line.startsWith('KEY DECISION:')) {
        sections.decisions.push(line.replace('KEY DECISION:', '').trim())
      }
    }

    // Apply updates (append for completed/decisions, replace for in_progress/next)
    if (args.completed) {
      sections.completed.push(args.completed)
    }
    if (args.working_on !== undefined) {
      sections.in_progress = args.working_on
    }
    if (args.next !== undefined) {
      sections.next = args.next
    }
    if (args.decision) {
      sections.decisions.push(args.decision)
    }

    // Build new notes
    const parts = []
    for (const item of sections.completed) {
      parts.push(`COMPLETED: ${item}`)
    }
    if (sections.in_progress) {
      parts.push(`IN PROGRESS: ${sections.in_progress}`)
    }
    if (sections.next) {
      parts.push(`NEXT: ${sections.next}`)
    }
    for (const item of sections.decisions) {
      parts.push(`KEY DECISION: ${item}`)
    }

    const newNotes = parts.join('\n')
    bd(['update', args.id, '--notes', newNotes])
    return {success: true, notes: newNotes}
  },

  task_decompose: (args) => {
    // Validate depends_on indices before creating anything
    args.sub_issues.forEach((sub, i) => {
      if (sub.depends_on) {
        for (const depIdx of sub.depends_on) {
          if (depIdx < 0 || depIdx >= i) {
            throw new Error(`Invalid depends_on[${depIdx}] in sub_issue[${i}]: must reference earlier sub-issue (0 to ${i - 1})`)
          }
        }
      }
    })

    const ids = []
    for (const sub of args.sub_issues) {
      const id = bd(['create', '--title', sub.title, '--parent', args.epic_id, '--type', 'task', '--description', sub.description, '--acceptance', sub.acceptance, '--design', sub.design, '--silent'])
      ids.push(id)
    }

    // Add dependencies
    args.sub_issues.forEach((sub, i) => {
      if (sub.depends_on) {
        sub.depends_on.forEach(depIdx => {
          bd(['dep', 'add', ids[i], ids[depIdx]])
        })
      }
    })

    // Update epic acceptance if provided
    if (args.update_epic_acceptance) {
      bd(['update', args.epic_id, '--acceptance', args.update_epic_acceptance])
    }

    return {ids, epic_id: args.epic_id}
  },

  task_done: (args) => {
    // Close the issue
    bd(['close', args.id, '--reason', args.summary])

    // Get issue info to find parent
    let epicStatus = null
    let nextReady = null
    let error = null

    try {
      const issue = bdJson(['show', args.id])
      if (issue.parent) {
        // Get sibling ready issues
        const readyList = bdJson(['ready', '--parent', issue.parent])
        nextReady = readyList.issues?.[0] || null

        // Get epic status
        const epicIssue = bdJson(['show', issue.parent])
        const children = epicIssue.children || []
        const completed = children.filter(c => c.status === 'closed').length + 1 // +1 for just closed
        epicStatus = {
          completed,
          remaining: children.length - completed
        }
      }
    }
    catch (e) {
      error = `Failed to get next/status: ${e.message}`
    }

    const result = {
      closed: {id: args.id},
      next_ready: nextReady,
      epic_status: epicStatus
    }
    if (error) {
      result.error = error
    }
    
    // Add actionable suggestion for next steps
    if (epicStatus?.remaining === 0 && epicStatus?.completed > 0) {
      try {
        const issue = bdJson(['show', args.id])
        if (issue.parent) {
          result.suggestion = `All sub-issues complete! Call task_done(id="${issue.parent}", summary="...") to close epic`
        }
      } catch (e) {
        // Ignore - we already have the closed issue info
      }
    } else if (nextReady) {
      result.suggestion = `Continue with ${nextReady.id}: ${nextReady.title}`
    }
    
    return result
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
