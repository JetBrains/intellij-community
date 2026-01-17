// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {bd, bdJson, bdShowOne} from './bd-client.mjs'
import {addSectionComments, buildMemoryFromEntries, buildPendingNotes, extractMemoryFromIssue, prepareSectionUpdates} from './notes.mjs'
import {buildIssueView} from './task-issue-view.mjs'
import {
  buildCreateSubTaskChoice,
  buildCreated,
  buildClosed,
  buildEmpty,
  buildError,
  buildIssue,
  buildNeedUser,
  buildProgress,
  buildStartNewTaskChoice,
  buildSummary,
  buildTaskChoice
} from './task-responses.mjs'
import {buildInProgressSummary, createEpic, getReadyChildren, needsReview} from './task-helpers.mjs'

/**
 * @typedef {{
 *   id?: string,
 *   title?: string,
 *   status?: string,
 *   issue_type?: string,
 *   parent?: string,
 *   priority?: (string|number),
 *   notes?: string,
 *   comments?: Array,
 *   children?: Array
 * }} Issue
 */

function compactMemory(memory) {
  if (!memory) return null
  const findings = Array.isArray(memory.findings) ? memory.findings : []
  const decisions = Array.isArray(memory.decisions) ? memory.decisions : []
  const result = {}
  if (findings.length > 0) result.findings = findings
  if (decisions.length > 0) result.decisions = decisions
  if (memory.truncated) {
    result.truncated = true
    if (memory.totals) {
      const moreFindings = Math.max(0, (memory.totals.findings || 0) - findings.length)
      const moreDecisions = Math.max(0, (memory.totals.decisions || 0) - decisions.length)
      if (moreFindings > 0 || moreDecisions > 0) {
        result.more = {findings: moreFindings, decisions: moreDecisions}
      }
    }
  }
  return Object.keys(result).length === 0 ? null : result
}

async function loadIssue(id, {resume, next, memory_limit, view, meta_max_chars} = {}) {
  const issue = /** @type {Issue | null} */ (await bdShowOne(id))
  if (!issue) {
    return buildError(`Issue ${id} not found`)
  }
  if (resume) {
    await bd(['update', id, '--status', 'in_progress'])
    issue.is_new = false
    issue.status = 'in_progress'
    if (issue.issue_type === 'epic') {
      const readyChildren = await getReadyChildren(id)
      if (readyChildren) {
        issue.ready_children = readyChildren
      }
    }
  }
  const memory = compactMemory(extractMemoryFromIssue(issue, memory_limit))
  let viewIssue
  try {
    viewIssue = buildIssueView(issue, {view, meta_max_chars})
  } catch (error) {
    return buildError(error.message || String(error))
  }
  return buildIssue(viewIssue, {next: next ?? (resume ? 'continue' : 'await_user'), memory})
}

function buildReadyAskUser(ready) {
  return buildNeedUser({
    question: 'Which task would you like to work on?',
    header: 'Task',
    choices: ready.map(buildTaskChoice),
    next: 'select_task'
  })
}

function buildSelectionAskUser(inProgress, questionText, next) {
  const parentIds = [...new Set(inProgress.map(i => i.parent).filter(Boolean))]
  const epicIds = inProgress.filter(i => i.issue_type === 'epic').map(i => i.id)
  const parentEpic = parentIds.length === 1 ? parentIds[0]
    : (parentIds.length === 0 && epicIds.length === 1 ? epicIds[0] : null)
  const choices = [
    ...inProgress.map(buildTaskChoice),
    ...(parentEpic ? [buildCreateSubTaskChoice(parentEpic)] : []),
    buildStartNewTaskChoice()
  ]
  return buildNeedUser({
    question: questionText,
    header: 'Task',
    choices,
    next
  })
}

async function handleTaskStatus(args) {
  if (args.user_request) {
    return buildError('task_status does not accept user_request; use task_start')
  }
  if (args.id) {
    return loadIssue(args.id, {
      resume: false,
      next: 'await_user',
      memory_limit: args.memory_limit,
      view: args.view,
      meta_max_chars: args.meta_max_chars
    })
  }

  const inProgress = /** @type {Issue[]} */ (await bdJson(['list', '--status', 'in_progress']))
  const ready = await bdJson(['ready', '--limit', '5'])

  if (inProgress.length === 0) {
    if (ready.length > 0) {
      return buildReadyAskUser(ready)
    }
    return buildEmpty()
  }

  if (inProgress.length === 1) {
    const summary = await buildInProgressSummary(inProgress[0])
    return buildSummary(summary, 'await_user')
  }

  return buildSelectionAskUser(inProgress, 'Which task to work on?', 'select_task')
}

async function handleTaskStart(args) {
  if (args.id) {
    return loadIssue(args.id, {
      resume: true,
      next: 'continue',
      memory_limit: args.memory_limit,
      view: args.view,
      meta_max_chars: args.meta_max_chars
    })
  }

  const inProgress = /** @type {Issue[]} */ (await bdJson(['list', '--status', 'in_progress']))
  const ready = await bdJson(['ready', '--limit', '5'])

  if (inProgress.length === 0) {
    if (args.user_request) {
      const title = args.user_request.trim()
      if (title) {
        const description = `USER REQUEST: ${args.user_request}`
        const id = await createEpic(title, description)
        const issue = await bdShowOne(id)
        if (!issue) {
          return buildIssue({id, is_new: true}, {next: 'continue'})
        }
        const memory = compactMemory(extractMemoryFromIssue(issue, args.memory_limit))
        issue.is_new = true
        let viewIssue
        try {
          viewIssue = buildIssueView(issue, {view: args.view, meta_max_chars: args.meta_max_chars})
        } catch (error) {
          return buildError(error.message || String(error))
        }
        return buildIssue(viewIssue, {next: 'continue', memory})
      }
    }
    if (ready.length > 0) {
      return buildReadyAskUser(ready)
    }
    return buildEmpty()
  }

  if (inProgress.length === 1 && !args.user_request) {
    const summary = await buildInProgressSummary(inProgress[0])
    return buildSummary(summary, 'await_user')
  }

  const questionText = args.user_request
    ? 'New request - which task?'
    : 'Which task to work on?'
  const next = args.user_request ? 'select_task_for_request' : 'select_task'
  return buildSelectionAskUser(inProgress, questionText, next)
}

