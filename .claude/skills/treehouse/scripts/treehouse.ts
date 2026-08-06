#!/usr/bin/env bun

import {mkdir, readFile, rm, writeFile} from "node:fs/promises";
import {dirname, isAbsolute, join, relative, resolve, sep} from "node:path";
import {randomUUID} from "node:crypto";

const RECEIPT_RELATIVE_PATH = "out/treehouse/lease.json";

export interface SpawnResult {
  readonly exitCode: number;
  readonly stdout: string;
  readonly stderr: string;
}

export interface SpawnOptions {
  readonly interactive?: boolean;
}

export interface Runtime {
  readonly cwd: string;
  readonly env: Record<string, string | undefined>;
  readonly isTTY: boolean;
  now(): Date;
  uuid(): string;
  readTextFile(path: string): Promise<string>;
  writeTextFile(path: string, text: string): Promise<void>;
  removeFile(path: string): Promise<void>;
  spawn(command: string[], options?: SpawnOptions): Promise<SpawnResult>;
}

const defaultRuntime: Runtime = {
  cwd: process.cwd(),
  env: process.env,
  isTTY: Boolean(process.stdin.isTTY && process.stdout.isTTY),
  now: () => new Date(),
  uuid: () => randomUUID(),
  readTextFile: path => readFile(path, "utf8"),
  async writeTextFile(path, text) {
    await mkdir(dirname(path), {recursive: true});
    await writeFile(path, text, "utf8");
  },
  removeFile: path => rm(path, {force: true}),
  async spawn(command, options = {}) {
    try {
      const interactive = options.interactive === true;
      const child = Bun.spawn(command, {
        env: process.env,
        stdin: interactive ? "inherit" : "ignore",
        stdout: interactive ? "inherit" : "pipe",
        stderr: interactive ? "inherit" : "pipe",
      });
      if (interactive) {
        return {exitCode: await child.exited, stdout: "", stderr: ""};
      }
      const [exitCode, stdout, stderr] = await Promise.all([
        child.exited,
        new Response(child.stdout).text(),
        new Response(child.stderr).text(),
      ]);
      return {exitCode, stdout, stderr};
    }
    catch (error) {
      return {
        exitCode: 127,
        stdout: "",
        stderr: error instanceof Error ? error.message : String(error),
      };
    }
  },
};

export class CliError extends Error {
  constructor(
    message: string,
    readonly exitCode: number,
    readonly details?: unknown,
  ) {
    super(message);
    this.name = "CliError";
  }
}

export interface LeaseReceiptV1 {
  readonly schema_version: 1;
  readonly path: string;
  readonly lease_id: string;
  readonly lease_holder: string;
  readonly acquired_at: string;
}

export interface LeaseReceiptV2 {
  readonly schema_version: 2;
  readonly path: string;
  readonly lease_id: string;
  readonly lease_holder: string;
  readonly acquired_at: string;
  readonly source_head: string;
}

export type LeaseReceipt = LeaseReceiptV1 | LeaseReceiptV2;

export interface WorkspaceStatus {
  readonly name: string;
  readonly path: string;
  readonly status: string;
  readonly lease_id: string;
  readonly lease_holder: string;
  readonly leased_at: string | null;
  readonly processes: unknown[];
  readonly [key: string]: unknown;
}

function usage(): string {
  return `Usage:
  treehouse.ts read status
  treehouse.ts write acquire [--holder <session-id>]
  treehouse.ts write return --workspace <leased-path> [--confirm-preserved]

The CLI uses only Treehouse's leased get/status/return lifecycle. It never installs Treehouse,
creates an ad hoc Git worktree, or invokes --force.`;
}

function failUsage(message: string): never {
  throw new CliError(`${message}\n\n${usage()}`, 2);
}

