import {describe, expect, test} from "bun:test";
import {
  CliError,
  execute,
  type Runtime,
  type SpawnOptions,
  type SpawnResult,
} from "./treehouse";

const ROOT = "/repo";
const WORKSPACE = "/treehouse/1/repo";
const RECEIPT_PATH = `${WORKSPACE}/out/treehouse/lease.json`;
const COMMON_DIR = "/repo/.git";
const SOURCE_HEAD = "1111111111111111111111111111111111111111";
const NATIVE_HEAD = "2222222222222222222222222222222222222222";

const AVAILABLE = {
  name: "1",
  path: WORKSPACE,
  status: "available",
  lease_id: "",
  lease_holder: "",
  leased_at: null,
  processes: [],
};

const LEASED = {
  ...AVAILABLE,
  status: "leased",
  lease_id: "lease-1",
  lease_holder: "agent-session",
  leased_at: "2026-08-06T10:00:00Z",
};

function result(stdout = "", exitCode = 0, stderr = ""): SpawnResult {
  return {exitCode, stdout, stderr};
}

function jsonResult(value: unknown): SpawnResult {
  return result(JSON.stringify(value));
}

function receipt(overrides: Record<string, unknown> = {}): string {
  return JSON.stringify({
    schema_version: 1,
    path: WORKSPACE,
    lease_id: "lease-1",
    lease_holder: "agent-session",
    acquired_at: "2026-08-06T10:00:00.000Z",
    ...overrides,
  });
}

function receiptV2(overrides: Record<string, unknown> = {}): string {
  return receipt({schema_version: 2, source_head: SOURCE_HEAD, ...overrides});
}

class FakeRuntime implements Runtime {
  cwd = ROOT;
  env: Record<string, string | undefined> = {};
  isTTY = false;
  readonly spawned: Array<{command: string[]; options?: SpawnOptions}> = [];
  readonly responses: SpawnResult[] = [];
  readonly files = new Map<string, string>();
  readonly removed: string[] = [];
  writeError: Error | undefined;
  uuidValue = "generated-uuid";

  now(): Date {
    return new Date("2026-08-06T10:00:00.000Z");
  }

  uuid(): string {
    return this.uuidValue;
  }

  async readTextFile(path: string): Promise<string> {
    const value = this.files.get(path);
    if (value === undefined) throw new Error(`Missing file: ${path}`);
    return value;
  }

  async writeTextFile(path: string, text: string): Promise<void> {
    if (this.writeError !== undefined) throw this.writeError;
    this.files.set(path, text);
  }

  async removeFile(path: string): Promise<void> {
    this.removed.push(path);
    this.files.delete(path);
  }

  async spawn(command: string[], options?: SpawnOptions): Promise<SpawnResult> {
    this.spawned.push({command, options});
    const response = this.responses.shift();
    if (response === undefined) throw new Error(`Unexpected spawn: ${command.join(" ")}`);
    return response;
  }
}

async function failure(action: () => Promise<unknown>): Promise<CliError> {
  try {
    await action();
  }
  catch (error) {
    expect(error).toBeInstanceOf(CliError);
    return error as CliError;
  }
  throw new Error("Expected the command to fail");
}

function prepareAcquireStart(runtime: FakeRuntime, allocation = LEASED): void {
  runtime.responses.push(
    result(`${ROOT}\n`),
    result(`${SOURCE_HEAD}\n`),
    result(`${COMMON_DIR}\n`),
    jsonResult([AVAILABLE]),
    jsonResult(allocation),
  );
}

function prepareWorkspace(runtime: FakeRuntime, allocation = LEASED, destinationHead = SOURCE_HEAD): void {
  runtime.responses.push(
    jsonResult([allocation]),
    result(`${WORKSPACE}\n`),
    result(`${COMMON_DIR}\n`),
    result(),
    result(`${destinationHead}\n`),
  );
  if (destinationHead !== SOURCE_HEAD) runtime.responses.push(result());
  runtime.responses.push(result(`${SOURCE_HEAD}\n`), result());
}

function prepareAcquire(runtime: FakeRuntime, allocation = LEASED, destinationHead = SOURCE_HEAD): void {
  prepareAcquireStart(runtime, allocation);
  prepareWorkspace(runtime, allocation, destinationHead);
}

function prepareReturn(runtime: FakeRuntime, gitStatus = ""): void {
  runtime.files.set(RECEIPT_PATH, receipt());
  runtime.responses.push(result(`${WORKSPACE}\n`), jsonResult([LEASED]), result(gitStatus));
}

