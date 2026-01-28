// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {bd, bdJson, bdShowOne} from './bd-client.mjs'
import {addSectionComments, buildMemoryFromEntries, extractMemoryFromIssue, prepareSectionUpdates} from './notes.mjs'
import {buildIssueView} from './task-issue-view.mjs'
import {
  buildClosed,
  buildCreated,
  buildEmpty,
  buildError,
  buildIssue,
  buildProgress,
  buildSummary,
  buildUpdated
} from './task-responses.mjs'
import {buildInProgressSummaries, createEpic, getReadyChildren} from './task-helpers.mjs'


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

function normalizeDependsOnInput(dependsOn) {
  if (dependsOn === undefined || dependsOn === null) return {list: []}
  if (typeof dependsOn === 'string') {
    const trimmed = dependsOn.trim()
    return trimmed ? {list: [trimmed]} : {error: 'depends_on must be a non-empty string'}
  }
  if (Array.isArray(dependsOn)) {
    const list = []
    for (const entry of dependsOn) {
      if (typeof entry !== 'string') return {error: 'depends_on entries must be strings'}
      const trimmed = entry.trim()
      if (!trimmed) return {error: 'depends_on entries must be non-empty'}
      list.push(trimmed)
    }
    return {list}
  }
  return {error: 'depends_on must be a string or array of strings'}
}

function normalizeDecomposeDependsOn(dependsOn, subIndex) {
  if (!Array.isArray(dependsOn)) return {list: []}
  const list = []
  for (const entry of dependsOn) {
    if (Number.isInteger(entry)) {
      if (entry < 0 || entry >= subIndex) {
        return {error: `Invalid depends_on[${entry}] in sub_issue[${subIndex}]: must reference 0 to ${subIndex - 1}`}
      }
      list.push({type: 'index', value: entry})
      continue
    }
    if (typeof entry === 'string') {
      const trimmed = entry.trim()
      if (!trimmed) return {error: `Invalid depends_on entry in sub_issue[${subIndex}]: empty id`}
      list.push({type: 'id', value: trimmed})
      continue
    }
    return {error: `Invalid depends_on entry in sub_issue[${subIndex}]: must be integer index or issue id`}
  }
  return {list}
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
  if (inProgress.length === 0) {
    return buildEmpty('No in-progress tasks found.', 'await_user')
  }

  const summaries = await buildInProgressSummaries(inProgress)
  return buildSummary(summaries, {next: 'await_user'})
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

  const userRequest = getNonEmptyString(args.user_request)
  if (userRequest) {
    const created = await createEpicFromUserRequest(userRequest, args)
    if (created) {
      return created
    }
  }

  return buildError('task_start requires id or user_request')
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
      const depResult = normalizeDecomposeDependsOn(sub.depends_on, i)
      if (depResult.error) {
        return buildError(depResult.error)
      }
      normalizedSubs.push({...sub, ...meta, normalized_depends_on: depResult.list})
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
      const deps = Array.isArray(sub.normalized_depends_on) ? sub.normalized_depends_on : []
      if (deps.length === 0) continue
      const seen = new Set()
      for (const dep of deps) {
        const depId = dep.type === 'index' ? ids[dep.value] : dep.value
        if (!depId || depId === ids[i] || seen.has(depId)) continue
        seen.add(depId)
        const depArgs = ['dep', 'add', ids[i], depId]
        const depType = getNonEmptyString(sub.dep_type)
        if (depType) depArgs.push('--type', depType)
        await bd(depArgs)
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

    const depResult = normalizeDependsOnInput(args.depends_on)
    if (depResult.error) {
      return buildError(depResult.error)
    }

    const id = await bd(createArgs)
    if (depResult.list.length > 0) {
      for (const depId of depResult.list) {
        const depArgs = ['dep', 'add', id, depId]
        if (args.dep_type) depArgs.push('--type', args.dep_type)
        await bd(depArgs)
      }
    }
    return buildCreated({id})
  },

  task_link: async (args) => {
    const issue = /** @type {Issue | null} */ (await bdShowOne(args.id))
    if (!issue) {
      return buildError(`Issue ${args.id} not found`)
    }

    const depResult = normalizeDependsOnInput(args.depends_on)
    if (depResult.error) {
      return buildError(depResult.error)
    }
    if (depResult.list.length === 0) {
      return buildError('depends_on required')
    }

    const added = []
    const seen = new Set()
    for (const depId of depResult.list) {
      if (depId === args.id) {
        return buildError('depends_on cannot include the issue id itself')
      }
      if (seen.has(depId)) continue
      seen.add(depId)
      const depArgs = ['dep', 'add', args.id, depId]
      if (args.dep_type) depArgs.push('--type', args.dep_type)
      await bd(depArgs)
      added.push(depId)
    }

    const payload = {id: args.id, added_depends_on: added}
    if (args.dep_type) payload.dep_type = args.dep_type
    return buildUpdated(payload)
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

          const parentIssue = await bdShowOne(parentId)
          if (parentIssue) {
            let children
            try {
              children = await bdJson(['list', '--parent', parentId, '--all'])
            } catch (error) {
              children = Array.isArray(parentIssue.children) ? parentIssue.children : []
            }
            if (!Array.isArray(children)) {
              children = []
            }
            const completed = children.filter(c => c.status === 'closed').length
            const remaining = children.length - completed
            epicStatus = {completed, remaining}

            const isEpic = parentIssue.issue_type === 'epic' || parentIssue.type === 'epic'
            const isPinned = parentIssue.status === 'pinned' || parentIssue.status === 'hooked'
            if (isEpic && children.length > 0 && remaining === 0 && parentIssue.status !== 'closed' && !isPinned) {
              await bd(['close', parentId, '--reason', 'Auto-closed: all child issues closed'])
            }
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