function nativeFailure(operation: string, result: SpawnResult, details?: unknown): CliError {
  const unavailable = result.exitCode === 127;
  const message = unavailable
    ? "Treehouse is unavailable. Do not install it or fall back to another workspace mechanism."
    : `Treehouse ${operation} failed${result.stderr.trim() ? `: ${result.stderr.trim()}` : "."}`;
  return new CliError(message, unavailable ? 127 : result.exitCode || 1, {
    ...(typeof details === "object" && details !== null ? details : {}),
    native_exit_code: result.exitCode,
    stdout: result.stdout.trim(),
    stderr: result.stderr.trim(),
  });
}

function parseJson(text: string, description: string): unknown {
  try {
    return JSON.parse(text);
  }
  catch (error) {
    throw new CliError(`${description} returned malformed JSON.`, 1, {
      output: text.trim(),
      parse_error: error instanceof Error ? error.message : String(error),
    });
  }
}

function nonEmptyString(value: unknown, field: string, description: string): string {
  if (typeof value !== "string" || value.length === 0) {
    throw new CliError(`${description} is missing a non-empty ${field}.`, 1, {field, value});
  }
  return value;
}

function parseStatus(value: unknown): WorkspaceStatus[] {
  if (!Array.isArray(value)) throw new CliError("Treehouse status JSON must be an array.", 1);
  return value.map((item, index) => {
    if (typeof item !== "object" || item === null || Array.isArray(item)) {
      throw new CliError(`Treehouse status entry ${index} must be an object.`, 1);
    }
    const record = item as Record<string, unknown>;
    const leaseId = record.lease_id;
    const leaseHolder = record.lease_holder;
    if (typeof leaseId !== "string" || typeof leaseHolder !== "string" || !Array.isArray(record.processes)) {
      throw new CliError(`Treehouse status entry ${index} has an unsupported shape.`, 1, {entry: record});
    }
    return {
      ...record,
      name: nonEmptyString(record.name, "name", `Treehouse status entry ${index}`),
      path: nonEmptyString(record.path, "path", `Treehouse status entry ${index}`),
      status: nonEmptyString(record.status, "status", `Treehouse status entry ${index}`),
      lease_id: leaseId,
      lease_holder: leaseHolder,
      leased_at: typeof record.leased_at === "string" ? record.leased_at : null,
      processes: record.processes,
    };
  });
}

async function readStatus(runtime: Runtime): Promise<WorkspaceStatus[]> {
  const result = await runtime.spawn(["treehouse", "status", "--json"]);
  if (result.exitCode !== 0) throw nativeFailure("status", result);
  return parseStatus(parseJson(result.stdout, "Treehouse status"));
}

async function gitRoot(runtime: Runtime): Promise<string> {
  return gitRootAt(runtime, runtime.cwd, "current directory");
}

function gitCommand(path: string, ...args: string[]): string[] {
  return ["git", "-c", "core.fsmonitor=false", "-C", path, ...args];
}

async function gitRootAt(runtime: Runtime, path: string, description: string): Promise<string> {
  const result = await runtime.spawn(gitCommand(path, "rev-parse", "--show-toplevel"));
  if (result.exitCode !== 0) {
    throw new CliError(`The ${description} is not inside a Git workspace.`, 2, {
      path,
      stderr: result.stderr.trim(),
    });
  }
  return resolve(result.stdout.trim());
}

async function gitHead(runtime: Runtime, path: string, description: string): Promise<string> {
  const result = await runtime.spawn(gitCommand(path, "rev-parse", "--verify", "HEAD^{commit}"));
  if (result.exitCode !== 0) {
    throw new CliError(`The ${description} does not have a valid HEAD commit.`, 2, {
      path,
      stderr: result.stderr.trim(),
    });
  }
  return nonEmptyString(result.stdout.trim(), "HEAD", description);
}

async function gitCommonDir(runtime: Runtime, path: string, description: string): Promise<string> {
  const result = await runtime.spawn(gitCommand(path, "rev-parse", "--git-common-dir"));
  if (result.exitCode !== 0) {
    throw new CliError(`The ${description} Git common directory could not be resolved.`, 2, {
      path,
      stderr: result.stderr.trim(),
    });
  }
  return resolve(path, nonEmptyString(result.stdout.trim(), "Git common directory", description));
}

