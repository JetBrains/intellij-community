// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {bd, bdJson, bdShowOne} from './bd-client.mjs'
import {addSectionComments, buildMemoryFromEntries, extractMemoryFromIssue, prepareSectionUpdates} from './notes.mjs'
import {buildIssueView} from './task-issue-view.mjs'
import {
  buildClosed,
  buildCreated,
  buildCreateSubTaskChoice,
  buildEmpty,
  buildError,
  buildIssue,
  buildNeedUser,
  buildProgress,
  buildStartNewTaskChoice,
  buildSummary,
  buildTaskChoice
} from './task-responses.mjs'
import {buildInProgressSummaries, computeSuggestedParent, createEpic, getReadyChildren} from './task-helpers.mjs'

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

function getNonEmptyString(value) {
  if (typeof value !== 'string') return null
  const trimmed = value.trim()
  return trimmed.length > 0 ? trimmed : null
}

function readMetaFields(source) {
  return {
    description: getNonEmptyString(source.description),
    design: getNonEmptyString(source.design),
    acceptance: getNonEmptyString(source.acceptance)
  }
}

function summarizeChildren(children) {
  if (!Array.isArray(children) || children.length === 0) return null
  return children.map(child => {
    const summary = {
      id: child.id,
      title: child.title,
      status: child.status
    }
    const type = child.issue_type || child.type
    if (type) summary.type = type
    if (child.priority !== undefined && child.priority !== null) summary.priority = child.priority
    if (child.assignee) summary.assignee = child.assignee
    return summary
  })
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
  if (issue.issue_type === 'epic') {
    let summarizedChildren = summarizeChildren(issue.children)
    if (!summarizedChildren) {
      try {
        const listedChildren = await bdJson(['list', '--parent', id])
        summarizedChildren = summarizeChildren(listedChildren)
      } catch (error) {
        summarizedChildren = null
      }
    }
    if (summarizedChildren) {
      issue.children = summarizedChildren
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

async function createEpicFromUserRequest(userRequest, {memory_limit, view, meta_max_chars, ...metaArgs} = {}) {
  const title = userRequest.trim()
  if (!title) {
    return null
  }
  const meta = readMetaFields(metaArgs)
  const id = await createEpic(title, {
    description: meta.description || `USER REQUEST: ${userRequest}`,
    design: meta.design,
    acceptance: meta.acceptance
  })
  const issue = await bdShowOne(id)
  if (!issue) {
    return buildIssue({id, is_new: true}, {next: 'continue'})
  }
  const memory = compactMemory(extractMemoryFromIssue(issue, memory_limit))
  issue.is_new = true
  let viewIssue
  try {
    viewIssue = buildIssueView(issue, {view, meta_max_chars})
  } catch (error) {
    return buildError(error.message || String(error))
  }
  return buildIssue(viewIssue, {next: 'continue', memory})
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

  const summaries = await buildInProgressSummaries(inProgress)
  const suggestedParent = computeSuggestedParent(inProgress)
  return buildSummary(summaries, {next: 'await_user', suggested_parent: suggestedParent})
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

  const hasUserRequest = typeof args.user_request === 'string' && args.user_request.trim().length > 0
  if (hasUserRequest) {
    const created = await createEpicFromUserRequest(args.user_request, args)
    if (created) {
      return created
    }
  }

  if (inProgress.length === 0) {
    if (ready.length > 0) {
      return buildReadyAskUser(ready)
    }
    return buildEmpty()
  }

  if (inProgress.length === 1 && !hasUserRequest) {
    const summaries = await buildInProgressSummaries(inProgress)
    const suggestedParent = computeSuggestedParent(inProgress)
    return buildSummary(summaries, {next: 'await_user', suggested_parent: suggestedParent})
  }

  return buildSelectionAskUser(inProgress, 'Which task to work on?', 'select_task')
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
      updateArgs.push('--notes', '')
    }
    if (updateArgs.length > 2) {
      await bd(updateArgs)
    }

    const memory = compactMemory(buildMemoryFromEntries(
      update.finalFindings,
      update.finalDecisions,
      args.memory_limit
    ))

    return buildProgress({memory, status: args.status || issue.status})
  },

  task_update_meta: async (args) => {
    const issue = /** @type {Issue | null} */ (await bdShowOne(args.id))
    if (!issue) {
      return buildError(`Issue ${args.id} not found`)
    }

    const meta = readMetaFields(args)
    if (!meta.description && !meta.design && !meta.acceptance) {
      return buildError('At least one of description, design, acceptance is required')
    }

    const updateArgs = ['update', args.id]
    if (meta.description) updateArgs.push('--description', meta.description)
    if (meta.design) updateArgs.push('--design', meta.design)
    if (meta.acceptance) updateArgs.push('--acceptance', meta.acceptance)

    await bd(updateArgs)

    return loadIssue(args.id, {
      resume: false,
      next: 'await_user',
      memory_limit: args.memory_limit,
      view: args.view,
      meta_max_chars: args.meta_max_chars
    })
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


    const normalizedSubs = []
    for (let i = 0; i < args.sub_issues.length; i++) {
      const sub = args.sub_issues[i]
      const meta = readMetaFields(sub)
      const missing = []
      if (!meta.description) missing.push('description')
      if (!meta.design) missing.push('design')
      if (!meta.acceptance) missing.push('acceptance')
      if (missing.length > 0) {
        return buildError(`sub_issues[${i}] missing required fields: ${missing.join(', ')}`)
      }
      normalizedSubs.push({...sub, ...meta})
    }

    const ids = []
    for (const sub of normalizedSubs) {
      const subType = sub.type || 'task'
      const id = await bd(['create', '--title', sub.title, '--parent', args.epic_id, '--type', subType, '--description', sub.description, '--acceptance', sub.acceptance, '--design', sub.design, '--silent'])
      ids.push(id)
    }

    // Add dependencies
    for (let i = 0; i < normalizedSubs.length; i++) {
      const sub = normalizedSubs[i]
      if (sub.depends_on) {
        for (const depIdx of sub.depends_on) {
          await bd(['dep', 'add', ids[i], ids[depIdx]])
        }
      }
    }

    let startedChildId
    if (args.sub_issues.length === 1) {
      startedChildId = ids[0]
      await bd(['update', startedChildId, '--status', 'in_progress'])
    }

    if (args.update_epic_acceptance) {
      await bd(['update', args.epic_id, '--acceptance', args.update_epic_acceptance])
    }

    const payload = {ids, epic_id: args.epic_id}
    if (startedChildId) payload.started_child_id = startedChildId
    return buildCreated(payload)
  },

  task_create: async (args) => {
    const title = getNonEmptyString(args.title)
    if (!title) {
      return buildError('title required for new issue')
    }
    const meta = readMetaFields(args)
    const missing = []
    if (!meta.description) missing.push('description')
    if (!meta.design) missing.push('design')
    if (!meta.acceptance) missing.push('acceptance')
    if (missing.length > 0) {
      return buildError(`Missing required fields: ${missing.join(', ')}`)
    }
    const issueType = args.type || 'task'
    const createArgs = [
      'create',
      '--title', title,
      '--type', issueType,
      '--description', meta.description,
      '--acceptance', meta.acceptance,
      '--design', meta.design,
      '--silent'
    ]
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

    const clearNotesIfNeeded = async () => {
      if (update.shouldStripNotes) {
        await bd(['update', args.id, '--notes', ''])
      }
    }

    // Helper to perform actual close and return result
    const doClose = async (summary) => {
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

    if (!args.reason) {
      return buildError('reason required')
    }

    await applyComments()
    await clearNotesIfNeeded()
    return await doClose(args.reason)
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
