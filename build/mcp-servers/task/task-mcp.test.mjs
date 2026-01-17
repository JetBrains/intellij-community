#!/usr/bin/env node
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import {after, before, beforeEach, describe, it} from 'node:test'
import assert from 'node:assert/strict'
import {execSync, spawn} from 'node:child_process'
import {mkdtempSync, rmSync} from 'node:fs'
import {tmpdir} from 'node:os'
import {dirname, join} from 'node:path'
import process from 'node:process'
import {fileURLToPath} from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))

process['env'].BD_NO_DAEMON = '1'

// MCP client for testing
class McpTestClient {
  constructor(serverProcess) {
    this.server = serverProcess
    this.responseQueue = []
    this.pendingResolvers = []
    this.requestId = 0

    // Buffer stdout for JSON-RPC responses
    let buffer = ''
    serverProcess.stdout.on('data', (data) => {
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
    this.server.stdin.write(JSON.stringify(request) + '\n')

    // Wait for response with matching id
    return new Promise((resolve) => {
      const checkQueue = () => {
        const idx = this.responseQueue.findIndex(r => r.id === id)
        if (idx >= 0) {
          resolve(this.responseQueue.splice(idx, 1)[0])
        } else {
          this.pendingResolvers.push((response) => {
            if (response.id === id) {
              resolve(response)
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
    this.server.stdin.end()
    this.server.kill()
  }
}

// Start MCP server in test directory
function startServer(testDir) {
  const server = spawn('node', [join(__dirname, 'task-mcp.mjs')], {
    cwd: testDir,  // Server runs in test dir so bd finds .beads/
    stdio: ['pipe', 'pipe', 'pipe']
  })
  return new McpTestClient(server)
}

describe('task MCP integration', {timeout: 30000}, () => {
  let testDir
  let client

  before(() => {
    // Create isolated test environment with git repo (required for bd)
    testDir = mkdtempSync(join(tmpdir(), 'task-mcp-test-'))
    execSync('git init', {cwd: testDir, stdio: 'pipe'})
    execSync('bd init --stealth', {cwd: testDir, stdio: 'pipe'})
  })

  after(() => {
    if (client) client.close()
    // Restore original cwd
    process.chdir(__dirname)
    rmSync(testDir, {recursive: true, force: true})
  })

  beforeEach(async () => {
    // Fresh client for each test
    if (client) client.close()
    // Close all in-progress issues to reset state
    try {
      const inProgress = JSON.parse(execSync('bd list --status in_progress --json', {cwd: testDir, encoding: 'utf-8'}))
      for (const issue of inProgress) {
        execSync(`bd close ${issue.id} --reason "test cleanup"`, {cwd: testDir, stdio: 'pipe'})
      }
    } catch (e) {
      // Ignore errors (e.g., no issues exist)
    }
    client = startServer(testDir)
    await client.initialize()
  })

  describe('task_status', () => {
    it('returns empty when no issues exist', async () => {
      const result = await client.callTool('task_status', {})
      assert.equal(result.kind, 'empty')
      assert.equal(result.next, 'provide_request')
      assert.equal(result.message, 'No ready tasks found.')
    })

    it('rejects user_request', async () => {
      const result = await client.callTool('task_status', {user_request: 'test task'})
      assert.equal(result.kind, 'error')
      assert.equal(result.message, 'task_status does not accept user_request; use task_start')
    })

    it('omits memory and notes/comments by default', async () => {
      const epic = await client.callTool('task_start', {user_request: 'Memory default'})

      const status = await client.callTool('task_status', {id: epic.issue.id})
      assert.equal(status.kind, 'issue')
      assert.equal(status.memory, undefined)
      assert.equal(status.issue.notes, undefined)
      assert.equal(status.issue.comments, undefined)
    })

    it('returns memory when memory_limit is set', async () => {
      const epic = await client.callTool('task_start', {user_request: 'Memory status'})

      await client.callTool('task_progress', {
        id: epic.issue.id,
        findings: ['F1', 'F2'],
        decisions: ['D1', 'D2']
      })

      const status = await client.callTool('task_status', {id: epic.issue.id, memory_limit: 1})
      assert.equal(status.kind, 'issue')
      assert.ok(status.memory)
      assert.deepEqual(status.memory.findings, ['F2'])
      assert.deepEqual(status.memory.decisions, ['D2'])
      assert.equal(status.memory.truncated, true)
      assert.deepEqual(status.memory.more, {findings: 1, decisions: 1})
      assert.equal(status.issue.notes, undefined)
      assert.equal(status.issue.comments, undefined)
    })

    it('supports meta view with truncation', async () => {
      const epic = await client.callTool('task_start', {user_request: 'Meta View'})

      const status = await client.callTool('task_status', {
        id: epic.issue.id,
        view: 'meta',
        meta_max_chars: 10
      })

      assert.equal(status.kind, 'issue')
      assert.equal(status.issue.type, 'epic')
      assert.equal(status.issue.issue_type, undefined)
      assert.ok(status.issue.description.endsWith('...'))
      assert.ok(status.issue.meta_truncated.includes('description'))
      assert.equal(status.issue.acceptance, 'PENDING')
      assert.equal(status.issue.design, 'PENDING')
    })
  })

  describe('task_start', () => {
    it('creates epic when user_request provided and no in-progress issues', async () => {
      const result = await client.callTool('task_start', {user_request: 'start task'})
      assert.equal(result.kind, 'issue')
      const issue = result.issue
      assert.ok(issue.id, 'should return id')
      assert.ok(issue.title === 'start task')
      assert.ok(issue.status === 'in_progress')
      assert.ok(issue.is_new === true)
      assert.equal(issue.type, 'epic')
      assert.equal(issue.issue_type, undefined)
    })

    it('omits memory by default', async () => {
      const result = await client.callTool('task_start', {user_request: 'No Memory'})
      assert.equal(result.kind, 'issue')
      assert.equal(result.memory, undefined)
    })

    it('returns issue for explicit id', async () => {
      const epic = await client.callTool('task_start', {user_request: 'Start by id'})

      const result = await client.callTool('task_start', {id: epic.issue.id})
      assert.equal(result.kind, 'issue')
      const issue = result.issue
      assert.ok(issue.id === epic.issue.id)
      assert.ok(issue.status === 'in_progress')
      assert.ok(issue.is_new === false)
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

      execSync(`bd close ${epic.issue.id} --reason "test"`, {cwd: testDir, stdio: 'pipe'})

      const resumed = await client.callTool('task_start', {id: epic.issue.id})
      assert.equal(resumed.kind, 'issue')
      const issue = resumed.issue
      assert.ok(issue.id === epic.issue.id)
      assert.ok(issue.is_new === false)
      assert.ok(issue.ready_children, 'should have ready_children')
      assert.equal(issue.ready_children.length, 2)
    })
  })

  describe('task_status with in-progress epic', () => {
    it('returns single in-progress issue', async () => {
      const epic = await client.callTool('task_start', {user_request: 'In Progress Epic'})

      const status = await client.callTool('task_status', {})
      assert.equal(status.kind, 'summary')
      assert.equal(status.issue.id, epic.issue.id)
      assert.equal(status.issue.status, 'in_progress')
    })

    it('shows Create sub-task option for single epic', async () => {
      await client.callTool('task_start', {user_request: 'Parent Epic'})

      const status = await client.callTool('task_start', {user_request: 'new task'})
      assert.equal(status.kind, 'need_user')

      const choices = status.choices
      const subTaskOption = choices.find(o => o.action === 'create_sub_task')
      assert.ok(subTaskOption, 'should have Create sub-task option')
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
      assert.equal(status.kind, 'summary')
      assert.equal(status.issue.id, epic.issue.id)
      assert.ok(status.issue.ready_children, 'should have ready_children')
      assert.equal(status.issue.ready_children.length, 2)
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

      assert.equal(result.kind, 'created')
      assert.equal(result.ids.length, 2)
      assert.equal(result.epic_id, epic.issue.id)
    })

    it('auto-starts a single child on create', async () => {
      const epic = await client.callTool('task_start', {user_request: 'Decompose Auto Start Test'})

      const result = await client.callTool('task_decompose', {
        epic_id: epic.issue.id,
        sub_issues: [
          {title: 'Sub 1', description: 'First', acceptance: 'Done', design: 'Simple'}
        ]
      })

      assert.equal(result.kind, 'created')
      assert.ok(result.started_child_id, 'should return started_child_id')
      assert.equal(result.started_child_id, result.ids[0])

      const inProgress = JSON.parse(execSync('bd list --status in_progress --json', {cwd: testDir, encoding: 'utf-8'}))
      assert.ok(inProgress.some(issue => issue.id === result.started_child_id))
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

      assert.equal(result.kind, 'progress')
      assert.deepEqual(result.memory.findings, ['Found pattern X'])
      assert.deepEqual(result.memory.decisions, ['Use approach Y'])

      const comments = JSON.parse(execSync(`bd comments ${epic.issue.id} --json`, {cwd: testDir, encoding: 'utf-8'}))
      assert.ok(comments.some(comment => comment.text === 'FINDING: Found pattern X'))
      assert.ok(comments.some(comment => comment.text === 'KEY DECISION: Use approach Y'))
    })

    it('omits memory when memory_limit is 0', async () => {
      const epic = await client.callTool('task_start', {user_request: 'Progress No Memory'})

      const result = await client.callTool('task_progress', {
        id: epic.issue.id,
        findings: ['F1'],
        decisions: ['D1'],
        memory_limit: 0
      })

      assert.equal(result.kind, 'progress')
      assert.equal(result.memory, undefined)
    })

    it('truncates memory when memory_limit is 1', async () => {
      const epic = await client.callTool('task_start', {user_request: 'Progress Trim Memory'})

      const result = await client.callTool('task_progress', {
        id: epic.issue.id,
        findings: ['F1', 'F2'],
        decisions: ['D1', 'D2'],
        memory_limit: 1
      })

      assert.equal(result.kind, 'progress')
      assert.ok(result.memory)
      assert.deepEqual(result.memory.findings, ['F2'])
      assert.deepEqual(result.memory.decisions, ['D2'])
      assert.equal(result.memory.truncated, true)
      assert.deepEqual(result.memory.more, {findings: 1, decisions: 1})
    })
  })

  describe('task_done', () => {
    it('closes epic directly', async () => {
      const epic = await client.callTool('task_start', {user_request: 'Close Test'})

      const result = await client.callTool('task_done', {
        id: epic.issue.id,
        reason: 'Completed successfully'
      })

      assert.equal(result.kind, 'closed')
      assert.equal(result.closed, epic.issue.id)
    })

    it('closes low-priority task directly', async () => {
      // Create task via bd directly (not epic, lower priority)
      const id = execSync(
        'bd create --title "Quick Task" --type task --priority P3 --silent',
        {cwd: testDir, encoding: 'utf-8'}
      ).trim()
      execSync(`bd update ${id} --status in_progress`, {cwd: testDir, stdio: 'pipe'})

      const result = await client.callTool('task_done', {
        id,
        reason: 'Done'
      })

      // P3 task should close without review
      assert.equal(result.kind, 'closed')
      assert.equal(result.closed, id)
    })
  })

  describe('task_reopen', () => {
    it('reopens closed issue with reason', async () => {
      const id = execSync(
        'bd create --title "Reopen Me" --type task --priority P3 --silent',
        {cwd: testDir, encoding: 'utf-8'}
      ).trim()
      execSync(`bd close ${id} --reason "done"`, {cwd: testDir, stdio: 'pipe'})

      const result = await client.callTool('task_reopen', {id, reason: 'Regression found'})
      assert.equal(result.kind, 'issue')
      assert.equal(result.issue.id, id)
      assert.equal(result.issue.status, 'open')
    })

    it('requires reason', async () => {
      const id = execSync(
        'bd create --title "Need Reason" --type task --priority P3 --silent',
        {cwd: testDir, encoding: 'utf-8'}
      ).trim()
      execSync(`bd close ${id} --reason "done"`, {cwd: testDir, stdio: 'pipe'})

      const result = await client.callTool('task_reopen', {id})
      assert.equal(result.kind, 'error')
      assert.ok(result.message.includes('reason required'))
    })
  })

  describe('sub-task option ambiguity', () => {
    it('hides sub-task option when multiple epics in progress', async () => {
      // Create first epic
      await client.callTool('task_start', {user_request: 'Epic 1'})

      // Create second epic (also becomes in_progress)
      const epic2Id = execSync(
        'bd create --title "Epic 2" --type epic --silent',
        {cwd: testDir, encoding: 'utf-8'}
      ).trim()
      execSync(`bd update ${epic2Id} --status in_progress`, {cwd: testDir, stdio: 'pipe'})

      const status = await client.callTool('task_start', {user_request: 'new task'})
      assert.equal(status.kind, 'need_user')

      const choices = status.choices
      const subTaskOption = choices.find(o => o.action === 'create_sub_task')
      assert.equal(subTaskOption, undefined, 'should NOT have Create sub-task option when ambiguous')
    })
  })
})