async function gitChanges(runtime: Runtime, path: string): Promise<string[]> {
  const result = await runtime.spawn(gitCommand(
    path, "status", "--porcelain=v1", "--untracked-files=all",
  ));
  if (result.exitCode !== 0) {
    throw new CliError("Git status failed.", result.exitCode || 1, {
      path,
      stderr: result.stderr.trim(),
    });
  }
  return result.stdout.trim().split("\n").filter(Boolean);
}

function pathContains(parent: string, child: string): boolean {
  const path = relative(resolve(parent), resolve(child));
  return path === "" || (path !== ".." && !path.startsWith(`..${sep}`) && !isAbsolute(path));
}

function parseAllocation(value: unknown): Pick<LeaseReceipt, "path" | "lease_id" | "lease_holder"> {
  if (typeof value !== "object" || value === null || Array.isArray(value)) {
    throw new CliError("Treehouse acquire JSON must be an object.", 1);
  }
  const record = value as Record<string, unknown>;
  return {
    path: resolve(nonEmptyString(record.path, "path", "Treehouse acquire result")),
    lease_id: nonEmptyString(record.lease_id, "lease_id", "Treehouse acquire result"),
    lease_holder: nonEmptyString(record.lease_holder, "lease_holder", "Treehouse acquire result"),
  };
}

function parseReceipt(value: unknown): LeaseReceipt {
  if (typeof value !== "object" || value === null || Array.isArray(value)) {
    throw new CliError("The Treehouse lease receipt must be an object.", 2);
  }
  const record = value as Record<string, unknown>;
  if (record.schema_version !== 1 && record.schema_version !== 2) {
    throw new CliError("The Treehouse lease receipt has an unsupported schema version.", 2, {
      schema_version: record.schema_version,
    });
  }
  const common = {
    path: resolve(nonEmptyString(record.path, "path", "Treehouse lease receipt")),
    lease_id: nonEmptyString(record.lease_id, "lease_id", "Treehouse lease receipt"),
    lease_holder: nonEmptyString(record.lease_holder, "lease_holder", "Treehouse lease receipt"),
    acquired_at: nonEmptyString(record.acquired_at, "acquired_at", "Treehouse lease receipt"),
  };
  if (record.schema_version === 1) return {schema_version: 1, ...common};
  return {
    schema_version: 2,
    ...common,
    source_head: nonEmptyString(record.source_head, "source_head", "Treehouse lease receipt"),
  };
}

function returnCommand(receipt: Pick<LeaseReceipt, "path" | "lease_id" | "lease_holder">): string[] {
  return [
    "treehouse", "return", receipt.path,
    "--if-lease-id", receipt.lease_id,
    "--if-lease-holder", receipt.lease_holder,
  ];
}

async function rollbackAcquire(runtime: Runtime, receipt: Pick<LeaseReceipt, "path" | "lease_id" | "lease_holder">): Promise<SpawnResult> {
  return runtime.spawn(returnCommand(receipt));
}