describe("status", () => {
  test("returns Treehouse's structured workspace and process data", async () => {
    const runtime = new FakeRuntime();
    runtime.responses.push(jsonResult([{...LEASED, processes: [{pid: 123, command: "idea"}]}]));

    const output = await execute(["read", "status"], runtime) as {workspaces: unknown[]};

    expect(output.workspaces).toEqual([{...LEASED, processes: [{pid: 123, command: "idea"}]}]);
    expect(runtime.spawned[0].command).toEqual(["treehouse", "status", "--json"]);
  });

  test("reports an unavailable executable without attempting installation", async () => {
    const runtime = new FakeRuntime();
    runtime.responses.push(result("", 127, "ENOENT"));

    const error = await failure(() => execute(["read", "status"], runtime));

    expect(error.exitCode).toBe(127);
    expect(error.message).toContain("Do not install");
    expect(runtime.spawned).toHaveLength(1);
  });

  test("rejects malformed status JSON", async () => {
    const runtime = new FakeRuntime();
    runtime.responses.push(result("not-json"));

    const error = await failure(() => execute(["read", "status"], runtime));

    expect(error.message).toContain("malformed JSON");
  });
});

describe("acquire", () => {
  test("acquires a guarded lease and writes its receipt into the workspace", async () => {
    const runtime = new FakeRuntime();
    prepareAcquire(runtime);

    const output = await execute(["write", "acquire", "--holder", "agent-session"], runtime) as Record<string, unknown>;

    expect(runtime.spawned[4].command).toEqual([
      "treehouse", "get", "--lease", "--json", "--lease-holder", "agent-session",
    ]);
    expect(output).toMatchObject({
      path: WORKSPACE,
      lease_id: "lease-1",
      lease_holder: "agent-session",
      source_head: SOURCE_HEAD,
      prepared_head: SOURCE_HEAD,
      receipt_path: RECEIPT_PATH,
    });
    expect(JSON.parse(runtime.files.get(RECEIPT_PATH)!)).toEqual({
      schema_version: 2,
      path: WORKSPACE,
      lease_id: "lease-1",
      lease_holder: "agent-session",
      acquired_at: "2026-08-06T10:00:00.000Z",
      source_head: SOURCE_HEAD,
    });
    expect(runtime.spawned[0].command).toContain("core.fsmonitor=false");
    expect(runtime.spawned.some(item => item.command.includes("status") && item.command.includes(ROOT))).toBe(false);
    expect(runtime.spawned.some(item => item.command.includes("checkout"))).toBe(false);
  });

  test("detaches a clean acquired workspace at the source HEAD", async () => {
    const runtime = new FakeRuntime();
    prepareAcquire(runtime, LEASED, NATIVE_HEAD);

    await execute(["write", "acquire", "--holder", "agent-session"], runtime);

    expect(runtime.spawned.map(item => item.command)).toContainEqual([
      "git", "-c", "core.fsmonitor=false", "-C", WORKSPACE,
      "checkout", "--detach", SOURCE_HEAD,
    ]);
  });

  test("uses the holder environment variable before generating a UUID", async () => {
    const runtime = new FakeRuntime();
    runtime.env.TREEHOUSE_LEASE_HOLDER = "environment-session";
    prepareAcquire(runtime, {...LEASED, lease_holder: "environment-session"});

    await execute(["write", "acquire"], runtime);

    expect(runtime.spawned[4].command.at(-1)).toBe("environment-session");
  });

  test("generates and records a stable holder when none is provided", async () => {
    const runtime = new FakeRuntime();
    prepareAcquire(runtime, {...LEASED, lease_holder: "agent-generated-uuid"});

    await execute(["write", "acquire"], runtime);

    expect(runtime.spawned[4].command.at(-1)).toBe("agent-generated-uuid");
    expect(JSON.parse(runtime.files.get(RECEIPT_PATH)!).lease_holder).toBe("agent-generated-uuid");
  });

  test("refuses to acquire from an already leased workspace", async () => {
    const runtime = new FakeRuntime();
    runtime.responses.push(
      result(`${ROOT}\n`),
      result(`${SOURCE_HEAD}\n`),
      result(`${COMMON_DIR}\n`),
      jsonResult([{...LEASED, path: ROOT}]),
    );

    const error = await failure(() => execute(["write", "acquire"], runtime));

    expect(error.message).toContain("already has an active");
    expect(runtime.spawned).toHaveLength(4);
  });

  test("returns a newly acquired lease when receipt writing fails", async () => {
    const runtime = new FakeRuntime();
    runtime.writeError = new Error("read-only filesystem");
    prepareAcquireStart(runtime);
    runtime.responses.push(result());

    const error = await failure(() => execute(["write", "acquire", "--holder", "agent-session"], runtime));

    expect(error.message).toContain("workspace was returned");
    expect(runtime.spawned[5].command).toEqual([
      "treehouse", "return", WORKSPACE,
      "--if-lease-id", "lease-1",
      "--if-lease-holder", "agent-session",
    ]);
    expect(runtime.spawned[5].command).not.toContain("--force");
  });

  test("returns the lease when exact-HEAD preparation fails", async () => {
    const runtime = new FakeRuntime();
    prepareAcquireStart(runtime);
    runtime.responses.push(
      jsonResult([LEASED]),
      result(`${WORKSPACE}\n`),
      result(`${COMMON_DIR}\n`),
      result(),
      result(`${NATIVE_HEAD}\n`),
      result("", 4, "checkout failed"),
      result(),
    );

    const error = await failure(() => execute(["write", "acquire", "--holder", "agent-session"], runtime));

    expect(error.message).toContain("lease was returned");
    expect(error.details).toMatchObject({source_head: SOURCE_HEAD, receipt_removed: true});
    expect(runtime.files.has(RECEIPT_PATH)).toBe(false);
    expect(runtime.spawned.at(-1)!.command.slice(0, 3)).toEqual(["treehouse", "return", WORKSPACE]);
  });

  test("returns the lease when its live identity changes during acquisition", async () => {
    const runtime = new FakeRuntime();
    prepareAcquireStart(runtime);
    runtime.responses.push(
      jsonResult([{...LEASED, lease_id: "different-lease"}]),
      result(),
    );

    const error = await failure(() => execute(["write", "acquire", "--holder", "agent-session"], runtime));

    expect(error.message).toContain("lease was returned");
    expect(error.details).toMatchObject({
      rollback_exit_code: 0,
      cause: {message: "The acquired workspace does not match the live Treehouse lease."},
    });
  });

  test("retains the receipt and lease identity when preparation rollback fails", async () => {
    const runtime = new FakeRuntime();
    prepareAcquireStart(runtime);
    runtime.responses.push(
      jsonResult([LEASED]),
      result(`${WORKSPACE}\n`),
      result("/different/.git\n"),
      result("", 4, "lease changed"),
    );

    const error = await failure(() => execute(["write", "acquire", "--holder", "agent-session"], runtime));

    expect(error.message).toContain("retain this lease identity");
    expect(error.details).toMatchObject({
      path: WORKSPACE,
      lease_id: "lease-1",
      rollback_exit_code: 4,
      receipt_removed: false,
    });
    expect(JSON.parse(runtime.files.get(RECEIPT_PATH)!)).toMatchObject({
      schema_version: 2,
      source_head: SOURCE_HEAD,
    });
  });

  test("recovers and returns a lease after malformed acquire output", async () => {
    const runtime = new FakeRuntime();
    runtime.responses.push(
      result(`${ROOT}\n`),
      result(`${SOURCE_HEAD}\n`),
      result(`${COMMON_DIR}\n`),
      jsonResult([AVAILABLE]),
      result("not-json"),
      jsonResult([LEASED]),
      result(),
    );

    const error = await failure(() => execute(["write", "acquire", "--holder", "agent-session"], runtime));

    expect(error.message).toContain("recovered lease was returned");
    expect(runtime.spawned[6].command.slice(0, 3)).toEqual(["treehouse", "return", WORKSPACE]);
  });

  test("reports the recovered identity when malformed output cannot be rolled back", async () => {
    const runtime = new FakeRuntime();
    runtime.responses.push(
      result(`${ROOT}\n`),
      result(`${SOURCE_HEAD}\n`),
      result(`${COMMON_DIR}\n`),
      jsonResult([AVAILABLE]),
      result("not-json"),
      jsonResult([LEASED]),
      result("", 4, "lease changed"),
    );

    const error = await failure(() => execute(["write", "acquire", "--holder", "agent-session"], runtime));

    expect(error.message).toContain("retain this lease identity");
    expect(error.details).toMatchObject({
      path: WORKSPACE,
      lease_id: "lease-1",
      lease_holder: "agent-session",
      rollback_exit_code: 4,
    });
  });
});

