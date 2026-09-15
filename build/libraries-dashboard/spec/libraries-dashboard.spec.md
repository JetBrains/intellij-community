---
name: Libraries Dashboard Spec
description: Requirements for the CLI tool that reports Maven library versions pinned in intellij.libraries.* wrapper modules and .idea/libraries project libraries, compares them with the upstream releases, and bumps a library in place.
targets:
  - ../libraries-dashboard.mjs
---

# Libraries Dashboard Spec

Status: Draft
Date: 2026-09-14

## Summary
A single-file Bun CLI (`libraries-dashboard.mjs`) with three commands. The report command scans every `intellij.libraries.*.iml` wrapper module and every `.idea/libraries/*.xml` project library, extracts the pinned Maven coordinates, and reports per artifact the pinned version(s), the latest version in the first repository that serves the artifact, an outdatedness classification, the repository, and a link to the GitHub releases page when the POM names one. The bump command rewrites the pinned version, the jar URLs and the checksums of a library in place. The check command compares the artifact snapshot of a multi-artifact library with the direct dependencies of its POM, which is the comparison JPS enforces at build time. The tool runs offline-friendly (disk cache) and produces an HTML dashboard, an ANSI terminal table, or JSON.

## Goals
- Discover every Maven library pinned through a wrapper module or a project library without manual input.
- Surface which libraries are outdated and by how much (major / minor / patch), and which pins are JetBrains forks.
- Resolve artifacts from every repository the IDE project declares, not only Maven Central.
- Provide a one-click path from a row in the dashboard to the upstream GitHub releases page.
- Make a version bump mechanical: one command edits the files, the build regenerates the rest.
- Run with `bun` using only built-ins (`fs`, `path`, `crypto`, global `fetch`), no npm/package.json dependencies.
- Finish a cold run in a few minutes on a typical connection; a warm (cached) run must complete in under 5 seconds.

## Non-goals
- Committing library version changes or creating tickets.
- Recomputing the transitive artifact set of a library. The bump command keeps the snapshot and checks it against the direct dependencies of the new POM only; a change deeper in the dependency tree needs the JPS resolution (`./build/downloadLibraries.cmd`).
- Surfacing `http_file` entries in `MODULE.bazel` or other Bazel-only Maven pins.
- Calling the GitHub API.
- Gating CI or builds. The report command always exits `0` when it produced a report.

## Requirements

### Discovery
- The tool MUST read `.idea/modules.xml` in the repository root and in `community/` and take every `filepath` whose file name starts with `intellij.libraries.` and ends with `.iml`. `$PROJECT_DIR$` resolves to the directory that holds the `.idea` folder. These files are the `wrapper` sources.
- The tool MUST read every `*.xml` file in `.idea/libraries/` and `community/.idea/libraries/`. These files are the `project` sources.
- The tool MUST NOT walk the file tree for wrapper modules, so copies under test data are not scanned.
- A file registered by both `modules.xml` files (every community module) MUST be scanned once.
- The tool MUST tolerate a missing `modules.xml` or libraries directory (partial checkout): the source is skipped silently.
  [@test] ../test/discovery.test.mjs

### Parsing
- For each source file, the tool MUST extract every `<library ... type="repository">…</library>` block.
- Within each block, the tool MUST read the `maven-id="groupId:artifactId:version"` attribute on the `<properties>` element as the canonical coordinate. When `maven-id` is absent, the block MUST be ignored.
- The parser MUST be tolerant of attribute order inside the `<library>` open tag.
- The parser MUST NOT use a full XML parser; it may rely on regular expressions given the stable serialization format.
- Each entry carries its `kind` (`wrapper` or `project`). The source name of a wrapper is the module name without the `intellij.libraries.` prefix; the source name of a project library is its `name` attribute.
  [@test] ../test/parsing.test.mjs

