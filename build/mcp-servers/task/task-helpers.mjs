// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {bd, bdJson} from './bd-client.mjs'

/**
 * @typedef {{
 *   id?: string,
 *   title?: string,
 *   status?: string,
 *   issue_type?: string,
 *   parent?: string,
 *   priority?: (string|number)
 * }} Issue
 */

// Fetch ready children for an epic (used in task_status and task_start resume)
export async function getReadyChildren(epicId) {
  const readyChildren = await bdJson(['ready', '--parent', epicId])
  if (readyChildren.length > 0) {
    return readyChildren.map(c => ({id: c.id, title: c.title}))
  }
  return null
}

export async function createEpic(title, {description, design, acceptance} = {}) {
  const resolvedDescription = description || `USER REQUEST: ${title}`
  const resolvedDesign = design || 'PENDING'
  const resolvedAcceptance = acceptance || 'PENDING'
  const id = await bd([
    'create',
    '--title', title,
    '--type', 'epic',
    '--description', resolvedDescription,
    '--acceptance', resolvedAcceptance,
    '--design', resolvedDesign,
    '--silent'
  ])
  await bd(['update', id, '--status', 'in_progress'])
  return id
}

export async function buildInProgressSummary(issue) {
  const result = {id: issue.id, title: issue.title, status: issue.status}
  if (issue.priority !== undefined && issue.priority !== null) {
    result.priority = issue.priority
  }
  if (issue.issue_type) {
    result.type = issue.issue_type
  }
  if (issue.assignee) {
    result.assignee = issue.assignee
  }
  if (issue.parent) {
    result.parent = issue.parent
  }
  if (issue.issue_type === 'epic') {
    const readyChildren = await getReadyChildren(issue.id)
    if (readyChildren) {
      result.ready_children = readyChildren
    }
  }
  return result
}

export async function buildInProgressSummaries(issues) {
  const summaries = []
  for (const issue of issues) {
    summaries.push(await buildInProgressSummary(issue))
  }
  return summaries
}

export function computeSuggestedParent(issues) {
  const parentIds = [...new Set(issues.map(i => i.parent).filter(Boolean))]
  const epicIds = issues.filter(i => i.issue_type === 'epic').map(i => i.id)
  if (parentIds.length === 1) {
    return {id: parentIds[0], reason: 'shared parent for in-progress tasks'}
  }
  if (parentIds.length === 0 && epicIds.length === 1) {
    return {id: epicIds[0], reason: 'single in-progress epic'}
  }
  return null
}
