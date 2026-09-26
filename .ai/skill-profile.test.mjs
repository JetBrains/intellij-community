// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {describe, it} from 'node:test'
import {deepEqual, equal, ok, rejects} from 'node:assert/strict'
import {mkdtempSync, rmSync, writeFileSync} from 'node:fs'
import {tmpdir} from 'node:os'
import {join} from 'node:path'
import {
  applyOverrides,
  collectSkillNames,
  loadProfiles,
  removeOverrides,
  resolveOffNames,
} from './skill-profile.mjs'

function withTempFile(content, run) {
  const dir = mkdtempSync(join(tmpdir(), 'skill-profile-'))
  try {
    const path = join(dir, 'profiles.json')
    writeFileSync(path, JSON.stringify(content), 'utf8')
    return run(path)
  }
  finally {
    rmSync(dir, {recursive: true, force: true})
  }
}

describe('the committed table', () => {
  it('names skills that exist', async () => {
    const table = await loadProfiles()
    const known = await collectSkillNames()
    const missing = Object.entries(table.areas)
      .flatMap(([area, names]) => names.filter(name => !known.has(name)).map(name => `${area}: ${name}`))
    deepEqual(missing, [], `skill-profiles.json names skills that are gone:\n${missing.join('\n')}`)
  })

  it('gives each profile a set of skills to hide', async () => {
    const table = await loadProfiles()
    const known = await collectSkillNames()
    for (const profile of Object.keys(table.profiles)) {
      const names = resolveOffNames(table, profile, known)
      ok(names.length > 0, `profile ${profile} hides no skill`)
    }
  })

  it('keeps a skill that no area names', async () => {
    const table = await loadProfiles()
    const known = await collectSkillNames()
    const grouped = new Set(Object.values(table.areas).flat())
    const minimal = new Set(resolveOffNames(table, 'minimal', known))
    for (const name of known) {
      if (!grouped.has(name)) {
        ok(!minimal.has(name), `${name} is in no area, so no profile may hide it`)
      }
    }
  })
})

describe('loadProfiles', () => {
  it('refuses a skill in two areas', async () => {
    await withTempFile(
      {areas: {one: ['air'], two: ['air']}, profiles: {}},
      async path => await rejects(loadProfiles(path), /is in area "one" and in area "two"/),
    )
  })

  it('refuses a profile that keeps an unknown area', async () => {
    await withTempFile(
      {areas: {one: ['air']}, profiles: {solo: {keeps: ['two']}}},
      async path => await rejects(loadProfiles(path), /keeps unknown area "two"/),
    )
  })
})

describe('resolveOffNames', () => {
  const table = {
    areas: {one: ['b-skill', 'a-skill'], two: ['c-skill']},
    profiles: {keepsOne: {keeps: ['one']}, keepsNone: {keeps: []}},
  }

  it('hides each area the profile does not keep', () => {
    deepEqual(resolveOffNames(table, 'keepsOne', new Set(['a-skill', 'b-skill', 'c-skill'])), ['c-skill'])
    deepEqual(resolveOffNames(table, 'keepsNone', new Set(['a-skill', 'b-skill', 'c-skill'])),
              ['a-skill', 'b-skill', 'c-skill'])
  })

  it('drops a name that is no longer a skill', () => {
    deepEqual(resolveOffNames(table, 'keepsNone', new Set(['a-skill'])), ['a-skill'])
  })

  it('refuses an unknown profile', () => {
    let message = ''
    try {
      resolveOffNames(table, 'ghost', new Set())
    }
    catch (error) {
      message = error.message
    }
    ok(message.includes('Unknown profile "ghost"'), message)
  })
})

describe('applyOverrides', () => {
  it('keeps the other keys of the settings file', () => {
    const settings = {permissions: {allow: ['Bash(ls:*)']}, hooks: {Stop: []}}
    const next = applyOverrides(settings, ['air'], 'off')
    deepEqual(next.permissions, {allow: ['Bash(ls:*)']})
    deepEqual(next.hooks, {Stop: []})
    equal(next.skillOverrides.air, 'off')
  })

  it('keeps an entry the developer wrote by hand', () => {
    const settings = {skillOverrides: {commits: 'name-only'}}
    const next = applyOverrides(settings, ['air'], 'off', [])
    deepEqual(next.skillOverrides, {air: 'off', commits: 'name-only'})
  })

  it('removes the entries of the profile it replaces', () => {
    const first = applyOverrides({}, ['air', 'compose'], 'off', [])
    const second = applyOverrides(first, ['dotnet-testing'], 'off', ['air', 'compose'])
    deepEqual(second.skillOverrides, {'dotnet-testing': 'off'})
  })

  it('does not change the settings object it reads', () => {
    const settings = {skillOverrides: {commits: 'off'}}
    applyOverrides(settings, ['air'], 'off')
    deepEqual(settings.skillOverrides, {commits: 'off'})
  })

  it('refuses an unknown state', () => {
    let message = ''
    try {
      applyOverrides({}, ['air'], 'hidden')
    }
    catch (error) {
      message = error.message
    }
    ok(message.includes('Unknown state "hidden"'), message)
  })
})

describe('removeOverrides', () => {
  it('removes the named entries only', () => {
    const settings = {skillOverrides: {air: 'off', commits: 'name-only'}, hooks: {}}
    const next = removeOverrides(settings, ['air'])
    deepEqual(next.skillOverrides, {commits: 'name-only'})
    deepEqual(next.hooks, {})
  })

  it('drops an empty skillOverrides key', () => {
    const next = removeOverrides({skillOverrides: {air: 'off'}}, ['air'])
    equal('skillOverrides' in next, false)
  })
})