export const toolHandlers = {
  task_status: handleTaskStatus,
  task_start: handleTaskStart,

  task_progress: async (args) => {
    const issue = /** @type {Issue | null} */ (await bdShowOne(args.id))
    if (!issue) {
      return buildError(`Issue ${args.id} not found`)
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

    const memory = compactMemory(buildMemoryFromEntries(
      update.finalFindings,
      update.finalDecisions,
      update.notesSections.pending_close,
      args.memory_limit
    ))

    if (args.completed && needsReview(issue)) {
      const response = buildNeedUser({
        question: `Review completed work (${issue.issue_type}/P${issue.priority ?? 2}). What next?`,
        header: 'Review',
        choices: [
          {label: 'Close issue', description: 'Work is complete', action: 'close_issue'},
          {label: 'Needs correction', description: 'Continue fixing', action: 'needs_correction'},
          {label: 'Add more changes', description: 'Continue without closing', action: 'continue_work'}
        ],
        next: 'review_completed'
      })
      if (memory) response.memory = memory
      response.status = args.status || issue.status
      return response
    }

    return buildProgress({memory, status: args.status || issue.status})
  },

  task_decompose: async (args) => {
    // Validate depends_on indices
    for (let i = 0; i < args.sub_issues.length; i++) {
      const sub = args.sub_issues[i]
      if (sub.depends_on) {
        for (const depIdx of sub.depends_on) {
          if (depIdx < 0 || depIdx >= i) {
            return buildError(`Invalid depends_on[${depIdx}] in sub_issue[${i}]: must reference 0 to ${i - 1}`)
          }
        }
      }
    }

    if (args.start_child_index !== undefined) {
      if (!Number.isInteger(args.start_child_index)) {
        return buildError('start_child_index must be an integer')
      }
      if (args.start_child_index < 0 || args.start_child_index >= args.sub_issues.length) {
        return buildError(`start_child_index ${args.start_child_index} out of range (0-${args.sub_issues.length - 1})`)
      }
    }

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
      startedChildId = ids[args.start_child_index]
      await bd(['update', startedChildId, '--status', 'in_progress'])
    }

    if (args.update_epic_acceptance) {
      await bd(['update', args.epic_id, '--acceptance', args.update_epic_acceptance])
    }

    return buildCreated({ids, epic_id: args.epic_id, started_child_id: startedChildId})
  },

  task_create: async (args) => {
    if (!args.title) {
      return buildError('title required for new issue')
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
    return buildCreated({id})
  },

  task_done: async (args) => {
    const issue = /** @type {Issue | null} */ (await bdShowOne(args.id))
    if (!issue) {
      return buildError(`Issue ${args.id} not found`)
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

      return buildClosed({closed: args.id, next_ready: nextReady, epic_status: epicStatus, parent_id: parentId})
    }

    // If confirmed, retrieve stored pending_close data and close
    if (args.confirmed) {
      const pending = update.notesSections.pending_close
      if (!pending) {
        return buildError('No pending close found. Call task_done with summary first.')
      }

      await applyComments()
      return await doClose(pending.summary)
    }

    // First call - check if review needed
    if (needsReview(issue)) {
      if (!args.summary) {
        return buildError('summary required for issues that need review')
      }

      await applyComments()
      await updateNotes({summary: args.summary})

      return buildNeedUser({
        question: `Close ${issue.issue_type} "${issue.title}"?`,
        header: 'Confirm',
        choices: [
          {label: 'Close', description: 'Work is complete', action: 'confirm_close'},
          {label: 'Keep open', description: 'Continue working', action: 'keep_open'}
        ],
        next: 'confirm_close'
      })
    }

    // No review needed - close directly
    if (!args.summary) {
      return buildError('summary required')
    }

    await applyComments()
    return await doClose(args.summary)
  },

  task_reopen: async (args) => {
    if (!args.reason || !args.reason.trim()) {
      return buildError('reason required for reopen')
    }

    const issue = /** @type {Issue | null} */ (await bdShowOne(args.id))
    if (!issue) {
      return buildError(`Issue ${args.id} not found`)
    }

    await bd(['reopen', args.id, '--reason', args.reason.trim()])

    const reopened = /** @type {Issue | null} */ (await bdShowOne(args.id))
    if (!reopened) {
      return buildIssue({id: args.id, status: 'open'}, {next: 'await_user'})
    }
    const memory = compactMemory(extractMemoryFromIssue(reopened, args.memory_limit))
    let viewIssue
    try {
      viewIssue = buildIssueView(reopened, {view: args.view, meta_max_chars: args.meta_max_chars})
    } catch (error) {
      return buildError(error.message || String(error))
    }
    return buildIssue(viewIssue, {next: 'await_user', memory})
  }
}
