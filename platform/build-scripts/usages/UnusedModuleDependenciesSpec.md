# Unused Module Dependencies Tool

## Goal

Provide a read-only command line tool that reports direct JPS module dependencies that can be removed safely from module `.iml` files.

JPS remains the source of truth for declared dependencies and scopes.
The tool uses Bazel incremental-compilation data only as evidence of which dependencies are actually used.

## Scope

V1 is intentionally narrow.

- Module-to-module dependencies only.
- Kotlin/JVM modules only.
- Read-only reporting only. The tool does not edit `.iml` files.
- Existing Bazel outputs only. The tool does not invoke Bazel.

Out of scope for V1:

- Library dependency cleanup.
- SDK dependency cleanup.
- Automatic fixes.
- PSI or source-level analysis outside persisted IC data.

## Inputs

The tool reads:

- the JPS project model loaded from the repository root
- `build/bazel-targets.json`
- produced target jars and sibling IC data directories
- persisted IC files `dep-graph.mv` and `config-state.dat`

Expected repository state:

- module declarations come from `.iml` files
- `build/bazel-targets.json` matches the current JPS model
- analyzed targets have already been built, so their jars and IC data exist on disk

## CLI Contract

Entry point: `FindUnusedModuleDependencies`

Arguments:

- `--project-root <path>`
  - optional
  - defaults to the current repository root
- exactly one selection mode:
  - `--all`
  - one or more `--module <name>`

Invalid argument combinations must return exit code `2`.

## Output Contract

The tool prints a human-readable report.

Report structure:

- analyzed modules section
- for each analyzed module, zero or more removable direct dependencies
- skipped modules section for modules that could not be analyzed safely

Each removable dependency entry must include:

- dependency module name
- declared JPS scope

Each skipped module entry must include:

- module name
- skip reason

## Exit Codes

- `0`: analysis completed and no removable dependencies were found
- `1`: analysis completed and at least one removable dependency was found
- `2`: invalid arguments or at least one selected module could not be analyzed safely

## Dependency Model

Candidates are direct `JpsModuleDependency` entries only.

Candidate filtering:

- ignore exported direct dependencies
- ignore `RUNTIME` dependencies
- ignore self-dependencies

Dependency retention rules:

- a non-`TEST` dependency is required if production analysis or test analysis uses it
- a `TEST` dependency is required only if test analysis uses it

## Evidence Model

The evidence source is persisted Bazel IC dependency data.

For each analyzed target:

1. find the target output jar from `build/bazel-targets.json`
2. derive the sibling IC directory by replacing the jar basename with `<jar-base>-ic`
3. read `dep-graph.mv`
4. read `config-state.dat`
5. extract external owner ids from the dependency graph
6. resolve those owner ids back to classpath jars and then to module owners

The dependency graph is treated as authoritative for persisted source-level edges, including edges that do not survive in plain bytecode when the IC graph records them.

## Owner Resolution

Owner resolution is classpath-order-sensitive.

Resolution algorithm:

1. read classpath entries from `config-state.dat` in recorded order
2. build a jar-to-module-owner map from `build/bazel-targets.json`
3. for each classpath jar, index:
   - class owners from class entries in the jar
   - package owners implied by those class entries
4. match unresolved owner ids against jars in classpath order
5. stop on the first classpath jar that provides a given owner id

Tie-breaking rule:

- earlier classpath entries win

Non-module jars:

- if an owner resolves to a classpath jar that is not owned by any module, that usage is treated as satisfied without keeping any module dependency

Supported owner kinds in V1:

- class owners
- package owners

## Exported Dependency Semantics

The tool must preserve the same effective behavior as exported dependency chains in the JPS model.

For a direct dependency `A -> B`:

- if a used owner resolves to `B`, keep `B`
- if a used owner resolves to a module reachable from `B` through exported dependencies included in the relevant classpath kind, keep `B`

Classpath kind rules:

- production analysis uses `PRODUCTION_COMPILE`
- test analysis uses `TEST_COMPILE`

## Production And Test Analysis

Production and test outputs are analyzed separately.

Production analysis:

- uses production target jars from `build/bazel-targets.json`

Test analysis:

- uses test target jars from `build/bazel-targets.json`
- a module may have empty test usage, but if a declared test target is analyzed its jar and IC data must exist

## Safety Rules

The tool is conservative.

It must skip a module instead of reporting removals when:

- the module is missing from `build/bazel-targets.json`
- a required target jar is missing
- `dep-graph.mv` is missing or unreadable
- `config-state.dat` is missing or unreadable
- an external owner id cannot be resolved confidently
- the same classpath jar maps to multiple module owners for a matched usage

Skipping is preferred to false-positive removal suggestions.

## Example Findings

Typical removable dependency cases:

- direct compile dependency declared but never used by production or test compilation
- direct test dependency declared but never used by test compilation
- direct dependency shadowed by an earlier non-module classpath jar that already satisfies the same owner

Typical kept dependency cases:

- class owner resolves directly to the dependency module
- package owner resolves to the dependency module
- usage resolves to a module reached through the dependency's exported closure
- dependency is used only from tests but is declared with non-`TEST` scope

## Test Coverage Expectations

V1 tests should cover at least:

- removable direct compile dependency
- removable direct test dependency
- compile dependency used only from tests and therefore kept
- exported closure retention
- classpath-order tie breaking against a non-module jar
- package-owned usage resolution
- missing IC data causing a skipped module and exit code `2`

## Future Extensions

Possible follow-ups after V1:

- machine-readable output
- optional fix mode that edits `.iml` files
- library dependency reporting
- richer diagnostics for why a dependency is kept
- batch workflows for generating reviewable cleanup patches
