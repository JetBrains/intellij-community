#!/usr/bin/env node
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {createMcpServer} from './mcp-rpc.mjs'
import {bd, bdJson, bdShowOne} from './bd-client.mjs'

// Parse notes into structured sections
// Tries JSON format first, falls back to legacy text format for backward compatibility
function parseNotes(notes) {
  if (!notes) return {findings: [], decisions: []}

  // Try JSON first
  if (notes.trim().startsWith('{')) {
    try {
      const parsed = JSON.parse(notes)
      const result = {
        findings: parsed.findings || [],
        decisions: parsed.decisions || []
      }
      // Preserve pending_close if present
      if (parsed.pending_close) {
        result.pending_close = parsed.pending_close
      }
      return result
    } catch (e) {
      // Fall through to text parsing
    }
  }

  // Legacy text format (backward compat)
  const sections = {findings: [], decisions: []}
  for (const line of notes.split('\n')) {
    if (line.startsWith('FINDING:')) {
      sections.findings.push(line.replace('FINDING:', '').trim())
    } else if (line.startsWith('KEY DECISION:')) {
      sections.decisions.push(line.replace('KEY DECISION:', '').trim())
    }
  }
  return sections
}

// Build notes string from sections
// Stores as pretty-printed JSON for human readability
function buildNotes(sections) {
  return JSON.stringify(sections, null, 2)
}

// Fetch ready children for an epic (used in task_status and task_epic resume)
function getReadyChildren(epicId) {
  const readyChildren = bdJson(['ready', '--parent', epicId])
  if (readyChildren.length > 0) {
    return readyChildren.map(c => ({id: c.id, title: c.title}))
  }
  return null
}

// Check if issue requires user review before closing (based on priority/type)
function needsReview(issue) {
  const priority = typeof issue.priority === 'string'
    ? parseInt(issue.priority.replace('P', ''), 10)
    : (issue.priority ?? 2)
  // P0/P1 (critical/high), bugs, features, and epics always need review
  return priority <= 1 || issue.issue_type === 'bug' || issue.issue_type === 'feature' || issue.issue_type === 'epic'
}