### Grouping
- Entries MUST be deduplicated by `groupId:artifactId` across both kinds. Each group MUST carry:
  - the sorted list of distinct versions observed,
  - the list of referring sources with kind, version and file path.
- When a group contains more than one distinct version, the group's status MUST be `inconsistent` regardless of upstream comparison.

### Version model
- A version is `\d+(\.\d+){0,3}` followed by an optional suffix that starts with `.`, `-`, `+` or `_`. The suffix is split into tokens on those separators. Versions of another shape are ignored.
- The tokens `Final`, `RELEASE` and `GA` are release markers and count as no suffix.
- A prerelease token matches `alpha|beta|rc|cr|ea|eap|milestone|snapshot|preview|dev|pr|m|nightly` with optional trailing digits.
- The variant family of a version is the list of alpha tokens that are neither prerelease tokens nor fork markers (`jre`, `jdk5`, `r`, `x-compat`). An empty family means a plain release.
- Comparison: numeric segments first, then suffix tokens pairwise. Numeric tokens compare numerically. A missing token beats a prerelease token and loses to a numeric token. Prerelease tokens compare by stage (`dev` < `snapshot` < ... < `rc` < `cr`), then by their number.
  [@test] ../test/version-selection.test.mjs

### Latest-version lookup
- The tool MUST build the repository list from Maven Central (`https://repo1.maven.org/maven2`) followed by every `<option name="url">` in `.idea/jarRepositories.xml` and `community/.idea/jarRepositories.xml`, in file order, without duplicates and without a second Maven Central mirror.
- For each artifact the tool MUST query `{repo}/{groupPath}/{artifactId}/maven-metadata.xml` repository by repository and stop at the first one that returns a non-empty `<version>` list. That repository is the artifact's `repo`.
- The latest version MUST be selected from the `<version>` entries by:
  - keeping only versions of the version model shape,
  - dropping fork versions,
  - keeping only versions whose variant family equals the family of the highest pinned version,
  - picking the highest stable version. When no stable version exists and the pinned version is a prerelease, picking the highest prerelease.
- When the pin is stable and only prereleases exist, `latest` MUST be null and the group MUST carry the note `only prereleases: <highest>`.
- Artifacts absent from every repository MUST be reported as `unknown` and MUST NOT crash the run.
  [@test] ../test/version-selection.test.mjs

### GitHub link derivation
- For each artifact, the tool SHOULD fetch the POM at `{repo}/{groupPath}/{artifactId}/{currentVersion}/{artifactId}-{currentVersion}.pom` from the resolving repository.
- The tool SHOULD search the POM in this order:
  1. `<scm>` child `<url>`, `<connection>`, or `<developerConnection>` containing `github.com`.
  2. Top-level `<url>` pointing to `github.com`.
- The extracted URL MUST be normalized to `https://github.com/{owner}/{repo}` (strip `scm:git:` / `git+` / `git://` prefixes, trailing slashes, and `.git` suffix).
- The dashboard link MUST point to `https://github.com/{owner}/{repo}/releases/latest`. The tool MUST NOT call the GitHub API.
- POM fetch failures MUST NOT abort the run; the artifact MUST simply report no GitHub link.
  [@test] ../test/parsing.test.mjs

### Classification
- Status values (in priority order for sorting worst-first): `major`, `minor`, `patch`, `inconsistent`, `unknown`, `fork`, `ahead`, `up-to-date`.
- A pin is a `fork` when its groupId starts with `org.jetbrains.intellij.deps` or its version carries a token `jetbrains`, `intellij`, `jb<digits>`, `idea<digits>`, `patched` or `amn`. The upstream latest is still resolved and shown; the row is not actionable.
- Classification MUST compare the highest observed current version against the resolved latest:
  - differing first numeric segment → `major`,
  - differing second segment → `minor`,
  - differing third or fourth segment, or a lower suffix → `patch`,
  - identical → `up-to-date`,
  - current > latest → `ahead`,
  - unresolvable latest → `unknown`.
  [@test] ../test/version-selection.test.mjs

