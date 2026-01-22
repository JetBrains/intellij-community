// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

export function createProjectPathManager({projectPath, defaultProjectPathKey = 'project_path'}) {
  let projectPathKey = null
  let hasSeenToolsList = false
  let hasProjectPathTools = false
  const toolProjectPathKeyByName = new Map()

  function normalizeProjectPathArgs(args, desiredKey) {
    if (!desiredKey) return

    const hasSnake = Object.prototype.hasOwnProperty.call(args, 'project_path')
    const hasCamel = Object.prototype.hasOwnProperty.call(args, 'projectPath')

    if (desiredKey === 'projectPath') {
      if (hasCamel) {
        if (hasSnake) delete args.project_path
        if (args.projectPath == null) args.projectPath = projectPath
        return
      }
      if (hasSnake) {
        args.projectPath = args.project_path
        delete args.project_path
        if (args.projectPath == null) args.projectPath = projectPath
        return
      }
      args.projectPath = projectPath
      return
    }

    if (desiredKey === 'project_path') {
      if (hasSnake) {
        if (hasCamel) delete args.projectPath
        if (args.project_path == null) args.project_path = projectPath
        return
      }
      if (hasCamel) {
        args.project_path = args.projectPath
        delete args.projectPath
        if (args.project_path == null) args.project_path = projectPath
        return
      }
      args.project_path = projectPath
    }
  }

  function shouldInjectProjectPath(toolName) {
    if (!hasSeenToolsList) return true
    if (!hasProjectPathTools) return false
    if (!toolName) return true
    return toolProjectPathKeyByName.has(toolName)
  }

  function chooseProjectPathKey(toolName) {
    if (toolName) {
      const key = toolProjectPathKeyByName.get(toolName)
      if (key) return key
    }
    return projectPathKey || defaultProjectPathKey
  }

  function injectProjectPathArgs(toolName, args) {
    if (!args || typeof args !== 'object') return
    if (shouldInjectProjectPath(toolName)) {
      normalizeProjectPathArgs(args, chooseProjectPathKey(toolName))
    }
  }

  function updateProjectPathKeys(tools) {
    if (!Array.isArray(tools)) return

    let hasSnake = false
    let hasCamel = false
    toolProjectPathKeyByName.clear()

    for (const tool of tools) {
      const props = tool?.inputSchema?.properties
      if (!props || typeof props !== 'object') continue

      if (Object.prototype.hasOwnProperty.call(props, 'project_path')) {
        hasSnake = true
        toolProjectPathKeyByName.set(tool.name, 'project_path')
        continue
      }

      if (Object.prototype.hasOwnProperty.call(props, 'projectPath')) {
        hasCamel = true
        toolProjectPathKeyByName.set(tool.name, 'projectPath')
      }
    }

    hasSeenToolsList = true
    hasProjectPathTools = toolProjectPathKeyByName.size > 0

    if (hasSnake) projectPathKey = 'project_path'
    else if (hasCamel) projectPathKey = 'projectPath'
    else projectPathKey = null
  }

  function stripProjectPathFromTools(tools) {
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
