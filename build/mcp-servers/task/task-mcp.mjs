#!/usr/bin/env node
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {createMcpServer} from './mcp-rpc.mjs'
import {bd, bdJson, bdShowOne} from './bd-client.mjs'

// Parse notes into structured sections
// Only tracks findings and decisions; legacy IN PROGRESS/COMPLETED/NEXT preserved in 'other'
function parseNotes(notes) {
  const sections = {findings: [], decisions: [], other: []}
  if (!notes) return sections

  for (const line of notes.split('\n')) {
    if (line.startsWith('FINDING:')) {
      sections.findings.push(line.replace('FINDING:', '').trim())
    } else if (line.startsWith('KEY DECISION:')) {
      sections.decisions.push(line.replace('KEY DECISION:', '').trim())
    } else if (line.trim()) {
      // Preserve all other lines (including legacy IN PROGRESS/COMPLETED/NEXT)
      sections.other.push(line)
    }
  }
  return sections
}

// Build notes string from sections
// Only outputs findings + decisions; legacy fields preserved in 'other'
function buildNotes(sections) {
  const parts = []
  // Preserve legacy/unknown lines at the top
  for (const item of (sections.other || [])) parts.push(item)
  for (const item of sections.findings) parts.push(`FINDING: ${item}`)
  for (const item of sections.decisions) parts.push(`KEY DECISION: ${item}`)
  return parts.join('\n')
}