### Caching
- The tool MUST persist `{latest, githubUrl, repo, note, fetchedAt}` per `groupId:artifactId` to `<repo>/out/libraries-dashboard/cache.json`. The repo-root `/out/` directory is git-ignored.
- The cache file carries `"version": 2`. A file with another version MUST be discarded.
- The output directory MUST be created on demand before any write.
- Cache entries older than 24 hours MUST be ignored.
- `--no-cache` MUST skip reading the cache (writes still occur).
- `--refresh` MUST force a full refetch regardless of cache freshness. The cached `repo` of an artifact is tried first.

### CLI surface
- Report: `bun libraries-dashboard.mjs [--no-cache] [--refresh] [--format=html|text|json|all] [--open]`.
- Bump: `bun libraries-dashboard.mjs bump <groupId:artifactId>[=<version>]... [--kind=wrapper|project] [--no-cache] [--refresh]`.
- Check: `bun libraries-dashboard.mjs check [<groupId:artifactId>...] [--kind=wrapper|project] [--no-cache] [--refresh]`.
- Default `--format` MUST be `all` (HTML file + terminal table).
- `--format=html` or `all` MUST write `<repo>/out/libraries-dashboard/dashboard.html`.
- `--format=json` MUST write `<repo>/out/libraries-dashboard/dashboard.json` and MUST NOT print the terminal table.
- `--format=text` MUST print the terminal table only and MUST NOT touch disk for output.
- `--open` MUST launch the system default browser on the generated HTML and MUST be a no-op when the selected format did not produce an HTML file.
- Unknown flags, a bump without coordinates, and a coordinate that is not pinned anywhere (bump or check) MUST exit with status `2` and a short error.
- Successful runs MUST exit with status `0`; fatal parse/IO errors MUST exit with status `1`.

### Bump command
- The target version is the explicit `=version` or, when absent, the resolved latest. Without a target or a resolving repository the library is reported and skipped; the command exits `1` at the end.
- `--kind` restricts the rewrite to wrapper modules or to project libraries. Skipped sources are listed. The Fleet project libraries (`fleet:*`) are generated from the Fleet version catalog by `fleet/build/generator`, so a bump of those goes through the catalog, not through this tool.
- For every source file of the library and only inside the `<library>` block whose `maven-id` matches the pinned coordinate, the tool MUST:
  1. replace `maven-id="G:A:old"` with the new version,
  2. replace the version in every URL whose path contains `/<old>/<name>-<old>`, so the jar, the sources jar and every `<artifact url>` of the same version move together while roots pinned to another version stay,
  3. replace the `<sha256sum>` of every `<artifact>` with the checksum of the new jar. The checksum comes from `<jar url>.sha256` in the resolving repository; when that file is absent the jar is downloaded and hashed. For Maven Central the checksum is fetched through the Central mirror from `jarRepositories.xml`, because the build downloads through that mirror and a release the mirror has not cached yet must fail the bump, not the build.
