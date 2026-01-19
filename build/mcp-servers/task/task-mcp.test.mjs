#!/usr/bin/env node
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/// <reference types="node" />
import {after, before, beforeEach, describe, it} from 'node:test'
import {deepStrictEqual, ok, strictEqual} from 'node:assert/strict'
import {execSync, spawn} from 'node:child_process'
import {mkdtempSync, rmSync} from 'node:fs'
import {tmpdir} from 'node:os'
import {dirname, join} from 'node:path'
import process from 'node:process'
import {fileURLToPath} from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const REQUEST_TIMEOUT_MS = 120_000
const SUITE_TIMEOUT_MS = 1_200_000
const TEST_ENV = {...process.env, BEADS_NO_DAEMON: 'true'}

function execTest(command, options = {}) {
  return execSync(command, {env: TEST_ENV, ...options})
}

// MCP client for testing
class McpTestClient {
  /** @type {import('node:child_process').ChildProcessWithoutNullStreams} */
  server

  /** @param {import('node:child_process').ChildProcessWithoutNullStreams} serverProcess */
  constructor(serverProcess) {
    /** @type {import('node:child_process').ChildProcessWithoutNullStreams} */
    this.server = serverProcess
    /** @type {Array<any>} */
    this.responseQueue = []
    /** @type {Array<(response: any) => void>} */
    this.pendingResolvers = []
    this.requestId = 0

    /** @type {import('node:child_process').ChildProcessWithoutNullStreams} */
    const server = this.server

    // Buffer stdout for JSON-RPC responses
    let buffer = ''
    // noinspection JSUnresolvedReference
    server.stdout.on('data', (data) => {
      buffer += data.toString()
      const lines = buffer.split('\n')
      buffer = lines.pop() // Keep incomplete line in buffer

      for (const line of lines) {
        if (!line.trim()) continue
        try {
          const response = JSON.parse(line)
          if (this.pendingResolvers.length > 0) {
            this.pendingResolvers.shift()(response)
          } else {
            this.responseQueue.push(response)
          }
        } catch (e) {
          // Ignore non-JSON output (stderr leaking to stdout, etc.)
        }
      }
    })
  }

  async send(method, params = {}) {
    const id = ++this.requestId
    const request = {jsonrpc: '2.0', id, method, params}
    /** @type {import('node:child_process').ChildProcessWithoutNullStreams} */
    const server = this.server
    // noinspection JSUnresolvedReference
    server.stdin.write(JSON.stringify(request) + '\n')

    // Wait for response with matching id
    return new Promise((resolve, reject) => {
      let settled = false
      const timeoutId = setTimeout(() => {
        if (settled) return
        settled = true
        reject(new Error(`Timed out waiting for response to ${method}`))
      }, REQUEST_TIMEOUT_MS)

      const finish = (response) => {
        if (settled) return
        settled = true
        clearTimeout(timeoutId)
        resolve(response)
      }

      const checkQueue = () => {
        const idx = this.responseQueue.findIndex(r => r.id === id)
        if (idx >= 0) {
          finish(this.responseQueue.splice(idx, 1)[0])
        } else {
          this.pendingResolvers.push((response) => {
            if (response.id === id) {
              finish(response)
            } else {
              this.responseQueue.push(response)
              checkQueue()
            }
          })
        }
      }
      checkQueue()
    })
  }

  async initialize() {
    return this.send('initialize', {
      protocolVersion: '2024-11-05',
      clientInfo: {name: 'test-client', version: '1.0.0'},
      capabilities: {}
    })
  }

  async callTool(name, args = {}) {
    const response = await this.send('tools/call', {name, arguments: args})
    if (response.error) {
      throw new Error(`MCP error: ${response.error.message}`)
    }
    // Parse tool result from content
    const content = response.result?.content?.[0]
    if (content?.type === 'text') {
      return JSON.parse(content.text)
    }
    return response.result
  }

