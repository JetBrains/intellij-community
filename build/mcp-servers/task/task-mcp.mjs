#!/usr/bin/env node
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {createMcpServer} from './mcp-rpc.mjs'
import {bd, bdJson} from './bd-client.mjs'

// Parse notes into structured sections
function parseNotes(notes) {
  const sections = {completed: [], in_progress: '', next: '', decisions: []}
  if (!notes) return sections

  for (const line of notes.split('\n')) {
    if (line.startsWith('COMPLETED:')) {
      sections.completed.push(line.replace('COMPLETED:', '').trim())
    } else if (line.startsWith('IN PROGRESS:')) {
      sections.in_progress = line.replace('IN PROGRESS:', '').trim()
    } else if (line.startsWith('NEXT:')) {
      sections.next = line.replace('NEXT:', '').trim()
    } else if (line.startsWith('KEY DECISION:')) {
      sections.decisions.push(line.replace('KEY DECISION:', '').trim())
    }
  }
  return sections
}

// Build notes string from sections
function buildNotes(sections) {
  const parts = []
  for (const item of sections.completed) parts.push(`COMPLETED: ${item}`)
  if (sections.in_progress) parts.push(`IN PROGRESS: ${sections.in_progress}`)
  if (sections.next) parts.push(`NEXT: ${sections.next}`)
  for (const item of sections.decisions) parts.push(`KEY DECISION: ${item}`)
  return parts.join('\n')
}