- The file MUST be written back byte-exact except for the replaced substrings. No trailing newline is added.
- All files of one library MUST be rewritten in memory before any write, so a failed checksum leaves the library untouched.
- When the block lists more than one `<artifact>`, the tool MUST print the compile and runtime dependencies of the new POM and MUST run the snapshot check (below) on the rewritten block. A problem is printed with the remedy and the command exits `1` at the end; the rewritten files stay, so the agent can apply the remedy in place.
- Some files outside the JPS model copy a library version, and a project structure test checks each copy: `community/platform/jps-bootstrap/pom.xml` (`JpsBoostrapStructureTest`) and the `VERSION` constant of `JetBrainsAnnotationsExternalLibraryResolver.java` (`IdeaUltimateProjectStructureTest`). The tool MUST keep the list in `VERSION_MIRRORS`. A bump MUST rewrite every copy of the bumped library in these files and list each rewritten file with the test that checks it. A POM version that is a property reference is not a copy. Both the bump and the report MUST print every copy whose version differs from the highest pinned version, and the bump MUST exit `1` when such a copy remains.
- After the changes the tool MUST print the follow-up commands: `./build/jpsModelToBazel.cmd`, then `./fleet/build/generateProjectModel.cmd dump` and `bazel mod deps --lockfile_mode=update` in the root and in `community/` when the Fleet generator exists in the checkout, then `bazel run //:format.check`. The Fleet generator copies the JPS library versions into `fleet/build/gradle/jps.versions.toml`, `fleet/build/jps-library-mappings.tsv` and both `fleet/kmp.MODULE.bazel` files, and its `check` mode fails on drift. The `kmp` module extension records the artifact list of each `kmp.MODULE.bazel` in the `MODULE.bazel.lock` of its module, and CI runs Bazel with `--lockfile_mode=error`, so a stale lockfile fails the build. Then the tool MUST print the verification command `./tests.cmd --module intellij.projectStructureTests --test 'com.intellij.ideaProjectStructure.fast.*'`. The generated update prompt MUST list the same commands.
  [@test] ../test/bump.test.mjs

### Snapshot check
JPS resolves a repository library from its POM and then requires the resolved jar set to equal the `<verification>` artifact set (`DependencyResolvingBuilder.isAllCompiledRootsVerificationPresent`). Bazel downloads the listed URLs instead, so a snapshot that disagrees with the POM passes every local Bazel build and fails the first JPS build on TeamCity. The mockito 5.23.0 bump pinned `byte-buddy-agent` at 1.18.13 while the POM declares 1.17.7; the check exists to catch this before a push.

- The direct dependencies of a POM are the `<dependency>` entries of the top-level `<dependencies>` element with scope `compile` or `runtime` (default `compile`), not optional, and of type `jar` (the default). Entries under `<dependencyManagement>`, `<build>`, `<profiles>` and `<reporting>`, and entries inside an XML comment, are not dependencies.
- The tool MUST resolve `${project.version}`, `${project.groupId}` and a `${property}` declared in the `<properties>` of the same POM. The project version and groupId fall back to the `<parent>`. A reference the POM cannot resolve, a dependency without a `<version>`, and an open version range yield no exact version. A hard requirement `[x]` is the exact version `x`.
- For one library block the check MUST report:
  - a `version` problem when an `<artifact>` has the `groupId:artifactId` of a direct dependency at another version than the POM declares,
  - a `missing` problem when a direct dependency has no `<artifact>` and no `<exclude>` entry.
- A dependency listed under `<exclude>`, a dependency with a `<classifier>`, and every dependency of a block with `include-transitive-deps="false"` are not compared. A dependency without an exact version is a note, not a problem.
- The check MUST print the remedy with every problem: pin the version the POM declares, or exclude the dependency and add a module dependency on its wrapper module.
- The `check` command MUST scan every library block that lists more than one `<artifact>` (or the blocks of the named libraries), read the POM from the local Maven repository (`$MAVEN_REPOSITORY` or `~/.m2/repository`) first and then from the cached repository and every repository in order, and exit `1` when a problem exists. A whole-repository run prints the notes as one count; a run with named libraries prints each note. An unavailable POM is reported and counted, not a failure.
- The check compares direct dependencies only. A dependency of a dependency is out of scope; the JPS run (`./build/downloadLibraries.cmd`) covers it.
  [@test] ../test/snapshot-check.test.mjs

