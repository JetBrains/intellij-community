// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import {mkdir, readdir, readFile, rm, writeFile} from "node:fs/promises"
import {dirname, join, resolve} from "node:path"
import process from "node:process"
import {fileURLToPath} from "node:url"

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const repoRoot = resolve(__dirname, "..", "..");
const profilesPath = join(__dirname, "skill-profiles.json");
const skillSourceDirs = [join(repoRoot, ".agents", "skills"), join(repoRoot, "community", ".agents", "skills")];
const settingsPath = join(repoRoot, ".claude", "settings.local.json");
const recordPath = join(repoRoot, ".claude", "local", "skill-profile.json");

/** The `skillOverrides` states that hide a skill. `on` is the state of a skill the file does not name. */
const validStates = new Set(["off", "name-only", "user-invocable-only"]);
const defaultState = "off";

/**
 * Reads and validates the grouping table.
 *
 * The table names each skill once, and a profile names the areas it keeps. A skill that no area
 * names is core, so it stays on for each profile. That keeps a new skill visible until somebody
 * decides which area owns it.
 */
export async function loadProfiles(path = profilesPath) {
  const parsed = JSON.parse(await readFile(path, "utf8"));
  const areas = parsed.areas;
  const profiles = parsed.profiles;
  const violations = [];
  if (!areas || typeof areas !== "object") {
    throw new Error(`${path} has no areas object.`);
  }
  if (!profiles || typeof profiles !== "object") {
    throw new Error(`${path} has no profiles object.`);
  }

  const owner = new Map();
  for (const [area, names] of Object.entries(areas)) {
    if (!Array.isArray(names) || names.length === 0 || names.some(name => typeof name !== "string" || name === "")) {
      violations.push(`Area "${area}" must be a non-empty array of skill names.`);
      continue;
    }
    for (const name of names) {
      const previousArea = owner.get(name);
      if (previousArea !== undefined) {
        violations.push(`Skill "${name}" is in area "${previousArea}" and in area "${area}".`);
        continue;
      }
      owner.set(name, area);
    }
  }

  for (const [profile, definition] of Object.entries(profiles)) {
    const keeps = definition?.keeps;
    if (!Array.isArray(keeps)) {
      violations.push(`Profile "${profile}" must have a keeps array.`);
      continue;
    }
    for (const area of keeps) {
      if (!Object.hasOwn(areas, area)) {
        violations.push(`Profile "${profile}" keeps unknown area "${area}".`);
      }
    }
  }

  if (violations.length > 0) {
    throw new Error(`Invalid ${path}:\n${violations.join("\n")}`);
  }
  return {areas, profiles};
}

/**
 * Returns the skills the profile turns off, sorted by name.
 *
 * `knownSkills` drops a name the table still has after the skill went. A phantom entry in the
 * settings file is harmless, but it hides the real state of the profile from the developer.
 */
export function resolveOffNames(table, profileName, knownSkills) {
  const profile = table.profiles[profileName];
  if (profile === undefined) {
    throw new Error(`Unknown profile "${profileName}". Known profiles: ${Object.keys(table.profiles).sort().join(", ")}.`);
  }
  const kept = new Set(profile.keeps);
  const names = [];
  for (const [area, areaSkills] of Object.entries(table.areas)) {
    if (kept.has(area)) {
      continue;
    }
    for (const name of areaSkills) {
      if (knownSkills === undefined || knownSkills.has(name)) {
        names.push(name);
      }
    }
  }
  return names.sort((left, right) => left.localeCompare(right));
}

/**
 * Merges the profile into a settings object and returns a new one.
 *
 * `previousNames` are the entries the last run of this script wrote. It removes those first, so a
 * second profile does not leave the skills of the first one hidden. An entry the developer wrote
 * by hand is not in that record, so it survives.
 */
export function applyOverrides(settings, names, state, previousNames = []) {
  if (!validStates.has(state)) {
    throw new Error(`Unknown state "${state}". Use one of: ${[...validStates].join(", ")}.`);
  }
  const next = {...settings};
  const overrides = {...(next.skillOverrides ?? {})};
  for (const name of previousNames) {
    delete overrides[name];
  }
  for (const name of names) {
    overrides[name] = state;
  }
  return withOverrides(next, overrides);
}

/** Removes the named entries and keeps the rest of the settings file. */
export function removeOverrides(settings, names) {
  const next = {...settings};
  const overrides = {...(next.skillOverrides ?? {})};
  for (const name of names) {
    delete overrides[name];
  }
  return withOverrides(next, overrides);
}

function withOverrides(settings, overrides) {
  if (Object.keys(overrides).length === 0) {
    delete settings.skillOverrides;
    return settings;
  }
  settings.skillOverrides = Object.fromEntries(
    Object.entries(overrides).sort(([left], [right]) => left.localeCompare(right)),
  );
  return settings;
}