async function prepareAcquiredWorkspace(
  runtime: Runtime,
  allocation: Pick<LeaseReceipt, "path" | "lease_id" | "lease_holder">,
  sourceCommonDir: string,
  sourceHead: string,
): Promise<void> {
  const status = await readStatus(runtime);
  const live = status.find(item => resolve(item.path) === allocation.path);
  if (live === undefined || live.lease_id !== allocation.lease_id || live.lease_holder !== allocation.lease_holder) {
    throw new CliError("The acquired workspace does not match the live Treehouse lease.", 1, {
      allocation,
      live: live ?? null,
    });
  }

  const destinationRoot = await gitRootAt(runtime, allocation.path, "acquired workspace");
  if (destinationRoot !== allocation.path) {
    throw new CliError("The acquired path is not the root of its Git workspace.", 1, {
      acquired_path: allocation.path,
      git_root: destinationRoot,
    });
  }
  const destinationCommonDir = await gitCommonDir(runtime, destinationRoot, "acquired workspace");
  if (destinationCommonDir !== sourceCommonDir) {
    throw new CliError("The acquired workspace does not share the source Git repository.", 1, {
      source_git_common_dir: sourceCommonDir,
      destination_git_common_dir: destinationCommonDir,
    });
  }

  const initialChanges = await gitChanges(runtime, destinationRoot);
  if (initialChanges.length > 0) {
    throw new CliError("The acquired workspace is dirty before HEAD preparation.", 1, {
      path: destinationRoot,
      changes: initialChanges,
    });
  }

  if (await gitHead(runtime, destinationRoot, "acquired workspace") !== sourceHead) {
    const checkout = await runtime.spawn(gitCommand(destinationRoot, "checkout", "--detach", sourceHead));
    if (checkout.exitCode !== 0) {
      throw new CliError("The acquired workspace could not be detached at the source HEAD.", checkout.exitCode || 1, {
        path: destinationRoot,
        source_head: sourceHead,
        stdout: checkout.stdout.trim(),
        stderr: checkout.stderr.trim(),
      });
    }
  }

  const preparedHead = await gitHead(runtime, destinationRoot, "prepared workspace");
  const preparedChanges = await gitChanges(runtime, destinationRoot);
  if (preparedHead !== sourceHead || preparedChanges.length > 0) {
    throw new CliError("The acquired workspace failed exact-HEAD verification.", 1, {
      path: destinationRoot,
      source_head: sourceHead,
      prepared_head: preparedHead,
      changes: preparedChanges,
    });
  }
}

function holderFrom(runtime: Runtime, requested: string | undefined): string {
  const holder = requested ?? runtime.env.TREEHOUSE_LEASE_HOLDER ?? `agent-${runtime.uuid()}`;
  if (holder.trim().length === 0) failUsage("--holder must not be blank");
  return holder;
}