### HTML output
- The generated HTML MUST be self-contained: no external scripts, no CDN references, no network dependencies at view time.
- It MUST support light and dark themes via `color-scheme: light dark` and system-color keywords (`Canvas`, `CanvasText`, `GrayText`).
- It MUST include, above the table, a live text filter over `groupId:artifactId`, a status dropdown, a kind dropdown (`wrapper` / `project`), and status count chips.
- Column headers MUST be sortable (ascending/descending toggle) client-side.
- Columns: artifact, current, latest, status, kind, source count, repository, GitHub, action. The source count renders as a `<details>` element that expands to the source list with kind and version. The latest cell shows the note as a tooltip when present.
- Status MUST render as a colored badge (`.badge-major`, `.badge-minor`, `.badge-patch`, `.badge-inconsistent`, `.badge-unknown`, `.badge-fork`, `.badge-ahead`, `.badge-up-to-date`).
- Each row MUST expose an **Action** column with a **Copy prompt** button for every artifact that is not `up-to-date`, `ahead` or `fork`. Clicking the button MUST place a ready-to-paste prompt onto the system clipboard (via `navigator.clipboard.writeText`, falling back to a hidden `<textarea>` + `document.execCommand("copy")`).
- After a successful copy the button MUST flash a `.copied` state (~1.2s) and a transient toast MUST appear near the bottom of the viewport for ~1.6s.
- The prompt for outdated artifacts MUST name the `groupId:artifactId`, the current and target versions, the repo-relative path and kind of every source file, the `bump` command with the explicit target, the follow-up commands, the JPS rule the snapshot check enforces with its remedy, and the `check` command to run after a manual edit of the block.
- The prompt for `unknown` artifacts MUST instead ask the agent to investigate the authoritative release source and propose a bump plan. It names the note when one exists.
- All file paths embedded in prompts MUST be repo-relative, not absolute.

### Terminal output
- Output MUST be sorted worst-first by status; ties broken by `groupId:artifactId`.
- Columns: artifact, current, latest (with the note when there is no latest), status, kind, source count, repository label, GitHub.
- The repository label is `central` for Maven Central, the project path for a JetBrains Space repository (`ij/intellij-dependencies`), `google` for the Android repository, otherwise the host.
- ANSI colors MUST be emitted only when `process.stdout.isTTY` is truthy and `NO_COLOR` is unset.
- A single summary line MUST follow the table: `Total: {n} ({status: count ...})`.

### Concurrency & networking
- Outbound HTTP requests MUST be issued in a pool of at most 8 concurrent artifacts. One artifact queries its repositories sequentially.
- Every `fetch` MUST use `AbortSignal.timeout(15000)`.
- Every `fetch` MUST send a `user-agent: intellij-libraries-dashboard/<ver>` header.
- Timeouts and non-2xx responses MUST be handled as soft failures (try the next repository, mark `unknown`, or drop the GitHub link), never thrown up the call stack.

## User Experience
- The tool is a CLI only. No in-IDE integration is in scope.
- User-visible strings in the HTML and terminal output are English-only; localization is out of scope (the tool is for platform maintainers).
- Typical invocation from the repository root: `bun community/build/libraries-dashboard/libraries-dashboard.mjs --open`, then `bun community/build/libraries-dashboard/libraries-dashboard.mjs bump <G:A>=<version>` for one row.

## Data & Backend
- Sources: Maven Central Repository 2 and the repositories in the two `jarRepositories.xml` files (all reached through `cache-redirector.jetbrains.com`). No authenticated endpoints.
- Formats consumed: `maven-metadata.xml` (versioning `<version>` list), Maven POM (`<scm>`, `<url>`, `<dependency>`), `<jar>.sha256`.
- Artifact coordinates are read exclusively from iml and library xml files; no JPS or Bazel model is loaded.
- Cache file shape: `{ "version": 2, "entries": { "<G:A>": { "latest": string|null, "githubUrl": string|null, "repo": string|null, "note": string|null, "fetchedAt": number } } }`.