/** Collects the skill names an agent in this checkout can reach. */
export async function collectSkillNames(dirs = skillSourceDirs) {
  const names = new Set();
  for (const dir of dirs) {
    let entries;
    try {
      entries = await readdir(dir, {withFileTypes: true});
    } catch (error) {
      if (error && error.code === "ENOENT") {
        continue;
      }
      throw error;
    }
    for (const entry of entries) {
      if (!entry.isDirectory()) {
        continue;
      }
      try {
        await readFile(join(dir, entry.name, "SKILL.md"), "utf8");
      } catch (error) {
        if (error && error.code === "ENOENT") {
          continue;
        }
        throw error;
      }
      names.add(entry.name);
    }
  }
  return names;
}

async function readJson(path, fallback) {
  try {
    return JSON.parse(await readFile(path, "utf8"));
  } catch (error) {
    if (error && error.code === "ENOENT") {
      return fallback;
    }
    throw error;
  }
}

async function writeJson(path, value) {
  await mkdir(dirname(path), {recursive: true});
  await writeFile(path, `${JSON.stringify(value, null, 2)}\n`, "utf8");
}

function printUsage() {
  console.log(`Usage: bun community/.ai/skill-profile.mjs <command>

  list                  Print the profiles and the areas.
  show <profile>        Print the skills the profile turns off.
  apply <profile>       Write the profile to .claude/settings.local.json.
                        --state off|name-only|user-invocable-only (default: ${defaultState})
                        --dry-run prints the change and writes nothing.
  reset                 Remove the entries this script wrote.

The settings file is yours alone: git ignores it. A skill that no area names stays on.`);
}

async function commandList(table) {
  const areaNames = Object.keys(table.areas).sort();
  console.log("Areas:");
  for (const area of areaNames) {
    console.log(`  ${area}: ${table.areas[area].join(", ")}`);
  }
  console.log("\nProfiles:");
  for (const profile of Object.keys(table.profiles).sort()) {
    const keeps = table.profiles[profile].keeps;
    console.log(`  ${profile}: keeps ${keeps.length === 0 ? "no area" : keeps.join(", ")}`);
  }
  const record = await readJson(recordPath, null);
  console.log(`\nApplied profile: ${record?.profile ?? "none"}`);
}

async function commandShow(table, profileName) {
  const names = resolveOffNames(table, profileName, await collectSkillNames());
  console.log(`Profile ${profileName} turns off ${names.length} skills:`);
  for (const name of names) {
    console.log(`  ${name}`);
  }
}

async function commandApply(table, profileName, state, dryRun) {
  const names = resolveOffNames(table, profileName, await collectSkillNames());
  const settings = await readJson(settingsPath, {});
  const record = await readJson(recordPath, null);
  const next = applyOverrides(settings, names, state, record?.names ?? []);
  if (dryRun) {
    console.log(`Profile ${profileName} would set ${names.length} skills to "${state}".`);
    console.log(JSON.stringify(next.skillOverrides ?? {}, null, 2));
    return;
  }
  await writeJson(settingsPath, next);
  await writeJson(recordPath, {profile: profileName, state, names});
  console.log(`Profile ${profileName} set ${names.length} skills to "${state}" in ${settingsPath}.`);
  console.log("Restart the harness, or open /skills, to see the new list.");
}

async function commandReset() {
  const record = await readJson(recordPath, null);
  if (record === null) {
    console.log("No profile is applied.");
    return;
  }
  const settings = await readJson(settingsPath, {});
  await writeJson(settingsPath, removeOverrides(settings, record.names ?? []));
  await rm(recordPath, {force: true});
  console.log(`Removed the ${record.names?.length ?? 0} entries of profile ${record.profile}.`);
}

export async function main(argv = process.argv.slice(2)) {
  const args = argv.filter(argument => !argument.startsWith("--"));
  const flags = new Set(argv.filter(argument => argument.startsWith("--")));
  const stateFlag = argv.find(argument => argument.startsWith("--state="));
  const state = stateFlag === undefined ? defaultState : stateFlag.slice("--state=".length);
  const [command, profileName] = args;

  if (command === undefined || flags.has("--help")) {
    printUsage();
    return;
  }
  const table = await loadProfiles();
  switch (command) {
    case "list":
      await commandList(table);
      return;
    case "show":
      if (profileName === undefined) {
        throw new Error("show needs a profile name.");
      }
      await commandShow(table, profileName);
      return;
    case "apply":
      if (profileName === undefined) {
        throw new Error("apply needs a profile name.");
      }
      await commandApply(table, profileName, state, flags.has("--dry-run"));
      return;
    case "reset":
      await commandReset();
      return;
    default:
      printUsage();
      throw new Error(`Unknown command "${command}".`);
  }
}

const executedPath = process["argv"]?.[1];
if (executedPath && resolve(executedPath) === __filename) {
  try {
    await main();
  } catch (error) {
    console.error(error instanceof Error ? error.message : error);
    process.exitCode = 1;
  }
}