async function executeAcquire(runtime: Runtime, holderOption: string | undefined): Promise<unknown> {
  const root = await gitRoot(runtime);
  const sourceHead = await gitHead(runtime, root, "source workspace");
  const sourceCommonDir = await gitCommonDir(runtime, root, "source workspace");
  const status = await readStatus(runtime);
  const current = status.find(item => resolve(item.path) === root && item.lease_id.length > 0);
  if (current !== undefined) {
    throw new CliError("The current workspace already has an active Treehouse lease.", 2, {
      path: current.path,
      lease_id: current.lease_id,
      lease_holder: current.lease_holder,
    });
  }

  const holder = holderFrom(runtime, holderOption);
  const result = await runtime.spawn(["treehouse", "get", "--lease", "--json", "--lease-holder", holder]);
  if (result.exitCode !== 0) throw nativeFailure("acquire", result, {lease_holder: holder});

  let allocation: Pick<LeaseReceipt, "path" | "lease_id" | "lease_holder">;
  try {
    allocation = parseAllocation(parseJson(result.stdout, "Treehouse acquire"));
  }
  catch (error) {
    const matches = (await readStatus(runtime)).filter(item => item.lease_holder === holder && item.lease_id.length > 0);
    if (matches.length === 1) {
      const recovered = matches[0];
      const rollback = await rollbackAcquire(runtime, recovered);
      throw new CliError(
        rollback.exitCode === 0
          ? "Treehouse acquired a lease but returned unusable JSON; the recovered lease was returned."
          : "Treehouse acquired a lease but returned unusable JSON, and the recovered lease could not be returned; retain this lease identity.",
        1,
        {
          path: recovered.path,
          lease_id: recovered.lease_id,
          lease_holder: recovered.lease_holder,
          rollback_exit_code: rollback.exitCode,
          rollback_stderr: rollback.stderr.trim(),
          cause: error instanceof Error ? error.message : String(error),
        },
      );
    }
    throw error;
  }
  if (allocation.lease_holder !== holder) {
    const rollback = await rollbackAcquire(runtime, allocation);
    throw new CliError(
      rollback.exitCode === 0
        ? "Treehouse recorded a different lease holder; the lease was returned."
        : "Treehouse recorded a different lease holder and the lease could not be returned; retain this lease identity.",
      1,
      {
        ...allocation,
        requested_holder: holder,
        rollback_exit_code: rollback.exitCode,
        rollback_stderr: rollback.stderr.trim(),
      },
    );
  }

  const receipt: LeaseReceiptV2 = {
    schema_version: 2,
    ...allocation,
    acquired_at: runtime.now().toISOString(),
    source_head: sourceHead,
  };
  const receiptPath = join(receipt.path, RECEIPT_RELATIVE_PATH);
  try {
    await runtime.writeTextFile(receiptPath, `${JSON.stringify(receipt, null, 2)}\n`);
  }
  catch (error) {
    const rollback = await rollbackAcquire(runtime, receipt);
    throw new CliError(
      rollback.exitCode === 0
        ? "The lease receipt could not be written, so the acquired workspace was returned."
        : "The lease receipt could not be written and the acquired workspace could not be returned; retain this lease identity.",
      1,
      {
        ...receipt,
        receipt_path: receiptPath,
        write_error: error instanceof Error ? error.message : String(error),
        rollback_exit_code: rollback.exitCode,
        rollback_stderr: rollback.stderr.trim(),
      },
    );
  }
  try {
    await prepareAcquiredWorkspace(runtime, receipt, sourceCommonDir, sourceHead);
  }
  catch (error) {
    const rollback = await rollbackAcquire(runtime, receipt);
    let receiptRemoved = false;
    if (rollback.exitCode === 0) {
      try {
        await runtime.removeFile(receiptPath);
        receiptRemoved = true;
      }
      catch {
        // The lease is already returned; report the stale receipt instead of masking the preparation failure.
      }
    }
    throw new CliError(
      rollback.exitCode === 0
        ? "The acquired workspace could not be prepared at the source HEAD, so the lease was returned."
        : "The acquired workspace could not be prepared at the source HEAD and the lease could not be returned; retain this lease identity.",
      1,
      {
        ...receipt,
        receipt_path: receiptPath,
        receipt_removed: receiptRemoved,
        rollback_exit_code: rollback.exitCode,
        rollback_stderr: rollback.stderr.trim(),
        cause: error instanceof CliError
          ? {message: error.message, details: error.details}
          : {message: error instanceof Error ? error.message : String(error)},
      },
    );
  }
  return {...receipt, prepared_head: sourceHead, receipt_path: receiptPath};
}

async function executeReturn(runtime: Runtime, workspaceOption: string, confirmPreserved: boolean): Promise<unknown> {
  const workspace = resolve(runtime.cwd, workspaceOption);
  if (pathContains(workspace, runtime.cwd)) {
    throw new CliError("Return must be run from outside the leased workspace.", 2, {
      cwd: runtime.cwd,
      workspace,
    });
  }
  const receiptPath = join(workspace, RECEIPT_RELATIVE_PATH);
  let receipt: LeaseReceipt;
  try {
    receipt = parseReceipt(parseJson(await runtime.readTextFile(receiptPath), "Treehouse lease receipt"));
  }
  catch (error) {
    if (error instanceof CliError) throw error;
    throw new CliError("No Treehouse lease receipt exists in the requested workspace.", 2, {
      path: workspace,
      receipt_path: receiptPath,
    });
  }
  if (receipt.path !== workspace) {
    throw new CliError("The Treehouse lease receipt belongs to a different workspace.", 2, {
      requested_path: workspace,
      receipt_path: receipt.path,
      lease_id: receipt.lease_id,
      lease_holder: receipt.lease_holder,
    });
  }
  const root = await gitRootAt(runtime, workspace, "requested workspace");
  if (root !== workspace) {
    throw new CliError("The requested path is not the root of its Git workspace.", 2, {
      requested_path: workspace,
      git_root: root,
    });
  }

  const status = await readStatus(runtime);
  const live = status.find(item => resolve(item.path) === receipt.path);
  if (live === undefined || live.lease_id !== receipt.lease_id || live.lease_holder !== receipt.lease_holder) {
    throw new CliError("The local Treehouse receipt does not match the live lease; refusing to return it.", 2, {
      receipt,
      live: live ?? null,
    });
  }
  if (live.processes.length > 0) {
    throw new CliError("The Treehouse workspace still has live processes; stop them before returning it.", 2, {
      path: receipt.path,
      lease_id: receipt.lease_id,
      lease_holder: receipt.lease_holder,
      processes: live.processes,
    });
  }

  const changes = await gitChanges(runtime, receipt.path);
  const dirty = changes.length > 0;
  if (dirty && !confirmPreserved) {
    throw new CliError("The workspace is dirty. Preserve all intended work, then rerun with --confirm-preserved in a TTY.", 2, {
      ...receipt,
      changes,
    });
  }
  if (dirty && !runtime.isTTY) {
    throw new CliError("A dirty Treehouse workspace can be returned only from an interactive TTY.", 2, {
      ...receipt,
      changes,
    });
  }

  const result = await runtime.spawn(returnCommand(receipt), {interactive: dirty});
  if (result.exitCode !== 0) throw nativeFailure("return", result, receipt);

  let receiptRemoved = true;
  try {
    await runtime.removeFile(receiptPath);
  }
  catch {
    receiptRemoved = false;
  }
  return {
    path: receipt.path,
    lease_id: receipt.lease_id,
    lease_holder: receipt.lease_holder,
    returned: true,
    dirty,
    receipt_removed: receiptRemoved,
  };
}