## Error Handling
- Missing `modules.xml`, libraries directory or `jarRepositories.xml`: skipped silently.
- Unreadable source file: skipped; other files continue.
- Source file without any `maven-id`: skipped silently.
- Artifact in no repository: reported as `unknown`, repository and GitHub link empty, counted in summary.
- POM lookup failure: GitHub column shows `—`, artifact is not otherwise degraded.
- Invalid JSON or an old version in the cache: cache is discarded and rebuilt on the current run.
- No artifacts discovered: the tool MUST print a diagnostic and exit `1`.
- Bump with a missing checksum or a source file that does not pin the old version: that library is left unchanged and reported; the command exits `1` after the other libraries.
- Bump whose rewritten block fails the snapshot check: the files stay rewritten, the problems and the remedy are printed, and the command exits `1` after the other libraries.
- Check with an unavailable POM: the library is reported and counted; the exit status depends on the problems of the other libraries only.

## Testing / Local Run
- Unit tests: `node --test community/build/libraries-dashboard/test/*.test.mjs`. The tests import the script; the script runs `main()` only when it is the entry point.
- Cold run (forces full refetch): `bun community/build/libraries-dashboard/libraries-dashboard.mjs --refresh`.
- Warm run (uses cache): `bun community/build/libraries-dashboard/libraries-dashboard.mjs`.
- Verify HTML: `open out/libraries-dashboard/dashboard.html` (or pass `--open`).
- Verify JSON shape: `bun community/build/libraries-dashboard/libraries-dashboard.mjs --format=json && jq '.artifacts[0]' out/libraries-dashboard/dashboard.json`.
- Spot-check an artifact's reported latest against `https://central.sonatype.com/artifact/{groupId}/{artifactId}`.
- Snapshot check over the repository: `bun community/build/libraries-dashboard/libraries-dashboard.mjs check`; for one library with its notes: `... check org.mockito:mockito-core`. The whole run reads the POMs from `~/.m2/repository` and finishes in under a second when they are there.
- Reproduce the JPS resolution that TeamCity runs before every build: `./build/downloadLibraries.cmd`. It calls the same `resolveProjectDependencies()` as jps-bootstrap and needs Space credentials.
- Verify a bump: run it on a single-artifact library, inspect `git diff`, then run `./build/jpsModelToBazel.cmd`, which downloads every jar and fails on a checksum mismatch. `jpsModelToBazel` does not compare the snapshot with the POM; the bump and `check` do. Then run `./fleet/build/generateProjectModel.cmd dump` and confirm with `./fleet/build/generateProjectModel.cmd check`. When the dump changes a `kmp.MODULE.bazel`, run `bazel mod deps --lockfile_mode=update` in the root and in `community/`, then confirm with `bazel build --nobuild --lockfile_mode=error` on a target that uses the `kmp` extension. Finish with `./tests.cmd --module intellij.projectStructureTests --test 'com.intellij.ideaProjectStructure.fast.*'`, which is the Smoke Tests gate for the version copies and the Kotlin, Compose and LanguageTool version alignment.

## Open Questions / Risks
- A cold run on a library that lives in a late repository costs one request per earlier repository. The repository order in `jarRepositories.xml` decides the cost.
- The prerelease and fork token lists are hard-coded; a novel suffix is treated as a variant family and may hide a newer release until the list grows.
- The mirror list is hard-coded. A new file that copies a library version, together with a new project structure test, needs an entry in `VERSION_MIRRORS`, or the bump misses it and the Smoke Tests build fails.
- The bump command does not update the transitive artifact set. A release that adds or drops a direct dependency fails the snapshot check and needs a manual edit of the `<artifact>` and `<root>` lists. A change deeper in the tree passes the check; only the JPS run finds it.
- The snapshot check reads one POM without its parents, so a version managed by a parent POM or a BOM is a note, not a comparison (132 such dependencies across 176 multi-artifact libraries on 2026-09-15).
- `repo1.maven.org` does not rate-limit anonymously, but the sustained request volume may become impolite; the 8-way concurrency cap is a heuristic.
