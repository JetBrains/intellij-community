// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {extractItems, requireString} from '../shared'
import type {SearchCapabilities, ToolArgs, UpstreamToolCaller} from '../types'
import {normalizeItems, normalizeLimit, resolveMoreFlag, serializeSearchResult} from './search-shared'
import {buildPathScope} from './search-scope'

export async function handleSearchSymbolTool(
  args: ToolArgs,
  projectPath: string,
  callUpstreamTool: UpstreamToolCaller,
  capabilities: SearchCapabilities
): Promise<string> {
  const query = requireString(args.q, 'q').trim()
  const limit = normalizeLimit(args.limit)
  const {normalizedPaths} = buildPathScope(projectPath, args.paths)

  if (capabilities.hasSearchSymbol) {
    const result = await callUpstreamTool('search_symbol', {
      q: query,
      ...(normalizedPaths ? {paths: normalizedPaths} : {}),
      limit
    })
    const items = normalizeItems(extractItems(result), projectPath, limit, true)
    const more = resolveMoreFlag(result, items.length, limit)
    return serializeSearchResult({items, more})
  }

  throw new Error('symbol search is not supported by this IDE version')
}