  close() {
    /** @type {import('node:child_process').ChildProcessWithoutNullStreams} */
    const server = this.server
    // noinspection JSUnresolvedReference
    server.stdin.end()
    // noinspection JSUnresolvedReference
    server.kill()
  }
}

// Start MCP server in test directory
function startServer(testDir) {
  /** @type {import('node:child_process').ChildProcessWithoutNullStreams} */
  const server = spawn('node', [join(__dirname, 'task-mcp.mjs')], {
    cwd: testDir,  // Server runs in test dir so bd finds .beads/
    env: TEST_ENV,
    stdio: ['pipe', 'pipe', 'pipe']
  })
  return new McpTestClient(server)
}

function createInProgressChildren(epicId, testDir) {
  const child1 = execTest(
    `bd create --title "Child 1" --type task --parent ${epicId} --silent`,
    {cwd: testDir, encoding: 'utf-8'}
  ).trim()
  const child2 = execTest(
    `bd create --title "Child 2" --type task --parent ${epicId} --silent`,
    {cwd: testDir, encoding: 'utf-8'}
  ).trim()
  execTest(`bd update ${child1} --status in_progress`, {cwd: testDir, stdio: 'pipe'})
  execTest(`bd update ${child2} --status in_progress`, {cwd: testDir, stdio: 'pipe'})
  return {child1, child2}
}

