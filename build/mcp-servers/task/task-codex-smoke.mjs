#!/usr/bin/env node
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Manual Codex CLI smoke checks for task workflow.
import assert from 'node:assert/strict'
import {execSync, spawn} from 'node:child_process'
import {cpSync, existsSync, mkdirSync, mkdtempSync, readdirSync, readFileSync, rmSync, statSync, writeFileSync} from 'node:fs'
import {homedir, tmpdir} from 'node:os'
import {dirname, join, resolve} from 'node:path'
import {fileURLToPath} from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const repoRoot = resolve(__dirname, '..', '..', '..', '..')

const rawArgs = process.argv.slice(2)
const args = new Set(rawArgs)
const keep = args.has('--keep')
const verbose = args.has('--verbose')

function getArgValue(flag) {
  const prefix = `${flag}=`
  const match = rawArgs.find(arg => arg.startsWith(prefix))
  if (match) return match.slice(prefix.length)
  const index = rawArgs.indexOf(flag)
  if (index >= 0 && rawArgs[index + 1]) return rawArgs[index + 1]
  return null
}

const codexBin = process.env.CODEX_BIN || 'codex'
const model = process.env.CODEX_MODEL || 'gpt-5.2'
const reasoningEffort = process.env.CODEX_REASONING_EFFORT || 'high'
const timeoutMs = Number.parseInt(process.env.CODEX_TIMEOUT || '600', 10) * 1000
const idleTimeoutMs = Number.parseInt(getArgValue('--idle-timeout') || process.env.CODEX_IDLE_TIMEOUT || '60', 10) * 1000
const cliCodexHome = getArgValue('--codex-home')
const sourceCodexHome = cliCodexHome || process.env.CODEX_HOME_SOURCE || process.env.CODEX_HOME || join(homedir(), '.codex')

function run(cmd, cwd) {
  return execSync(cmd, {cwd, encoding: 'utf-8', stdio: 'pipe'})
}

function copyCodexAuth(targetCodexHome) {
  if (!existsSync(sourceCodexHome)) return []
  const candidates = [
    'auth.json',
    'auth.yaml',
    'auth.yml',
    'credentials.json',
    'credentials.yaml',
    'credentials.yml'
  ]
  const dirCandidates = ['auth', 'credentials']
  const copied = []
  for (const filename of candidates) {
    const sourcePath = join(sourceCodexHome, filename)
    if (!existsSync(sourcePath)) continue
    cpSync(sourcePath, join(targetCodexHome, filename))
    copied.push(filename)
  }
  for (const dirname of dirCandidates) {
    const sourcePath = join(sourceCodexHome, dirname)
    if (!existsSync(sourcePath)) continue
    try {
      if (statSync(sourcePath).isDirectory()) {
        cpSync(sourcePath, join(targetCodexHome, dirname), {recursive: true})
        copied.push(`${dirname}/`)
      }
    } catch {
      // Ignore non-directory entries.
    }
  }
  return copied
}

function writeCodexConfig(targetCodexHome) {
  const configPath = join(targetCodexHome, 'config.toml')
  const mcpPath = resolve(repoRoot, 'community/build/mcp-servers/task/task-mcp.mjs')
  const tomlPath = mcpPath.replace(/\\/g, '\\\\')
  const lines = [
    `model = "${model}"`,
    'approval_policy = "never"',
    'cli_auth_credentials_store = "file"',
    '',
    '[mcp_servers.task]',
    'type = "stdio"',
    'command = "node"',
    `args = ["${tomlPath}"]`,
    ''
  ]
  writeFileSync(configPath, lines.join('\n'))
  return configPath
}

function listFilesRecursive(root) {
  if (!existsSync(root)) return []
  const results = []
  const stack = [root]
  while (stack.length > 0) {
    const dir = stack.pop()
    let entries
    try {
      entries = readdirSync(dir, {withFileTypes: true})
    } catch {
      continue
    }
    for (const entry of entries) {
      const fullPath = join(dir, entry.name)
      if (entry.isDirectory()) {
        stack.push(fullPath)
      } else {
        results.push(fullPath)
      }
    }
  }
  return results
}

function findLatestSessionLog(codexHome) {
  const sessionsDir = join(codexHome, 'sessions')
  const files = listFilesRecursive(sessionsDir).filter(file => file.endsWith('.jsonl'))
  let latest = null
  let latestMtime = 0
  for (const file of files) {
    let stats
    try {
      stats = statSync(file)
    } catch {
      continue
    }
    if (stats.mtimeMs > latestMtime) {
      latestMtime = stats.mtimeMs
      latest = file
    }
  }
  return latest
}