// 5 action-driven tools
const tools = [
  {
    name: 'task_status',
    description: 'Get state and next action. Pass user_request for new tasks.',
    inputSchema: {
      type: 'object',
      properties: {
        id: {type: 'string', description: 'Issue ID for full details'},
        user_request: {type: 'string', description: 'User task description ($ARGUMENTS from /task command)'}
      }
    }
  },
  {
    name: 'task_epic',
    description: 'Create new epic or resume existing. Returns action for next step.',
    inputSchema: {
      type: 'object',
      properties: {
        title: {type: 'string', description: 'Epic title (required for new)'},
        description: {type: 'string', description: 'WHAT and WHY (required for new)'},
        resume: {type: 'string', description: 'Resume existing by ID'}
      }
    }
  },
  {
    name: 'task_progress',
    description: 'Update progress notes. Returns action to continue.',
    inputSchema: {
      type: 'object',
      properties: {
        id: {type: 'string', description: 'Issue ID'},
        completed: {type: 'string', description: 'What was completed (appends)'},
        working_on: {type: 'string', description: 'Current work (replaces)'},
        next: {type: 'string', description: 'Next action (replaces)'},
        decision: {type: 'string', description: 'Key decision (appends)'}
      },
      required: ['id']
    }
  },
  {
    name: 'task_decompose',
    description: 'Create sub-issues under epic. Returns action for approval flow.',
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
              description: {type: 'string', description: 'WHAT and WHY'},
              acceptance: {type: 'string', description: 'Testable outcomes'},
              design: {type: 'string', description: 'Technical approach'},
              depends_on: {type: 'array', items: {type: 'integer'}, description: '0-based indices'}
            },
            required: ['title', 'description', 'acceptance', 'design']
          }
        },
        update_epic_acceptance: {type: 'string', description: 'Update epic acceptance'}
      },
      required: ['epic_id', 'sub_issues']
    }
  },
  {
    name: 'task_done',
    description: 'Close issue. Returns action for next work or epic closure.',
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
      const issue = bdJson(['show', args.id])
      // For specific issue, provide contextual action
      if (issue.status === 'in_progress') {
        const notes = parseNotes(issue.notes)
        issue.action = notes.next
          ? `Continue: ${notes.next}`
          : `Work on this issue. When done: task_done(id="${args.id}", summary="...")`
      }
      return issue
    }

    // Overview: in-progress + ready
    const inProgressList = bdJson(['list', '--status', 'in_progress'])
    const readyList = bdJson(['ready', '--limit', '10'])
    const inProgress = inProgressList.issues?.[0] || null
    const ready = readyList.issues || []

    let action
    if (inProgress) {
      if (args.user_request) {
        // Conflict: has in-progress but user provided new task
        action = `In-progress: ${inProgress.id} "${inProgress.title}". New request provided. AskUserQuestion: continue current or create new epic?`
      } else {
        const notes = parseNotes(inProgress.notes)
        action = notes.next
          ? `Continue: ${notes.next}`
          : `Resume ${inProgress.id}: ${inProgress.title}. Update: task_progress(id="${inProgress.id}", working_on="...")`
      }
    } else if (args.user_request) {
      // No in-progress, user provided task - create epic
      action = `Create epic: task_epic(title="[summarize request]", description="USER REQUEST: ${args.user_request}")`
    } else if (ready.length > 0) {
      action = `Start ${ready[0].id}: ${ready[0].title}. Run: task_epic(resume="${ready[0].id}")`
    } else {
      action = 'No tasks. Ask user what to work on.'
    }

    return {in_progress: inProgress, ready, action}
  },

  task_epic: (args) => {
    if (args.resume) {
      const issue = bdJson(['show', args.resume])
      bd(['update', args.resume, '--status', 'in_progress'])

      // Check if has sub-issues
      const hasChildren = issue.children && issue.children.length > 0
      const action = hasChildren
        ? `Check sub-issues: task_status(id="${args.resume}")`
        : `Explore codebase, then: task_decompose(epic_id="${args.resume}", sub_issues=[...])`

      return {id: args.resume, title: issue.title, type: issue.type, is_new: false, action}
    }

    if (!args.title || !args.description) {
      throw new Error('title and description required for new epic')
    }

    const id = bd(['create', '--title', args.title, '--type', 'epic', '--description', args.description, '--acceptance', 'PENDING', '--design', 'PENDING', '--silent'])
    bd(['update', id, '--status', 'in_progress'])

    return {
      id,
      title: args.title,
      is_new: true,
      action: `Explore codebase, then: task_decompose(epic_id="${id}", sub_issues=[...])`
    }
  },

  task_progress: (args) => {
    const issue = bdJson(['show', args.id])
    const sections = parseNotes(issue.notes)

    // Apply updates
    if (args.completed) sections.completed.push(args.completed)
    if (args.working_on !== undefined) sections.in_progress = args.working_on
    if (args.next !== undefined) sections.next = args.next
    if (args.decision) sections.decisions.push(args.decision)

    const newNotes = buildNotes(sections)
    bd(['update', args.id, '--notes', newNotes])

    // Determine action
    let action
    if (sections.next) {
      action = `Continue: ${sections.next}`
    } else if (sections.in_progress) {
      action = `Working on: ${sections.in_progress}. When done: task_done(id="${args.id}", summary="...")`
    } else {
      action = `Update next step or complete: task_done(id="${args.id}", summary="...")`
    }

    return {success: true, notes: newNotes, action}
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
      const id = bd(['create', '--title', sub.title, '--parent', args.epic_id, '--type', 'task', '--description', sub.description, '--acceptance', sub.acceptance, '--design', sub.design, '--silent'])
      ids.push(id)
    }

    // Add dependencies
    args.sub_issues.forEach((sub, i) => {
      if (sub.depends_on) {
        sub.depends_on.forEach(depIdx => bd(['dep', 'add', ids[i], ids[depIdx]]))
      }
    })

    if (args.update_epic_acceptance) {
      bd(['update', args.epic_id, '--acceptance', args.update_epic_acceptance])
    }

    return {
      ids,
      epic_id: args.epic_id,
      action: 'Sub-issues created. Write plan file (epic ID pointer), then ExitPlanMode for approval.'
    }
  },

  task_done: (args) => {
    bd(['close', args.id, '--reason', args.summary])

    let epicStatus = null
    let nextReady = null
    let parentId = null

    try {
      const issue = bdJson(['show', args.id])
      parentId = issue.parent

      if (parentId) {
        const readyList = bdJson(['ready', '--parent', parentId])
        nextReady = readyList.issues?.[0] || null

        const epicIssue = bdJson(['show', parentId])
        const children = epicIssue.children || []
        const completed = children.filter(c => c.status === 'closed').length + 1
        epicStatus = {completed, remaining: children.length - completed}
      }
    } catch (e) {
      // Continue with partial info
    }

    // Determine action
    let action
    if (epicStatus?.remaining === 0 && parentId) {
      action = `ALL SUB-ISSUES DONE. Close epic NOW: task_done(id="${parentId}", summary="...")`
    } else if (nextReady) {
      action = `Next: ${nextReady.id} - ${nextReady.title}. Start: task_progress(id="${nextReady.id}", working_on="...")`
    } else if (parentId) {
      action = `Check for more work: task_status(id="${parentId}")`
    } else {
      action = 'Epic closed. Check task_status() for more work.'
    }

    return {closed: {id: args.id}, next_ready: nextReady, epic_status: epicStatus, action}
  }
}

createMcpServer({
  serverInfo: {name: 'task', version: '2.0.0'},
  tools,
  toolHandlers
})