describe('task MCP integration', {timeout: SUITE_TIMEOUT_MS}, () => {
  let testDir
  let client

  before(async () => {
    // Create isolated test environment with git repo (required for bd)
    testDir = mkdtempSync(join(tmpdir(), 'task-mcp-test-'))
    execTest('git init', {cwd: testDir, stdio: 'pipe'})
    execTest('bd init --stealth', {cwd: testDir, stdio: 'pipe'})
    client = startServer(testDir)
    await client.initialize()
  })

  after(() => {
    if (client) client.close()
    rmSync(testDir, {recursive: true, force: true})
  })

  beforeEach(() => {
    // Close all in-progress issues to reset state
    try {
      /** @type {{id: string}[]} */
      const inProgress = JSON.parse(execTest('bd list --status in_progress --json', {cwd: testDir, encoding: 'utf-8'}))
      for (const issue of inProgress) {
        execTest(`bd close ${issue.id} --reason "test cleanup"`, {cwd: testDir, stdio: 'pipe'})
      }
    } catch (e) {
      // Ignore errors (e.g., no issues exist)
    }
  })

  describe('task_status', () => {
    it('returns empty when no issues exist', async () => {
      const result = await client.callTool('task_status', {})
      strictEqual(result.kind, 'empty')
      strictEqual(result.next, 'await_user')
      strictEqual(result.message, 'No in-progress tasks found.')
    })

    it('rejects user_request', async () => {
      const result = await client.callTool('task_status', {user_request: 'test task'})
      strictEqual(result.kind, 'error')
      strictEqual(result.message, 'task_status does not accept user_request; use task_start')
    })

    it('omits memory and notes/comments by default', async () => {
      const epic = await client.callTool('task_start', {user_request: 'Memory default'})

      const status = await client.callTool('task_status', {id: epic.issue.id})
      strictEqual(status.kind, 'issue')
      strictEqual(status.memory, undefined)
      strictEqual(status.issue.notes, undefined)
      strictEqual(status.issue.comments, undefined)
    })

    it('returns memory when memory_limit is set', async () => {
      const epic = await client.callTool('task_start', {user_request: 'Memory status'})

      await client.callTool('task_progress', {
        id: epic.issue.id,
        findings: ['F1', 'F2'],
        decisions: ['D1', 'D2']
      })

      const status = await client.callTool('task_status', {id: epic.issue.id, memory_limit: 1})
      strictEqual(status.kind, 'issue')
      ok(status.memory)
      deepStrictEqual(status.memory.findings, ['F2'])
      deepStrictEqual(status.memory.decisions, ['D2'])
      strictEqual(status.memory.truncated, true)
      deepStrictEqual(status.memory.more, {findings: 1, decisions: 1})
      strictEqual(status.issue.notes, undefined)
      strictEqual(status.issue.comments, undefined)
    })

    it('supports meta view with truncation', async () => {
      const epic = await client.callTool('task_start', {user_request: 'Meta View'})

      const status = await client.callTool('task_status', {
        id: epic.issue.id,
        view: 'meta',
        meta_max_chars: 10
      })

      strictEqual(status.kind, 'issue')
      strictEqual(status.issue.type, 'epic')
      strictEqual(status.issue.issue_type, undefined)
      ok(status.issue.description.endsWith('...'))
      ok(status.issue.meta_truncated.includes('description'))
      strictEqual(status.issue.acceptance, 'PENDING')
      strictEqual(status.issue.design, 'PENDING')
    })

    it('includes children for epic status view', async () => {
      const epic = await client.callTool('task_start', {user_request: 'Epic with children status'})

      await client.callTool('task_decompose', {
        epic_id: epic.issue.id,
        sub_issues: [
          {title: 'Sub 1', description: 'First', acceptance: 'Done', design: 'Simple'},
          {title: 'Sub 2', description: 'Second', acceptance: 'Done', design: 'Simple'}
        ]
      })

      const status = await client.callTool('task_status', {id: epic.issue.id})
      strictEqual(status.kind, 'issue')
      ok(Array.isArray(status.issue.children))
      strictEqual(status.issue.children.length, 2)
      const titles = status.issue.children.map(child => child.title).sort()
      deepStrictEqual(titles, ['Sub 1', 'Sub 2'])
      ok(status.issue.children[0].id)
      ok(status.issue.children[0].status)
    })
  })

  describe('task_start', () => {
    it('creates epic when user_request provided and no in-progress issues', async () => {
      const result = await client.callTool('task_start', {user_request: 'start task'})
      strictEqual(result.kind, 'issue')
      const issue = result.issue
      ok(issue.id, 'should return id')
      ok(issue.title === 'start task')
      ok(issue.status === 'in_progress')
      ok(issue.is_new === true)
      strictEqual(issue.type, 'epic')
      strictEqual(issue.issue_type, undefined)
    })

    it('uses provided meta when creating epic', async () => {
      const result = await client.callTool('task_start', {
        user_request: 'Meta Epic',
        description: 'Custom description',
        design: 'Custom design',
        acceptance: 'Custom acceptance',
        view: 'meta'
      })

      strictEqual(result.kind, 'issue')
      strictEqual(result.issue.description, 'Custom description')
      strictEqual(result.issue.design, 'Custom design')
      strictEqual(result.issue.acceptance, 'Custom acceptance')
    })

    it('omits memory by default', async () => {
      const result = await client.callTool('task_start', {user_request: 'No Memory'})
      strictEqual(result.kind, 'issue')
      strictEqual(result.memory, undefined)
    })

    it('returns issue for explicit id', async () => {
      const epic = await client.callTool('task_start', {user_request: 'Start by id'})

      const result = await client.callTool('task_start', {id: epic.issue.id})
      strictEqual(result.kind, 'issue')
      const issue = result.issue
      ok(issue.id === epic.issue.id)
      ok(issue.status === 'in_progress')
      ok(issue.is_new === false)
    })

    it('creates epic even when in-progress issues exist', async () => {
      await client.callTool('task_start', {user_request: 'Existing task'})

      const result = await client.callTool('task_start', {user_request: 'New epic'})
      strictEqual(result.kind, 'issue')
      strictEqual(result.issue.is_new, true)
      strictEqual(result.issue.title, 'New epic')
    })

    it('rejects calls without id or user_request', async () => {
      const result = await client.callTool('task_start', {})
      strictEqual(result.kind, 'error')
      strictEqual(result.message, 'task_start requires id or user_request')
    })

    it('resumes epic with ready_children', async () => {
      const epic = await client.callTool('task_start', {user_request: 'Resume with Children'})

      await client.callTool('task_decompose', {
        epic_id: epic.issue.id,
        sub_issues: [
          {title: 'Child 1', description: 'First', acceptance: 'Done', design: 'Simple'},
          {title: 'Child 2', description: 'Second', acceptance: 'Done', design: 'Simple'}
        ]
      })

      execTest(`bd close ${epic.issue.id} --reason "test"`, {cwd: testDir, stdio: 'pipe'})

      const resumed = await client.callTool('task_start', {id: epic.issue.id})
      strictEqual(resumed.kind, 'issue')
      const issue = resumed.issue
      ok(issue.id === epic.issue.id)
      ok(issue.is_new === false)
      ok(issue.ready_children, 'should have ready_children')
      strictEqual(issue.ready_children.length, 2)
    })
  })


  describe('task_status with in-progress epic', () => {
    it('returns single in-progress issue', async () => {
      const epic = await client.callTool('task_start', {user_request: 'In Progress Epic'})

      const status = await client.callTool('task_status', {})
      strictEqual(status.kind, 'summary')
      ok(Array.isArray(status.issues))
      strictEqual(status.issues.length, 1)
      strictEqual(status.issues[0].id, epic.issue.id)
      strictEqual(status.issues[0].status, 'in_progress')
    })

    it('returns summary list when tasks share a single epic', async () => {
      const epic = await client.callTool('task_start', {user_request: 'Parent Epic'})

      createInProgressChildren(epic.issue.id, testDir)

      const status = await client.callTool('task_status', {})
      strictEqual(status.kind, 'summary')
      ok(Array.isArray(status.issues))
      ok(status.issues.length >= 3)
      ok(status.issues.some(issue => issue.id === epic.issue.id))
    })

    it('returns ready_children for epic with decomposed sub-issues', async () => {
      const epic = await client.callTool('task_start', {user_request: 'Epic with children'})

      await client.callTool('task_decompose', {
        epic_id: epic.issue.id,
        sub_issues: [
          {title: 'Sub 1', description: 'First', acceptance: 'Done', design: 'Simple'},
          {title: 'Sub 2', description: 'Second', acceptance: 'Done', design: 'Simple'}
        ]
      })

      const status = await client.callTool('task_status', {})
      strictEqual(status.kind, 'summary')
      ok(Array.isArray(status.issues))
      const epicSummary = status.issues.find(issue => issue.id === epic.issue.id)
      ok(epicSummary.ready_children, 'should have ready_children')
      strictEqual(epicSummary.ready_children.length, 2)
    })
  })

  describe('task_decompose', () => {
    it('creates sub-issues under epic', async () => {
      const epic = await client.callTool('task_start', {user_request: 'Decompose Test'})

      const result = await client.callTool('task_decompose', {
        epic_id: epic.issue.id,
        sub_issues: [
          {title: 'Sub 1', description: 'First', acceptance: 'Done', design: 'Simple'},
          {title: 'Sub 2', description: 'Second', acceptance: 'Done', design: 'Simple', depends_on: [0]}
        ]
      })

      strictEqual(result.kind, 'created')
      strictEqual(result.next, 'continue')
      strictEqual(result.ids.length, 2)
      strictEqual(result.epic_id, epic.issue.id)
    })

    it('rejects sub-issues missing meta', async () => {
      const epic = await client.callTool('task_start', {user_request: 'Decompose Missing Meta'})

      const result = await client.callTool('task_decompose', {
        epic_id: epic.issue.id,
        sub_issues: [
          {title: 'Sub 1', description: 'First', acceptance: 'Done'}
        ]
      })

      strictEqual(result.kind, 'error')
      ok(result.message.includes('missing required fields'))
    })

    it('auto-starts a single child on create', async () => {
      const epic = await client.callTool('task_start', {user_request: 'Decompose Auto Start Test'})

      const result = await client.callTool('task_decompose', {
        epic_id: epic.issue.id,
        sub_issues: [
          {title: 'Sub 1', description: 'First', acceptance: 'Done', design: 'Simple'}
        ]
      })

      strictEqual(result.kind, 'created')
      strictEqual(result.next, 'continue')
      ok(result.started_child_id, 'should return started_child_id')
      strictEqual(result.started_child_id, result.ids[0])

      const inProgress = JSON.parse(execTest('bd list --status in_progress --json', {cwd: testDir, encoding: 'utf-8'}))
      ok(inProgress.some(issue => issue.id === result.started_child_id))
    })

    it('accepts issue id dependencies for incremental graph updates', async () => {
      const epic = await client.callTool('task_start', {user_request: 'Decompose Id Dependencies'})
      const dep = await client.callTool('task_create', {
        title: 'Dependency Task',
        description: 'Existing dependency',
        design: 'Simple',
        acceptance: 'Done',
        parent: epic.issue.id
      })

      const result = await client.callTool('task_decompose', {
        epic_id: epic.issue.id,
        sub_issues: [
          {title: 'Sub 1', description: 'First', acceptance: 'Done', design: 'Simple', depends_on: [dep.id]}
        ]
      })

      const created = JSON.parse(execTest(`bd show ${result.ids[0]} --json`, {cwd: testDir, encoding: 'utf-8'}))[0]
      ok(created.dependencies.some(depEntry => depEntry.id === dep.id))
    })

    it('accepts dep_type for sub-issue dependencies', async () => {
      const epic = await client.callTool('task_start', {user_request: 'Decompose Dep Type'})
      const dep = await client.callTool('task_create', {
        title: 'Dep Type Task',
        description: 'Existing dependency',
        design: 'Simple',
        acceptance: 'Done',
        parent: epic.issue.id
      })

      const result = await client.callTool('task_decompose', {
        epic_id: epic.issue.id,
        sub_issues: [
          {
            title: 'Sub 1',
            description: 'First',
            acceptance: 'Done',
            design: 'Simple',
            depends_on: [dep.id],
            dep_type: 'blocks'
          }
        ]
      })

      const created = JSON.parse(execTest(`bd show ${result.ids[0]} --json`, {cwd: testDir, encoding: 'utf-8'}))[0]
      const dependency = created.dependencies.find(depEntry => depEntry.id === dep.id)
      ok(dependency)
      strictEqual(dependency['dependency_type'], 'blocks')
    })
  })

  describe('task_create', () => {
    it('requires description/design/acceptance', async () => {
      const result = await client.callTool('task_create', {title: 'Incomplete Task'})
      strictEqual(result.kind, 'error')
      ok(result.message.includes('Missing required fields'))
    })

    it('creates a task with meta', async () => {
      const result = await client.callTool('task_create', {
        title: 'Complete Task',
        description: 'Do the thing',
        design: 'Straightforward steps',
        acceptance: 'Verify behavior X'
      })

      strictEqual(result.kind, 'created')
      strictEqual(result.next, 'continue')
      ok(result.id, 'should return id')
    })

    it('accepts depends_on arrays', async () => {
      const dep1 = await client.callTool('task_create', {
        title: 'Dep 1',
        description: 'First dep',
        design: 'Simple',
        acceptance: 'Done'
      })
      const dep2 = await client.callTool('task_create', {
        title: 'Dep 2',
        description: 'Second dep',
        design: 'Simple',
        acceptance: 'Done'
      })

      const result = await client.callTool('task_create', {
        title: 'Task With Deps',
        description: 'Depends on others',
        design: 'Simple',
        acceptance: 'Done',
        depends_on: [dep1.id, dep2.id]
      })

      const created = JSON.parse(execTest(`bd show ${result.id} --json`, {cwd: testDir, encoding: 'utf-8'}))[0]
      const depIds = created.dependencies.map(depEntry => depEntry.id).sort()
      deepStrictEqual(depIds, [dep1.id, dep2.id].sort())
    })
  })

  describe('task_link', () => {
    it('adds dependencies between existing issues', async () => {
      const dep = await client.callTool('task_create', {
        title: 'Link Dep',
        description: 'Dependency',
        design: 'Simple',
        acceptance: 'Done'
      })
      const target = await client.callTool('task_create', {
        title: 'Link Target',
        description: 'Target',
        design: 'Simple',
        acceptance: 'Done'
      })

      const result = await client.callTool('task_link', {id: target.id, depends_on: [dep.id]})
      strictEqual(result.kind, 'updated')
      strictEqual(result.id, target.id)
      deepStrictEqual(result.added_depends_on, [dep.id])

      const linked = JSON.parse(execTest(`bd show ${target.id} --json`, {cwd: testDir, encoding: 'utf-8'}))[0]
      ok(linked.dependencies.some(depEntry => depEntry.id === dep.id))
    })

    it('rejects empty depends_on', async () => {
      const target = await client.callTool('task_create', {
        title: 'Link Target Empty',
        description: 'Target',
        design: 'Simple',
        acceptance: 'Done'
      })

      const result = await client.callTool('task_link', {id: target.id, depends_on: []})
      strictEqual(result.kind, 'error')
      ok(result.message.includes('depends_on'))
    })
  })

  describe('task_update_meta', () => {
    it('requires at least one field', async () => {
      const epic = await client.callTool('task_start', {user_request: 'Meta Update Required'})

      const result = await client.callTool('task_update_meta', {id: epic.issue.id})
      strictEqual(result.kind, 'error')
      ok(result.message.includes('At least one'))
    })

    it('updates description/design/acceptance', async () => {
      const epic = await client.callTool('task_start', {user_request: 'Meta Update'})

      const updated = await client.callTool('task_update_meta', {
        id: epic.issue.id,
        description: 'Updated description',
        design: 'Updated design',
        acceptance: 'Updated acceptance',
        view: 'meta'
      })

      strictEqual(updated.kind, 'issue')
      strictEqual(updated.issue.description, 'Updated description')
      strictEqual(updated.issue.design, 'Updated design')
      strictEqual(updated.issue.acceptance, 'Updated acceptance')
    })
  })

  describe('task_progress', () => {
    it('adds findings and decisions', async () => {
      const epic = await client.callTool('task_start', {user_request: 'Progress Test'})

      const result = await client.callTool('task_progress', {
        id: epic.issue.id,
        findings: ['Found pattern X'],
        decisions: ['Use approach Y'],
        memory_limit: 10
      })

      strictEqual(result.kind, 'progress')
      deepStrictEqual(result.memory.findings, ['Found pattern X'])
      deepStrictEqual(result.memory.decisions, ['Use approach Y'])

      const comments = JSON.parse(execTest(`bd comments ${epic.issue.id} --json`, {cwd: testDir, encoding: 'utf-8'}))
      ok(comments.some(comment => comment.text === 'FINDING: Found pattern X'))
      ok(comments.some(comment => comment.text === 'KEY DECISION: Use approach Y'))
    })

    it('omits memory when memory_limit is 0', async () => {
      const epic = await client.callTool('task_start', {user_request: 'Progress No Memory'})

      const result = await client.callTool('task_progress', {
        id: epic.issue.id,
        findings: ['F1'],
        decisions: ['D1'],
        memory_limit: 0
      })

      strictEqual(result.kind, 'progress')
      strictEqual(result.memory, undefined)
    })

    it('truncates memory when memory_limit is 1', async () => {
      const epic = await client.callTool('task_start', {user_request: 'Progress Trim Memory'})

      const result = await client.callTool('task_progress', {
        id: epic.issue.id,
        findings: ['F1', 'F2'],
        decisions: ['D1', 'D2'],
        memory_limit: 1
      })

      strictEqual(result.kind, 'progress')
      ok(result.memory)
      deepStrictEqual(result.memory.findings, ['F2'])
      deepStrictEqual(result.memory.decisions, ['D2'])
      strictEqual(result.memory.truncated, true)
      deepStrictEqual(result.memory.more, {findings: 1, decisions: 1})
    })
  })

  describe('task_done', () => {
    it('closes epic directly', async () => {
      const epic = await client.callTool('task_start', {user_request: 'Close Test'})

      const result = await client.callTool('task_done', {
        id: epic.issue.id,
        reason: 'Completed successfully'
      })

      strictEqual(result.kind, 'closed')
      strictEqual(result.closed, epic.issue.id)
    })

    it('auto-closes epic when all children are closed', async () => {
      const epic = await client.callTool('task_start', {user_request: 'Auto-close Epic'})
      const decompose = await client.callTool('task_decompose', {
        epic_id: epic.issue.id,
        sub_issues: [
          {title: 'Child 1', description: 'Child one', design: 'Simple', acceptance: 'Done'},
          {title: 'Child 2', description: 'Child two', design: 'Simple', acceptance: 'Done'}
        ]
      })

      await client.callTool('task_done', {id: decompose.ids[0], reason: 'Done'})
      let epicState = JSON.parse(execTest(`bd show ${epic.issue.id} --json`, {cwd: testDir, encoding: 'utf-8'}))[0]
      ok(epicState.status !== 'closed')

      await client.callTool('task_done', {id: decompose.ids[1], reason: 'Done'})
      epicState = JSON.parse(execTest(`bd show ${epic.issue.id} --json`, {cwd: testDir, encoding: 'utf-8'}))[0]
      strictEqual(epicState.status, 'closed')
      strictEqual(epicState['close_reason'], 'Auto-closed: all child issues closed')
    })

    it('does not auto-close pinned epics', async () => {
      const epic = await client.callTool('task_start', {user_request: 'Pinned Epic'})
      const decompose = await client.callTool('task_decompose', {
        epic_id: epic.issue.id,
        sub_issues: [
          {title: 'Child 1', description: 'Child one', design: 'Simple', acceptance: 'Done'}
        ]
      })

      execTest(`bd update ${epic.issue.id} --status pinned`, {cwd: testDir, stdio: 'pipe'})

      await client.callTool('task_done', {id: decompose.ids[0], reason: 'Done'})
      const epicState = JSON.parse(execTest(`bd show ${epic.issue.id} --json`, {cwd: testDir, encoding: 'utf-8'}))[0]
      ok(['pinned', 'hooked'].includes(epicState.status))
    })

    it('closes low-priority task directly', async () => {
      // Create task via bd directly (not epic, lower priority)
      const id = execTest(
        'bd create --title "Quick Task" --type task --priority P3 --silent',
        {cwd: testDir, encoding: 'utf-8'}
      ).trim()
      execTest(`bd update ${id} --status in_progress`, {cwd: testDir, stdio: 'pipe'})

      const result = await client.callTool('task_done', {
        id,
        reason: 'Done'
      })

      // P3 task should close without review
      strictEqual(result.kind, 'closed')
      strictEqual(result.closed, id)
    })
  })

  describe('task_reopen', () => {
    it('reopens closed issue with reason', async () => {
      const id = execTest(
        'bd create --title "Reopen Me" --type task --priority P3 --silent',
        {cwd: testDir, encoding: 'utf-8'}
      ).trim()
      execTest(`bd close ${id} --reason "done"`, {cwd: testDir, stdio: 'pipe'})

      const result = await client.callTool('task_reopen', {id, reason: 'Regression found'})
      strictEqual(result.kind, 'issue')
      strictEqual(result.issue.id, id)
      strictEqual(result.issue.status, 'open')
    })

    it('requires reason', async () => {
      const id = execTest(
        'bd create --title "Need Reason" --type task --priority P3 --silent',
        {cwd: testDir, encoding: 'utf-8'}
      ).trim()
      execTest(`bd close ${id} --reason "done"`, {cwd: testDir, stdio: 'pipe'})

      const result = await client.callTool('task_reopen', {id})
      strictEqual(result.kind, 'error')
      ok(result.message.includes('reason required'))
    })
  })

})