function analyzeSessionLog(codexHome) {
  const summary = {taskMcpCalls: 0, bdShellCommands: []}
  const logPath = findLatestSessionLog(codexHome)
  if (!logPath) return summary
  let text
  try {
    text = readFileSync(logPath, 'utf-8')
  } catch {
    return summary
  }
  for (const line of text.split('\n')) {
    if (!line.trim()) continue
    let event
    try {
      event = JSON.parse(line)
    } catch {
      continue
    }
    if (event.type === 'response_item' && event.payload?.type === 'function_call') {
      const name = event.payload.name
      if (typeof name === 'string' && name.startsWith('mcp__task__')) {
        summary.taskMcpCalls += 1
      }
      if (name === 'shell_command') {
        let commandText = null
        if (typeof event.payload.arguments === 'string') {
          try {
            const parsed = JSON.parse(event.payload.arguments)
            if (parsed && typeof parsed.command === 'string') {
              commandText = parsed.command
            }
          } catch {
            // ignore malformed args
          }
        }
        if (commandText && /\bbd\b/.test(commandText)) {
          summary.bdShellCommands.push(commandText)
        }
      }
    }
  }
  return summary
}

function createWorkDir() {
  const workDir = mkdtempSync(join(tmpdir(), 'codex-task-smoke-'))
  run('git init', workDir)
  run('bd init --stealth', workDir)

  const codexDir = join(workDir, '.codex')
  const skillsDir = join(codexDir, 'skills')
  mkdirSync(skillsDir, {recursive: true})
  cpSync(join(repoRoot, '.codex', 'skills', 'task'), join(skillsDir, 'task'), {recursive: true})
  const copied = copyCodexAuth(codexDir)
  const configPath = writeCodexConfig(codexDir)
  if (verbose) {
    console.log(`codex config source: ${sourceCodexHome}`)
    console.log(`codex config copied: ${copied.length > 0 ? copied.join(', ') : '(none)'}`)
    console.log(`codex config created: ${configPath}`)
  }
  if (copied.length === 0) {
    console.log(`warn - no Codex auth found in ${sourceCodexHome}`)
  }

  return workDir
}

function runCodex(prompt, workDir, {verboseLogs} = {}) {
  return new Promise((resolve) => {
    const cmdArgs = [
      'exec',
      '--json',
      '--model', model,
      '-c', `model_reasoning_effort="${reasoningEffort}"`,
      '--sandbox', 'workspace-write',
      '--disable', 'shell_snapshot',
      '--skip-git-repo-check',
      '--color', 'never',
      '--cd', workDir,
      prompt
    ]

    const env = {
      ...process.env,
      BD_NO_DAEMON: '1',
      CODEX_CI: '0',
      CODEX_SANDBOX_NETWORK_DISABLED: '0',
      CODEX_HOME: join(workDir, '.codex')
    }

    if (verboseLogs) {
      console.log(`codex cmd: ${codexBin} ${cmdArgs.join(' ')}`)
      console.log(`codex env CODEX_HOME=${env.CODEX_HOME}`)
      console.log(`codex env CODEX_CI=${env.CODEX_CI} CODEX_SANDBOX_NETWORK_DISABLED=${env.CODEX_SANDBOX_NETWORK_DISABLED}`)
    }

    const contentParts = []
    let stderr = ''
    let nonJsonLines = 0
    let timedOut = false
    let idleTimedOut = null
    let buffer = ''
    let lastOutputActivity = Date.now()
    let lastJsonActivity = Date.now()

    const proc = spawn(codexBin, cmdArgs, {cwd: workDir, env, stdio: ['ignore', 'pipe', 'pipe']})

    const idleInterval = setInterval(() => {
      const now = Date.now()
      if (!idleTimedOut && now - lastOutputActivity > idleTimeoutMs) {
        idleTimedOut = 'output'
        proc.kill('SIGKILL')
        return
      }
      if (!idleTimedOut && now - lastJsonActivity > idleTimeoutMs) {
        idleTimedOut = 'json'
        proc.kill('SIGKILL')
      }
    }, 1000)

    const timeoutId = setTimeout(() => {
      timedOut = true
      proc.kill('SIGKILL')
    }, timeoutMs)

    proc.stdout.on('data', (data) => {
      lastOutputActivity = Date.now()
      buffer += data.toString().replace(/\r/g, '\n')
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''
      for (const line of lines) {
        if (!line.trim()) continue
        try {
          const event = JSON.parse(line)
          lastJsonActivity = Date.now()
          if (event.type === 'item.completed' && event.item?.type === 'agent_message' && event.item.text) {
            contentParts.push(event.item.text)
          } else if (event.type === 'message' && event.message?.role === 'assistant') {
            for (const block of (event.message.content || [])) {
              if (block.type === 'text' && block.text) contentParts.push(block.text)
            }
          }
        } catch {
          nonJsonLines += 1
          if (verboseLogs && nonJsonLines <= 50) {
            console.log(`codex stdout: ${line}`)
          }
          stderr += `${line}\n`
        }
      }
    })

    proc.stderr.on('data', (data) => {
      lastOutputActivity = Date.now()
      const text = data.toString()
      stderr += text
      if (verboseLogs && text.trim()) {
        console.log(text.trimEnd())
      }
    })

    proc.on('error', (err) => {
      clearTimeout(timeoutId)
      clearInterval(idleInterval)
      const summary = analyzeSessionLog(env.CODEX_HOME)
      resolve({
        success: false,
        content: '',
        error: err.code === 'ENOENT'
          ? "Codex CLI not found. Ensure 'codex' is installed and in PATH."
          : `Error: ${err.message}`,
        ...summary
      })
    })

    proc.on('close', (code) => {
      clearTimeout(timeoutId)
      clearInterval(idleInterval)
      if (buffer.trim()) {
        const line = buffer.trim()
        try {
          const event = JSON.parse(line)
          lastJsonActivity = Date.now()
          if (event.type === 'item.completed' && event.item?.type === 'agent_message' && event.item.text) {
            contentParts.push(event.item.text)
          }
        } catch {
          nonJsonLines += 1
          if (verboseLogs && nonJsonLines <= 50) {
            console.log(`codex stdout: ${line}`)
          }
          stderr += `${line}\n`
        }
      }

      const summary = analyzeSessionLog(env.CODEX_HOME)

      if (timedOut) {
        resolve({success: false, content: '', error: `Codex timed out after ${timeoutMs / 1000}s`, ...summary})
        return
      }
      if (idleTimedOut === 'output') {
        resolve({success: false, content: '', error: `Codex idle timeout after ${idleTimeoutMs / 1000}s without output`, ...summary})
        return
      }
      if (idleTimedOut === 'json') {
        resolve({success: false, content: '', error: `Codex idle timeout after ${idleTimeoutMs / 1000}s without JSON output`, ...summary})
        return
      }
      if (code !== 0) {
        resolve({success: false, content: '', error: `Codex exited with code ${code}: ${stderr}`, ...summary})
        return
      }
      resolve({success: true, content: contentParts.join('\n'), ...summary})
    })
  })
}