function singleValue(tokens: string[], name: string): {value?: string; rest: string[]} {
  const rest: string[] = [];
  let value: string | undefined;
  for (let index = 0; index < tokens.length; index++) {
    if (tokens[index] !== name) {
      rest.push(tokens[index]);
      continue;
    }
    if (value !== undefined) failUsage(`${name} may be passed only once`);
    const next = tokens[++index];
    if (next == null || next.startsWith("--")) failUsage(`${name} requires a value`);
    value = next;
  }
  return {value, rest};
}

export async function execute(argv: string[], runtime: Runtime = defaultRuntime): Promise<unknown> {
  if (argv.includes("--help") || argv.includes("-h")) return {usage: usage()};
  const [access, action, ...tokens] = argv;
  if (access === "read" && action === "status") {
    if (tokens.length > 0) failUsage(`Unknown option: ${tokens[0]}`);
    return {workspaces: await readStatus(runtime)};
  }
  if (access === "write" && action === "acquire") {
    const parsed = singleValue(tokens, "--holder");
    if (parsed.rest.length > 0) failUsage(`Unknown option: ${parsed.rest[0]}`);
    return executeAcquire(runtime, parsed.value);
  }
  if (access === "write" && action === "return") {
    const parsed = singleValue(tokens, "--workspace");
    if (parsed.value === undefined) failUsage("--workspace is required");
    const confirmCount = parsed.rest.filter(token => token === "--confirm-preserved").length;
    const unknown = parsed.rest.find(token => token !== "--confirm-preserved");
    if (unknown !== undefined) failUsage(`Unknown option: ${unknown}`);
    if (confirmCount > 1) failUsage("--confirm-preserved may be passed only once");
    return executeReturn(runtime, parsed.value, confirmCount === 1);
  }
  failUsage("Expected 'read status', 'write acquire', or 'write return'");
}

export function errorResult(error: unknown): {exitCode: number; output: unknown} {
  if (error instanceof CliError) {
    return {
      exitCode: error.exitCode,
      output: {
        ok: false,
        error: error.message,
        ...(error.details === undefined ? {} : {details: error.details}),
      },
    };
  }
  return {
    exitCode: 1,
    output: {ok: false, error: error instanceof Error ? error.message : String(error)},
  };
}

async function main(): Promise<void> {
  try {
    console.log(JSON.stringify({ok: true, data: await execute(Bun.argv.slice(2))}, null, 2));
  }
  catch (error) {
    const result = errorResult(error);
    console.error(JSON.stringify(result.output, null, 2));
    process.exitCode = result.exitCode;
  }
}

if (import.meta.main) await main();
