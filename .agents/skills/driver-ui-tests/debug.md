# Debug a Failed Driver Test

Read this file when a test fails and you need the state of the IDE at the failure.

## Output Locations

After test failure, check:
- UI hierarchy: `out/ide-tests/tests/{IDE-version}/{TestName}/{test-method}/log/ui-hierarchy/ui.html`
- IDE log: `out/ide-tests/tests/{IDE-version}/{TestName}/{test-method}/log/idea.log`
- Screenshots: `out/ide-tests/tests/{IDE-version}/{TestName}/{test-method}/log/screenshots/`
- Exceptions: `out/ide-tests/tests/{IDE-version}/{TestName}/{test-method}/error/`

## Inspect the LIVE UI hierarchy, not just the post-mortem file

The `ui-hierarchy/ui.html` and `full-screen.png` written on failure are captured **after** `useDriverAndCloseIde` tears the IDE down — by then the session has ended and panels often revert to an empty/welcome state, so they can be misleading. Two better sources:

- **Heartbeat screenshot** `log/screenshots/001_heartbeat/` — captured mid-run, shows the real state during the wait.
- **Live UI hierarchy server** — while the IDE is up, the component tree is browsable at `http://localhost:<port>/api/remote-driver/` (the harness sets `-Dexpose.ui.hierarchy.url=true`; the port is logged at startup as `UI Hierarchy: http://localhost:<port>/api/remote-driver/`). To inspect interactively, **park the test** at the point of interest — temporarily raise a `waitFor` timeout (e.g. to `20.minutes`) — and `curl`/open that URL while the IDE stays alive. Each node exposes `class` (simple), `javaclass` (FQN, incl. `Outer$Inner` for inner classes), `visible_text`, and `accessiblename`; read these to build a reliable matcher instead of guessing from source.

## Common Issues

| Failure | Cause and fix |
|---|---|
| Element not found | The accessible name or the class in the query is wrong. Read the live UI hierarchy, see [find-elements.md](find-elements.md) |
| Multiple elements match | Scope the search to a parent element, see [find-elements.md](find-elements.md) |
| The first call fails at once, or the frame never comes | The test starts before the project opens. Put `waitForProjectOpen(Timeouts.M)` before the first call |

## Driving a real browser (Playwright)

Playwright runs in the **test JVM**, alongside the driver-driven IDE (both on localhost) — useful when the IDE's client is a web app/plugin. `page.onConsoleMessage { ... }` captures the page **and its iframes** (a strong diagnostic). Put custom screenshots/files under `context.paths.testHome.resolve("log")` so they are collected as test artifacts. See `plugins/figma/integrationTests` for a full example.