describe("return", () => {
  test("returns a clean workspace with both lease guards and removes the receipt", async () => {
    const runtime = new FakeRuntime();
    prepareReturn(runtime);
    runtime.responses.push(result());

    const output = await execute(["write", "return", "--workspace", WORKSPACE], runtime) as Record<string, unknown>;

    expect(runtime.spawned[3]).toEqual({
      command: [
        "treehouse", "return", WORKSPACE,
        "--if-lease-id", "lease-1",
        "--if-lease-holder", "agent-session",
      ],
      options: {interactive: false},
    });
    expect(runtime.spawned[3].command).not.toContain("--force");
    expect(runtime.spawned[2].command).toContain("core.fsmonitor=false");
    expect(runtime.removed).toEqual([RECEIPT_PATH]);
    expect(output).toMatchObject({returned: true, dirty: false, receipt_removed: true});
  });

  test("refuses a receipt that does not match the live lease", async () => {
    const runtime = new FakeRuntime();
    runtime.files.set(RECEIPT_PATH, receipt());
    runtime.responses.push(
      result(`${WORKSPACE}\n`),
      jsonResult([{...LEASED, lease_id: "different-lease"}]),
    );

    const error = await failure(() => execute(["write", "return", "--workspace", WORKSPACE], runtime));

    expect(error.message).toContain("does not match");
    expect(runtime.spawned).toHaveLength(2);
  });

  test("requires explicit preservation confirmation for a dirty workspace", async () => {
    const runtime = new FakeRuntime();
    prepareReturn(runtime, " M changed.kt\n?? new.kt\n");

    const error = await failure(() => execute(["write", "return", "--workspace", WORKSPACE], runtime));

    expect(error.message).toContain("--confirm-preserved");
    expect(error.details).toMatchObject({changes: ["M changed.kt", "?? new.kt"]});
    expect(runtime.files.has(RECEIPT_PATH)).toBe(true);
  });

  test("requires a TTY for a confirmed dirty return", async () => {
    const runtime = new FakeRuntime();
    prepareReturn(runtime, " M changed.kt\n");

    const error = await failure(() => execute([
      "write", "return", "--workspace", WORKSPACE, "--confirm-preserved",
    ], runtime));

    expect(error.message).toContain("interactive TTY");
    expect(runtime.spawned).toHaveLength(3);
  });

  test("delegates a confirmed dirty return to Treehouse interactively", async () => {
    const runtime = new FakeRuntime();
    runtime.isTTY = true;
    prepareReturn(runtime, " M changed.kt\n");
    runtime.responses.push(result());

    const output = await execute([
      "write", "return", "--workspace", WORKSPACE, "--confirm-preserved",
    ], runtime) as Record<string, unknown>;

    expect(runtime.spawned[3].options).toEqual({interactive: true});
    expect(output).toMatchObject({returned: true, dirty: true});
  });

  test("retains the receipt and identity when Treehouse return fails", async () => {
    const runtime = new FakeRuntime();
    prepareReturn(runtime);
    runtime.responses.push(result("", 4, "lease changed"));

    const error = await failure(() => execute(["write", "return", "--workspace", WORKSPACE], runtime));

    expect(error.details).toMatchObject({
      path: WORKSPACE,
      lease_id: "lease-1",
      lease_holder: "agent-session",
    });
    expect(runtime.files.has(RECEIPT_PATH)).toBe(true);
    expect(runtime.removed).toEqual([]);
  });

  test("refuses a receipt copied from another workspace", async () => {
    const runtime = new FakeRuntime();
    runtime.files.set(RECEIPT_PATH, receipt({path: ROOT}));

    const error = await failure(() => execute(["write", "return", "--workspace", WORKSPACE], runtime));

    expect(error.message).toContain("different workspace");
    expect(runtime.spawned).toHaveLength(0);
  });

  test("refuses to return from inside the leased workspace", async () => {
    const runtime = new FakeRuntime();
    runtime.cwd = `${WORKSPACE}/plugins`;

    const error = await failure(() => execute(["write", "return", "--workspace", WORKSPACE], runtime));

    expect(error.message).toContain("outside the leased workspace");
    expect(runtime.spawned).toHaveLength(0);
  });

  test("refuses to return a workspace with live processes", async () => {
    const runtime = new FakeRuntime();
    runtime.files.set(RECEIPT_PATH, receiptV2());
    runtime.responses.push(
      result(`${WORKSPACE}\n`),
      jsonResult([{...LEASED, processes: [{pid: 42, name: "bazel"}]}]),
    );

    const error = await failure(() => execute(["write", "return", "--workspace", WORKSPACE], runtime));

    expect(error.message).toContain("live processes");
    expect(error.details).toMatchObject({processes: [{pid: 42, name: "bazel"}]});
    expect(runtime.spawned).toHaveLength(2);
  });
});

describe("command surface", () => {
  test("does not accept destructive Treehouse operations or force", async () => {
    const runtime = new FakeRuntime();

    expect((await failure(() => execute(["write", "destroy"], runtime))).exitCode).toBe(2);
    expect((await failure(() => execute(["write", "return", "--workspace", WORKSPACE, "--force"], runtime))).exitCode).toBe(2);
    expect((await failure(() => execute(["write", "return"], runtime))).exitCode).toBe(2);
    expect(runtime.spawned).toEqual([]);
  });
});