// Check if issue requires user review before closing (based on priority/type)
function needsReview(issue) {
  const priority = typeof issue.priority === 'string'
    ? parseInt(issue.priority.replace('P', ''), 10)
    : (issue.priority ?? 2)
  // P0/P1 (critical/high), bugs, and features always need review
  return priority <= 1 || issue.type === 'bug' || issue.type === 'feature'
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
    description: 'Update progress. Records findings/decisions in notes. Returns action with review requirement based on priority/type.',
    inputSchema: {
      type: 'object',
      properties: {
        id: {type: 'string', description: 'Issue ID'},
        finding: {type: 'string', description: 'Discovery during exploration - files, patterns, dependencies (appends to notes)'},
        completed: {type: 'string', description: 'What was completed (triggers review check for P0/P1/bug/feature)'},
        decision: {type: 'string', description: 'Key decision made (appends to notes)'},
        status: {type: 'string', enum: ['in_progress', 'blocked', 'deferred'], description: 'Set status (in_progress to resume blocked/deferred)'}
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
              type: {type: 'string', description: 'Issue type (task|bug|feature|chore)'},
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
    name: 'task_create',
    description: 'Create a non-epic issue (task/bug/feature). Returns action for next step.',
    inputSchema: {
      type: 'object',
      properties: {
        title: {type: 'string', description: 'Issue title'},
        description: {type: 'string', description: 'WHAT and WHY'},
        type: {type: 'string', description: 'Issue type (task|bug|feature|chore)', default: 'task'},
        parent: {type: 'string', description: 'Optional parent epic ID'},
        acceptance: {type: 'string', description: 'Testable outcomes'},
        design: {type: 'string', description: 'Technical approach'},
        priority: {type: 'string', description: 'Priority (0-4 or P0-P4)'},
        depends_on: {type: 'string', description: 'Optional dependency: new issue depends on this ID'},
        dep_type: {type: 'string', description: 'Dependency type (e.g. discovered-from)'}
      },
      required: ['title']
    }
  },
  {
    name: 'task_dep_add',
    description: 'Add dependency: child depends on parent (child needs parent).',
    inputSchema: {
      type: 'object',
      properties: {
        child: {type: 'string', description: 'Dependent issue ID'},
        parent: {type: 'string', description: 'Required issue ID'},
        type: {type: 'string', description: 'Dependency type (e.g. discovered-from)'}
      },
      required: ['child', 'parent']
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

// Compute action string for a specific issue based on status
function computeAction(issue) {
  const id = issue.id
  if (issue.status === 'in_progress') {
    return `Work on this issue. When done: task_done(id="${id}", summary="...")`
  }
  if (issue.status === 'open') {
    return issue.type === 'epic'
      ? `Start: task_epic(resume="${id}")`
      : `Start: task_progress(id="${id}", status="in_progress")`
  }
  if (issue.status === 'blocked') {
    return `Blocked. To unblock: task_progress(id="${id}", status="in_progress")`
  }
  if (issue.status === 'deferred') {
    return `Deferred. To resume: task_progress(id="${id}", status="in_progress")`
  }
  return `Check status: task_status(id="${id}")`
}

const toolHandlers = {
  task_status: (args) => {
    // Specific issue query - return minimal details
    if (args.id) {
      const issue = bdShowOne(args.id)
      if (!issue) {
        return {error: `Issue ${args.id} not found`}
      }
      return {
        id: issue.id,
        title: issue.title,
        status: issue.status,
        type: issue.type,
        action: computeAction(issue)
      }
    }

    // Overview query - selection-oriented responses
    const inProgress = bdJson(['list', '--status', 'in_progress'])
    const ready = bdJson(['ready', '--limit', '5'])

    // No in-progress issues
    if (inProgress.length === 0) {
      if (args.user_request) {
        return {action: `Create epic: task_epic(title="[summarize]", description="USER REQUEST: ${args.user_request}")`}
      }
      if (ready.length > 0) {
        return {
          action: 'ask',
          askUser: {
            question: 'Which task would you like to work on?',
            header: 'Task',
            options: ready.map(r => ({label: r.title, description: r.id})),
            multiSelect: false
          }
        }
      }
      return {action: 'No tasks. Ask user what to work on.'}
    }

    // Single in-progress, no new request - just continue
    if (inProgress.length === 1 && !args.user_request) {
      const issue = inProgress[0]
      return {
        id: issue.id,
        title: issue.title,
        action: `Continue working on "${issue.title}". When done: task_done(id="${issue.id}", summary="...")`
      }
    }

    // Multiple in-progress OR conflict with user_request - user must select
    const questionText = args.user_request
      ? `"${args.user_request}" - which task?`
      : 'Which task to work on?'
    return {
      action: 'ask',
      askUser: {
        question: questionText,
        header: 'Task',
        options: [
          ...inProgress.map(i => ({label: i.title, description: i.id})),
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

      let action
      if (issue.type === 'epic') {
        // Check if has sub-issues
        const hasChildren = issue.children && issue.children.length > 0
        action = hasChildren
          ? `Check sub-issues: task_status(id="${args.resume}")`
          : `Explore codebase, then: task_decompose(epic_id="${args.resume}", sub_issues=[...])`
      } else {
        action = `Work on this issue. When done: task_done(id="${args.resume}", summary="...")`
      }

      return {id: args.resume, title: issue.title, type: issue.type, is_new: false, action}
    }

    if (!args.title || !args.description) {
      throw new Error('title and description required for new epic')
    }

    const id = bd(['create', '--title', args.title, '--type', 'epic', '--description', args.description, '--acceptance', 'PENDING', '--design', 'PENDING', '--silent'])
    bd(['update', id, '--status', 'in_progress'])

    return {
      id,
      action: `Explore codebase, then: task_decompose(epic_id="${id}", sub_issues=[...])`
    }
  },

  task_progress: (args) => {
    const issue = bdShowOne(args.id)
    if (!issue) {
      return {error: `Issue ${args.id} not found`}
    }
    const sections = parseNotes(issue.notes)

    // Apply updates - only findings and decisions are stored in notes
    if (args.finding) sections.findings.push(args.finding)
    if (args.decision) sections.decisions.push(args.decision)

    const newNotes = buildNotes(sections)
    const updateArgs = ['update', args.id, '--notes', newNotes]
    if (args.status) updateArgs.push('--status', args.status)
    bd(updateArgs)

    // Determine action based on what was updated
    let action
    if (args.status === 'blocked' || args.status === 'deferred') {
      action = `Issue ${args.status}. Pick next: task_status()`
    } else if (args.status === 'in_progress') {
      action = `Resumed. Work on this issue. When done: task_done(id="${args.id}", summary="...")`
    } else if (args.completed) {
      // Work was completed - check if review is needed based on priority/type
      if (needsReview(issue)) {
        action = 'ask'
        return {
          success: true,
          notes: newNotes,
          status: args.status || issue.status,
          action,
          reviewRequired: `${issue.type}/P${issue.priority ?? 2}`,
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
      } else {
        action = `Changes noted. Close: task_done(id="${args.id}", summary="${args.completed}")`
      }
    } else {
      action = `Continue working. When done: task_done(id="${args.id}", summary="...")`
    }

    return {success: true, notes: newNotes, status: args.status || issue.status, action}
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

    if (args.update_epic_acceptance) {
      bd(['update', args.epic_id, '--acceptance', args.update_epic_acceptance])
    }

    return {
      ids,
      epic_id: args.epic_id,
      action: 'Sub-issues created. Write plan file (epic ID pointer), then ExitPlanMode for approval.'
    }
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
    return {
      id,
      action: `Created. Start: task_progress(id="${id}", status="in_progress")`
    }
  },

  task_dep_add: (args) => {
    const depArgs = ['dep', 'add', args.child, args.parent]
    if (args.type) depArgs.push('--type', args.type)
    bd(depArgs)
    return {success: true, action: `Dependency added. Continue: task_status(id="${args.child}")`}
  },

  task_done: (args) => {
    bd(['close', args.id, '--reason', args.summary])

    let epicStatus = null
    let nextReady = null
    let parentId = null
    let epicTitle = null

    try {
      const issue = bdShowOne(args.id)
      parentId = issue.parent

      if (parentId) {
        const readyList = bdJson(['ready', '--parent', parentId])
        nextReady = readyList[0] || null

        const epicIssue = bdShowOne(parentId)
        if (epicIssue) {
          epicTitle = epicIssue.title
          const children = epicIssue.children || []
          const completed = children.filter(c => c.status === 'closed').length
          epicStatus = {completed, remaining: children.length - completed}
        }
      }
    } catch (e) {
      // Continue with partial info
    }

    // Determine action
    let action
    if (epicStatus?.remaining === 0 && parentId) {
      action = epicTitle
        ? `ALL SUB-ISSUES DONE. Close epic "${epicTitle}" NOW: task_done(id="${parentId}", summary="...")`
        : `ALL SUB-ISSUES DONE. Close epic NOW: task_done(id="${parentId}", summary="...")`
    } else if (nextReady) {
      action = `Next: ${nextReady.id} - ${nextReady.title}. Start: task_progress(id="${nextReady.id}", status="in_progress")`
    } else if (parentId) {
      action = `Check for more work: task_status(id="${parentId}")`
    } else {
      action = 'Issue closed. Check task_status() for more work.'
    }

    return {closed: {id: args.id}, next_ready: nextReady, epic_status: epicStatus, action}
  }
}

createMcpServer({
  serverInfo: {name: 'task', version: '2.0.0'},
  tools,
  toolHandlers
})
