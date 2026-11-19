# Running Tests Without Taking Over Your Screen

`scripts/hidden_desktop.py` runs a test on a hidden desktop, so that the IDE windows do not take the focus from the user. The container runs the Linux IDE. The hidden desktop runs the IDE of the OS of the machine. The script is for an agent that checks a change on the machine where the user works:

1. Edit the code.
2. Take an existing test, or write a one-shot test. Do not commit a one-shot test.
3. Run the test on the hidden desktop.
4. Read the result and the screenshots, and repeat.

The windows and the event loop are real, but no window reaches the screen. Each process that the test starts stays on the hidden desktop. This includes each IDE that IDE Starter starts, and the backend and the frontend in split mode.

| OS | The hidden desktop |
|----|--------------------|
| Windows | A separate Windows desktop. |
| Linux | An Xvfb display with a private session bus. The script needs `xvfb-run`, `xauth` and `dbus-run-session`. IDE Starter starts a window manager only when `fluxbox` and `xprop` are installed. |
| macOS | Unsupported. The script exits with code 2. |

Run the script from the repository root. After `--`, give the `tests.cmd` command of [Running Tests from Terminal](../SKILL.md#running-tests-from-terminal), without `USE_DOCKER_CONTAINER`. On Windows, use PowerShell or cmd.exe, because Git Bash rewrites `/c`.

```powershell
# Windows
python .agents/skills/driver-ui-tests/scripts/hidden_desktop.py --timeout 5400 -- cmd /c tests.cmd <arguments>
```

```bash
# Linux
python3 .agents/skills/driver-ui-tests/scripts/hidden_desktop.py --timeout 5400 -- ./tests.cmd <arguments>
```

- Use `tests.cmd`. It runs `bazel run`, so the Bazel client starts the test on the hidden desktop. Under `bazel test` or `bt.cmd`, the Bazel server starts the test, and the server can run on the user's desktop.
- Start the Bazel server before the run, for example with `./bazel.cmd info server_pid`. A server that the run starts belongs to the run: it stays on the hidden desktop, or it stops with the run.
- A build and a UI test take many minutes. Start the script as a background task when the agent can.
- `--timeout` is in seconds. A timeout and Ctrl+C kill the command and each of its processes. On Linux, SIGTERM and SIGHUP do the same. When the command exits, the script also kills each process that it left. On Windows, the processes also stop when another process kills the script.
- The output of the command passes through, and the exit code is the exit code of the command.
- The script needs Python 3.8 or later and no packages.
- A login can stop the run or reach the user's screen. A new browser opens on the hidden desktop, where nobody can answer it. A browser that runs already opens the page on the user's screen, and so does a UAC prompt. Ask the user to do each login that the run needs before the run, for example the authorization of a package repository.

## Limits on Windows

Xvfb takes OS input and screen captures, so a test on Linux needs no change. The Windows hidden desktop takes neither, so a test there has these limits:

- **Input:** set the system property `driver.robot.use.input.events` to `true`, as in [Enabling a registry flag at startup](../api.md#enabling-a-registry-flag-at-startup). The robot then posts clicks, keys, mouse moves and `dragAndDrop` to the IDE event queue. The default robot sends OS input, and OS input does not work on this desktop.
- **Input that is not posted:** `pressMouse`, `releaseMouse`, `moveMouseAndPress`, `selectAndDrag` and `getColor` still use OS input or screen pixels. To drag, use `dragAndDrop`.
- **Clicks:** click a component. The input-events robot cannot click an arbitrary screen point (AT-4884).
- **Screenshots:** a screen capture is black. `driver.takeScreenshot` then paints the IDE windows into `full_screen.png`, so the `beforeIdeClosed` screenshot of IDE Starter works too. A native OS dialog is not in that image. For one image of each window, call `driver.takeScreenshotOfAllWindows("<folder>")`.
