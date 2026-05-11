# Mast IntelliJ Platform Spike

## Status
Started on 2026-05-11.

Current local checkout:

- Repository path: `/home/dev/workspace/mast`
- Upstream source: `JetBrains/intellij-community`
- Upstream commit: `a4606924cce74e0810eaab9668a8668a0c32d41a`
- Local branch: `feat/intellij-platform-spike`
- Clone mode: shallow clone, depth 1
- Working tree size: approximately 2.3 GB

GitHub repository creation is currently blocked by token permissions:

- `gh repo fork JetBrains/intellij-community --org singlr-ai --fork-name mast --clone=false`
- Result: `HTTP 403: Resource not accessible by personal access token`
- `gh repo create singlr-ai/mast --private --description "Mast IntelliJ Platform spike"`
- Result: `GraphQL: Resource not accessible by personal access token`

An owner token or manual GitHub repository creation is required before this branch can be pushed.

## Initial Judgment
The IntelliJ Platform path is credible and should continue. The repository is large, but it is buildable by design, has official GitHub Actions workflows, and already contains the IDE substrate Mast needs for Java-heavy teams.

The right architecture is a thin fork plus Mast-owned plugins:

- Keep JetBrains upstream intact as much as possible.
- Put SAIL surfaces in a bundled Mast plugin.
- Keep ACP/agent work in a separate Mast plugin unless JetBrains AI Assistant can be legally and technically reused.
- Defer deep product rebranding until buildability and plugin packaging are proven.

## Upstream Build Notes
The upstream README states:

- IntelliJ IDEA 2023.2 or newer is required for source development.
- JetBrains Runtime 25 without JCEF is required to compile.
- At least 8 GB RAM is expected.
- Full GitHub Actions product builds require larger runners because standard GitHub runners do not have enough disk.
- Full installer builds use:

```bash
./installers.cmd -Dintellij.build.target.os=current
```

- Tests use:

```bash
./tests.cmd --module <module> --test <pattern>
```

The repository contains product CI workflows:

- `.github/workflows/IntelliJ_IDEA.yml`
- `.github/workflows/PyCharm.yml`
- `.github/workflows/ide_build_and_upload.yml`

The shared IDE build workflow uses the custom runner label `Large-linux-x64-for-intellij-community` for Linux, Windows, and macOS SIT artifact builds, then uses `macos-latest` only for the final `.dmg` assembly step.

## Commands Run
Bootstrap:

```bash
git clone --depth 1 https://github.com/JetBrains/intellij-community.git /home/dev/workspace/mast
git remote rename origin upstream
git checkout -b feat/intellij-platform-spike
```

Repository checks:

```bash
git status --short --branch
git remote -v
git rev-parse --is-shallow-repository
git rev-parse HEAD
du -sh /home/dev/workspace/mast
```

Build entrypoint checks:

```bash
./tests.cmd --help
./bazel.cmd --version
```

Results:

- `./tests.cmd --help` succeeded.
- `./bazel.cmd --version` downloaded JetBrains-pinned Bazelisk and Bazel, then reported `bazel 9.1.0-jb_20260505_126 996316f`.

## Product Strategy
Recommended product structure:

1. Fork or mirror `JetBrains/intellij-community` into `singlr-ai/mast`.
2. Add a Mast product/plugin layer with minimal changes to IntelliJ core.
3. Start by bundling a SAIL plugin rather than rebranding the full IDE immediately.
4. Create a separate branding spec only after the SAIL plugin runs in a dev IDE.
5. Treat upstream syncability as a release criterion.

## SAIL Plugin Direction
The first Mast plugin should add a `Sails` tool window with:

- SAIL connection settings
- Sails list and status
- Specs grouped by lifecycle state
- Dispatch action
- Agent status, log, report, and handoff views

This maps naturally to IntelliJ tool windows and avoids recreating a custom shell.

## ACP Direction
There are two viable paths:

1. Use JetBrains AI Assistant ACP support if it can be depended on or installed in Mast under acceptable terms.
2. Build a Mast-owned ACP client/plugin that launches configured agents over stdio and renders thread state in a Mast tool window.

The second option is safer from a product-ownership standpoint. It avoids tying core Mast agent UX to a proprietary JetBrains plugin.

## Remote Workspace Direction
Remote development remains the highest-risk area.

Open questions:

- Can a Mast distribution legally and technically use JetBrains Remote Development/Gateway?
- Is the remote backend script present and usable in open-source builds?
- Can SAIL containers run the JetBrains backend with acceptable memory and disk use?
- Should Mast instead use local IntelliJ UI with SAIL providing remote execution, terminal, and agent runtime?

This needs a dedicated remote-workspace spike before the Zed-based Mast can be retired.

## Legal And Licensing Notes
The repository is Apache-2.0, but JetBrains distributions may include proprietary bundled plugins. For Mast:

- Audit bundled plugins before redistribution.
- Avoid JetBrains trademarks, icons, and product names in Mast branding.
- Keep JetBrains attribution and license files intact.
- Do not assume AI Assistant, Gateway, Remote Development, Code With Me, or other JetBrains service integrations are redistributable.

## Next Steps
1. Create `singlr-ai/mast` manually or grant a token that can create/fork repositories under `singlr-ai`.
2. Add `origin` to this local checkout:

```bash
git remote add origin https://github.com/singlr-ai/mast.git
```

3. Push the spike branch:

```bash
git push -u origin feat/intellij-platform-spike
```

4. Start `mast-intellij-sail-plugin-mvp` after the repository is pushable.
5. Keep `chorus` in maintenance mode while the IntelliJ path is proven.
