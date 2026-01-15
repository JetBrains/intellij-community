#!/usr/bin/env node
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {createMcpServer} from '../shared/mcp-rpc.mjs'
import {bd, bdJson, bdShowOne} from './bd-client.mjs'
import {addSectionComments, buildPendingNotes, parseNotes, prepareSectionUpdates} from './notes.mjs'

// Fetch ready children for an epic (used in task_status and task_start resume)
async function getReadyChildren(epicId) {
  const readyChildren = await bdJson(['ready', '--parent', epicId])
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

async function createEpic(title, description) {
  const id = await bd(['create', '--title', title, '--type', 'epic', '--description', description, '--acceptance', 'PENDING', '--design', 'PENDING', '--silent'])
  await bd(['update', id, '--status', 'in_progress'])
  return id
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
    name: 'task_start',
    description: 'Start task workflow (status + optional epic creation)',
    inputSchema: {
      type: 'object',
      properties: {
        id: {type: 'string', description: 'Issue ID for full details'},
        user_request: {type: 'string', description: 'User task description'}
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

async function handleTaskStatus(args, options = {}) {
  const shouldResume = options.resume === true
  // Specific issue query - return full details (replaces task_show)
  if (args.id) {
    const issue = await bdShowOne(args.id)
    if (!issue) {
      return {error: `Issue ${args.id} not found`}
    }
    if (shouldResume) {
      await bd(['update', args.id, '--status', 'in_progress'])
      issue.is_new = false
      issue.status = 'in_progress'
      if (issue.issue_type === 'epic') {
        const readyChildren = await getReadyChildren(args.id)
        if (readyChildren) {
          issue.ready_children = readyChildren
        }
      }
    }
    const parsedNotes = parseNotes(issue.notes, issue['comments'])
    if (parsedNotes) {
      issue.notes = parsedNotes
    } else {
      delete issue.notes
    }
    return issue
  }

  // Overview query - selection-oriented responses
  const inProgress = await bdJson(['list', '--status', 'in_progress'])
  const ready = await bdJson(['ready', '--limit', '5'])

  // No in-progress issues
  if (inProgress.length === 0) {
    if (args.user_request) {
      const title = args.user_request.trim()
      if (title) {
        const description = `USER REQUEST: ${args.user_request}`
        const id = await createEpic(title, description)
        const issue = await bdShowOne(id)
        if (!issue) {
          return {id, is_new: true}
        }
        const parsedNotes = parseNotes(issue.notes, issue['comments'])
        if (parsedNotes) {
          issue.notes = parsedNotes
        } else {
          delete issue.notes
        }
        issue.is_new = true
        return issue
      }
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
      const readyChildren = await getReadyChildren(issue.id)
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
}

const toolHandlers = {
  task_status: handleTaskStatus,
  task_start: (args) => handleTaskStatus(args, {resume: true}),

  task_progress: async (args) => {
    const issue = await bdShowOne(args.id)
    if (!issue) {
      return {error: `Issue ${args.id} not found`}
    }

    const update = prepareSectionUpdates(issue, args.findings, args.decisions)
    if (update.findingsToAdd.length > 0 || update.decisionsToAdd.length > 0) {
      await addSectionComments(args.id, update.findingsToAdd, update.decisionsToAdd)
    }

    const updateArgs = ['update', args.id]
    if (args.status) updateArgs.push('--status', args.status)
    if (update.shouldStripNotes) {
      updateArgs.push('--notes', buildPendingNotes(update.notesSections.pending_close))
    }
    if (updateArgs.length > 2) {
      await bd(updateArgs)
    }

    const responseNotes = {findings: update.finalFindings, decisions: update.finalDecisions}
    if (update.notesSections.pending_close) {
      responseNotes.pending_close = update.notesSections.pending_close
    }

    if (args.completed && needsReview(issue)) {
      return {
        success: true,
        notes: responseNotes,
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

    return {success: true, notes: responseNotes, status: args.status || issue.status}
  },

  task_decompose: async (args) => {
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
      const id = await bd(['create', '--title', sub.title, '--parent', args.epic_id, '--type', subType, '--description', sub.description, '--acceptance', sub.acceptance, '--design', sub.design, '--silent'])
      ids.push(id)
    }

    // Add dependencies
    for (let i = 0; i < args.sub_issues.length; i++) {
      const sub = args.sub_issues[i]
      if (sub.depends_on) {
        for (const depIdx of sub.depends_on) {
          await bd(['dep', 'add', ids[i], ids[depIdx]])
        }
      }
    }

    let startedChildId = null
    if (args.start_child_index !== undefined) {
      if (!Number.isInteger(args.start_child_index)) {
        throw new Error('start_child_index must be an integer')
      }
      if (args.start_child_index < 0 || args.start_child_index >= ids.length) {
        throw new Error(`start_child_index ${args.start_child_index} out of range (0-${ids.length - 1})`)
      }
      startedChildId = ids[args.start_child_index]
      await bd(['update', startedChildId, '--status', 'in_progress'])
    }

    if (args.update_epic_acceptance) {
      await bd(['update', args.epic_id, '--acceptance', args.update_epic_acceptance])
    }

    return {ids, epic_id: args.epic_id, started_child_id: startedChildId}
  },

  task_create: async (args) => {
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

    const id = await bd(createArgs)
    if (args.depends_on) {
      const depArgs = ['dep', 'add', id, args.depends_on]
      if (args.dep_type) depArgs.push('--type', args.dep_type)
      await bd(depArgs)
    }
    return {id}
  },

  task_done: async (args) => {
    const issue = await bdShowOne(args.id)
    if (!issue) {
      return {error: `Issue ${args.id} not found`}
    }

    const update = prepareSectionUpdates(issue, args.findings, args.decisions)

    const applyComments = async () => {
      if (update.findingsToAdd.length > 0 || update.decisionsToAdd.length > 0) {
        await addSectionComments(args.id, update.findingsToAdd, update.decisionsToAdd)
      }
    }

    const updateNotes = async (pendingClose) => {
      const pendingCloseChanged = JSON.stringify(pendingClose ?? null)
        !== JSON.stringify(update.notesSections.pending_close ?? null)
      if (update.shouldStripNotes || pendingCloseChanged) {
        await bd(['update', args.id, '--notes', buildPendingNotes(pendingClose)])
      }
    }

    // Helper to perform actual close and return result
    const doClose = async (summary) => {
      await updateNotes(null)

      await bd(['close', args.id, '--reason', summary])

      let epicStatus = null
      let nextReady = null
      let parentId = null

      try {
        const closedIssue = await bdShowOne(args.id)
        parentId = closedIssue.parent

        if (parentId) {
          const readyList = await bdJson(['ready', '--parent', parentId])
          nextReady = readyList[0] || null

          const epicIssue = await bdShowOne(parentId)
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
      const pending = update.notesSections.pending_close
      if (!pending) {
        return {error: 'No pending close found. Call task_done with summary first.'}
      }

      await applyComments()
      return await doClose(pending.summary)
    }

    // First call - check if review needed
    if (needsReview(issue)) {
      if (!args.summary) {
        return {error: 'summary required for issues that need review'}
      }

      await applyComments()
      await updateNotes({summary: args.summary})

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

    await applyComments()
    return await doClose(args.summary)
  }
}

createMcpServer({
  serverInfo: {name: 'task', version: '3.0.0'},
  tools,
  toolHandlers
})