async function scenarioCreateEpic(workDir) {
  const prompt = 'Track this in Beads: product-dsl generator emits a duplicate content module id; root cause unknown. Create an epic and start the investigation now.'
  const result = await runCodex(prompt, workDir, {verboseLogs: verbose})
  assert.ok(result.success, result.error)
  assert.ok(result.taskMcpCalls > 0, 'expected task MCP to be invoked')
  assert.equal(result.bdShellCommands.length, 0, 'did not expect bd CLI usage')
  if (verbose && result.content) console.log(result.content)

  const issues = JSON.parse(run('bd list --json', workDir))
  const epic = issues.find(issue => (issue.issue_type || issue.type) === 'epic')
  assert.ok(epic, 'expected an epic to be created')
  const children = issues.filter(issue => issue.parent === epic.id || issue.id.startsWith(`${epic.id}.`))
  assert.ok(children.length >= 1, 'expected at least one child under epic')
  assert.ok(children.some(child => child.status === 'in_progress'), 'expected an auto-started child')
}

async function scenarioResumeTask(workDir) {
  const taskId = run('bd create --title "Existing Task" --type task --silent', workDir).trim()
  const prompt = `Resume task ${taskId} in Beads.`
  const result = await runCodex(prompt, workDir, {verboseLogs: verbose})
  assert.ok(result.success, result.error)
  assert.ok(result.taskMcpCalls > 0, 'expected task MCP to be invoked')
  assert.equal(result.bdShellCommands.length, 0, 'did not expect bd CLI usage')
  if (verbose && result.content) console.log(result.content)

  const show = JSON.parse(run(`bd show ${taskId} --json`, workDir))
  const issue = show[0]
  assert.ok(issue, 'expected issue to exist')
  assert.equal(issue.status, 'in_progress')
}

async function runScenario(name, fn) {
  const workDir = createWorkDir()
  let success = false
  try {
    await fn(workDir)
    success = true
    console.log(`ok - ${name}`)
  } catch (err) {
    console.error(`fail - ${name}: ${err.message || err}`)
  } finally {
    if (!success || keep) {
      console.log(`work dir: ${workDir}`)
    } else {
      rmSync(workDir, {recursive: true, force: true})
    }
  }
  return success
}

async function main() {
  const results = []
  results.push(await runScenario('create epic + investigation child', scenarioCreateEpic))
  results.push(await runScenario('resume task by id', scenarioResumeTask))

  if (results.every(Boolean)) {
    return 0
  }
  return 1
}

main().then((code) => {
  process.exitCode = code
})
