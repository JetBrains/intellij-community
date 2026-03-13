// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import type {ToolSpecLike} from './proxy-tools/types'

type ProjectPathKey = 'project_path' | 'projectPath'

interface ProjectPathManager {
  injectProjectPathArgs: (toolName: string | undefined, args: Record<string, unknown>) => void
  stripProjectPathFromTools: (tools: ToolSpecLike[]) => void
  updateProjectPathKeys: (tools: ToolSpecLike[]) => void
}

export function createProjectPathManager({
  projectPath,
  defaultProjectPathKey = 'project_path'
}: {
  projectPath: string
  defaultProjectPathKey?: ProjectPathKey
}): ProjectPathManager {
  let projectPathKey: ProjectPathKey | null = null
  let hasSeenToolsList = false
  let hasProjectPathTools = false
  const toolProjectPathKeyByName = new Map<string, ProjectPathKey>()

  function normalizeProjectPathArgs(args: Record<string, unknown>, desiredKey: ProjectPathKey | null): void {
    if (!desiredKey) return

    const hasSnake = Object.prototype.hasOwnProperty.call(args, 'project_path')
    const hasCamel = Object.prototype.hasOwnProperty.call(args, 'projectPath')

    if (desiredKey === 'projectPath') {
      if (hasSnake) delete args.project_path
      args.projectPath = projectPath
      return
    }

    if (desiredKey === 'project_path') {
      if (hasCamel) delete args.projectPath
      args.project_path = projectPath
    }
  }

  function shouldInjectProjectPath(toolName: string | undefined): boolean {
    if (!hasSeenToolsList) return true
    if (!hasProjectPathTools) return false
    if (!toolName) return true
    return toolProjectPathKeyByName.has(toolName)
  }

  function chooseProjectPathKey(toolName: string | undefined): ProjectPathKey {
    if (toolName) {
      const key = toolProjectPathKeyByName.get(toolName)
      if (key) return key
    }
    return projectPathKey || defaultProjectPathKey
  }

  function injectProjectPathArgs(toolName: string | undefined, args: Record<string, unknown>): void {
    if (!args || typeof args !== 'object') return
    if (shouldInjectProjectPath(toolName)) {
      normalizeProjectPathArgs(args, chooseProjectPathKey(toolName))
    }
  }

  function updateProjectPathKeys(tools: ToolSpecLike[]): void {
    if (!Array.isArray(tools)) return

    let hasSnake = false
    let hasCamel = false
    toolProjectPathKeyByName.clear()

    for (const tool of tools) {
      const props = tool?.inputSchema?.properties
      if (!props || typeof props !== 'object') continue

      if (Object.prototype.hasOwnProperty.call(props, 'project_path')) {
        hasSnake = true
        if (typeof tool.name === 'string') {
          toolProjectPathKeyByName.set(tool.name, 'project_path')
        }
        continue
      }

      if (Object.prototype.hasOwnProperty.call(props, 'projectPath')) {
        hasCamel = true
        if (typeof tool.name === 'string') {
          toolProjectPathKeyByName.set(tool.name, 'projectPath')
        }
      }
    }

    hasSeenToolsList = true
    hasProjectPathTools = toolProjectPathKeyByName.size > 0

    if (hasSnake) projectPathKey = 'project_path'
    else if (hasCamel) projectPathKey = 'projectPath'
    else projectPathKey = null
  }

  function stripProjectPathFromTools(tools: ToolSpecLike[]): void {
    if (!Array.isArray(tools)) return

    for (const tool of tools) {
      const schema = tool?.inputSchema
      if (!schema || schema.type !== 'object') continue

      const props = schema.properties
      if (!props || typeof props !== 'object') continue

      const removedKeys = []
      if (Object.prototype.hasOwnProperty.call(props, 'project_path')) {
        delete props.project_path
        removedKeys.push('project_path')
      }
      if (Object.prototype.hasOwnProperty.call(props, 'projectPath')) {
        delete props.projectPath
        removedKeys.push('projectPath')
      }

      if (removedKeys.length > 0 && Array.isArray(schema.required)) {
        schema.required = schema.required.filter((name) => !removedKeys.includes(name))
      }
    }
  }

  return {
    injectProjectPathArgs,
    stripProjectPathFromTools,
    updateProjectPathKeys
  }
}