const tools = [
  {
    name: 'task_status',
    description: 'Get issue state or full details',
    inputSchema: {
      type: 'object',
      properties: {
        id: {type: 'string', description: 'Issue ID for full details'},
        user_request: {type: 'string', description: 'User task description'}
      }
    }
  },
  {
    name: 'task_epic',
    description: 'Create or resume epic',
    inputSchema: {
      type: 'object',
      properties: {
        title: {type: 'string', description: 'Epic title'},
        description: {type: 'string', description: 'WHAT and WHY'},
        resume: {type: 'string', description: 'Resume by ID'}
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
        status: {type: 'string', enum: ['in_progress', 'blocked', 'deferred']}
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
  }
]

const toolHandlers = {
  task_status: (args) => {
    // Specific issue query - return full details (replaces task_show)
    if (args.id) {
      const issue = bdShowOne(args.id)
      if (!issue) {
        return {error: `Issue ${args.id} not found`}
      }
      // Parse notes if present
      if (issue.notes) {
        issue.notes = parseNotes(issue.notes)
      }
      return issue
    }

    // Overview query - selection-oriented responses
    const inProgress = bdJson(['list', '--status', 'in_progress'])
    const ready = bdJson(['ready', '--limit', '5'])

    // No in-progress issues
    if (inProgress.length === 0) {
      if (args.user_request) {
        return {user_request: args.user_request}
      }
      if (ready.length > 0) {
        return {
          askUser: {
            question: 'Which task would you like to work on?',
            header: 'Task',
            options: ready.map(r => ({label: r.title, description: r.id})),
            multiSelect: false
          }
        }
      }
      return {empty: true}
    }

    // Single in-progress, no new request - just continue
    if (inProgress.length === 1 && !args.user_request) {
      const issue = inProgress[0]
      const result = {id: issue.id, title: issue.title, status: issue.status, type: issue.issue_type}

      // For epics, include ready children so Claude knows what to work on
      if (issue.issue_type === 'epic') {
        const readyChildren = getReadyChildren(issue.id)
        if (readyChildren) {
          result.ready_children = readyChildren
        }
      }
      return result
    }

    // Multiple in-progress OR conflict with user_request - user must select
    const questionText = args.user_request
      ? 'New request - which task?'
      : 'Which task to work on?'

    // Find parent epic - only if unambiguous (single epic context)
    const parentIds = [...new Set(inProgress.map(i => i.parent).filter(Boolean))]
    const epicIds = inProgress.filter(i => i.issue_type === 'epic').map(i => i.id)
    const parentEpic = parentIds.length === 1 ? parentIds[0]
      : (parentIds.length === 0 && epicIds.length === 1 ? epicIds[0] : null)
    return {
      askUser: {
        question: questionText,
        header: 'Task',
        options: [
          ...inProgress.map(i => ({label: i.title, description: i.id})),
          ...(parentEpic ? [{label: 'Create sub-task', description: `Under ${parentEpic}`}] : []),
          {label: 'Start new task', description: 'Create a new epic'}
        ],
        multiSelect: false
      }
    }
  },

  task_epic: (args) => {
    if (args.resume) {
      const issue = bdShowOne(args.resume)
      if (!issue) {
        return {error: `Issue ${args.resume} not found`}
      }
      bd(['update', args.resume, '--status', 'in_progress'])

      // Return full issue details (same as task_status(id)) to avoid redundant follow-up call
      if (issue.notes) {
        issue.notes = parseNotes(issue.notes)
      }
      issue.is_new = false

      // For epics, include ready children so Claude knows what to work on
      if (issue.issue_type === 'epic') {
        const readyChildren = getReadyChildren(args.resume)
        if (readyChildren) {
          issue.ready_children = readyChildren
        }
      }

      return issue
    }

    if (!args.title || !args.description) {
      throw new Error('title and description required for new epic')
    }

    const id = bd(['create', '--title', args.title, '--type', 'epic', '--description', args.description, '--acceptance', 'PENDING', '--design', 'PENDING', '--silent'])
    bd(['update', id, '--status', 'in_progress'])

    return {id, is_new: true}
  },

  task_progress: (args) => {
    const issue = bdShowOne(args.id)
    if (!issue) {
      return {error: `Issue ${args.id} not found`}
    }
    const sections = parseNotes(issue.notes)

    if (args.findings) sections.findings.push(...args.findings)
    if (args.decisions) sections.decisions.push(...args.decisions)

    const newNotes = buildNotes(sections)
    const updateArgs = ['update', args.id, '--notes', newNotes]
    if (args.status) updateArgs.push('--status', args.status)
    bd(updateArgs)

    if (args.completed && needsReview(issue)) {
      return {
        success: true,
        notes: sections,
        status: args.status || issue.status,
        askUser: {
          question: `Review completed work (${issue.type}/P${issue.priority ?? 2}). What next?`,
          header: 'Review',
          options: [
            {label: 'Close issue', description: 'Work is complete'},
            {label: 'Needs correction', description: 'Continue fixing'},
            {label: 'Add more changes', description: 'Continue without closing'}
          ],
          multiSelect: false
        }
      }
    }

    return {success: true, notes: sections, status: args.status || issue.status}
  },

  task_decompose: (args) => {
    // Validate depends_on indices
    args.sub_issues.forEach((sub, i) => {
      if (sub.depends_on) {
        for (const depIdx of sub.depends_on) {
          if (depIdx < 0 || depIdx >= i) {
            throw new Error(`Invalid depends_on[${depIdx}] in sub_issue[${i}]: must reference 0 to ${i - 1}`)
          }
        }
      }
    })

    const ids = []
    for (const sub of args.sub_issues) {
      const subType = sub.type || 'task'
      const id = bd(['create', '--title', sub.title, '--parent', args.epic_id, '--type', subType, '--description', sub.description, '--acceptance', sub.acceptance, '--design', sub.design, '--silent'])
      ids.push(id)
    }

    // Add dependencies
    args.sub_issues.forEach((sub, i) => {
      if (sub.depends_on) {
        sub.depends_on.forEach(depIdx => bd(['dep', 'add', ids[i], ids[depIdx]]))
      }
    })

    let startedChildId = null
    if (args.start_child_index !== undefined) {
      if (!Number.isInteger(args.start_child_index)) {
        throw new Error('start_child_index must be an integer')
      }
      if (args.start_child_index < 0 || args.start_child_index >= ids.length) {
        throw new Error(`start_child_index ${args.start_child_index} out of range (0-${ids.length - 1})`)
      }
      startedChildId = ids[args.start_child_index]
      bd(['update', startedChildId, '--status', 'in_progress'])
    }

    if (args.update_epic_acceptance) {
      bd(['update', args.epic_id, '--acceptance', args.update_epic_acceptance])
    }

    return {ids, epic_id: args.epic_id, started_child_id: startedChildId}
  },

  task_create: (args) => {
    if (!args.title) {
      throw new Error('title required for new issue')
    }
    const issueType = args.type || 'task'
    const createArgs = ['create', '--title', args.title, '--type', issueType, '--silent']
    if (args.description) createArgs.push('--description', args.description)
    if (args.acceptance) createArgs.push('--acceptance', args.acceptance)
    if (args.design) createArgs.push('--design', args.design)
    if (args.parent) createArgs.push('--parent', args.parent)
    if (args.priority) createArgs.push('--priority', args.priority)

    const id = bd(createArgs)
    if (args.depends_on) {
      const depArgs = ['dep', 'add', id, args.depends_on]
      if (args.dep_type) depArgs.push('--type', args.dep_type)
      bd(depArgs)
    }
    return {id}
  },

  task_done: (args) => {
    const issue = bdShowOne(args.id)
    if (!issue) {
      return {error: `Issue ${args.id} not found`}
    }

    const sections = parseNotes(issue.notes)

    // Helper to perform actual close and return result
    const doClose = (summary) => {
      // Clean up pending_close if present
      if (sections.pending_close) {
        delete sections.pending_close
        bd(['update', args.id, '--notes', buildNotes(sections)])
      }

      bd(['close', args.id, '--reason', summary])

      let epicStatus = null
      let nextReady = null
      let parentId = null

      try {
        const closedIssue = bdShowOne(args.id)
        parentId = closedIssue.parent

        if (parentId) {
          const readyList = bdJson(['ready', '--parent', parentId])
          nextReady = readyList[0] || null

          const epicIssue = bdShowOne(parentId)
          if (epicIssue) {
            const children = epicIssue.children || []
            const completed = children.filter(c => c.status === 'closed').length
            epicStatus = {completed, remaining: children.length - completed}
          }
        }
      } catch (e) {
        // Continue with partial info
      }

      return {closed: args.id, next_ready: nextReady, epic_status: epicStatus, parent_id: parentId}
    }

    // If confirmed, retrieve stored pending_close data and close
    if (args.confirmed) {
      const pending = sections.pending_close
      if (!pending) {
        return {error: 'No pending close found. Call task_done with summary first.'}
      }

      // Merge any final findings/decisions
      if (args.findings) sections.findings.push(...args.findings)
      if (args.decisions) sections.decisions.push(...args.decisions)
      bd(['update', args.id, '--notes', buildNotes(sections)])

      return doClose(pending.summary)
    }

    // First call - check if review needed
    if (needsReview(issue)) {
      if (!args.summary) {
        return {error: 'summary required for issues that need review'}
      }

      // Store pending close data and any findings/decisions
      if (args.findings) sections.findings.push(...args.findings)
      if (args.decisions) sections.decisions.push(...args.decisions)
      sections.pending_close = {summary: args.summary}
      bd(['update', args.id, '--notes', buildNotes(sections)])

      return {
        askUser: {
          question: `Close ${issue.issue_type} "${issue.title}"?`,
          header: 'Confirm',
          options: [
            {label: 'Close', description: 'Work is complete'},
            {label: 'Keep open', description: 'Continue working'}
          ],
          multiSelect: false
        }
      }
    }

    // No review needed - close directly
    if (!args.summary) {
      return {error: 'summary required'}
    }

    // Record final findings/decisions before closing
    if (args.findings) sections.findings.push(...args.findings)
    if (args.decisions) sections.decisions.push(...args.decisions)
    if (args.findings || args.decisions) {
      bd(['update', args.id, '--notes', buildNotes(sections)])
    }

    return doClose(args.summary)
  }
}

createMcpServer({
  serverInfo: {name: 'task', version: '3.0.0'},
  tools,
  toolHandlers
})
