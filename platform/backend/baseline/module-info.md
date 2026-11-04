### Module `intellij.platform.backend.baseline`

This is a marker module for backend functionality that also runs in the standalone JetBrains Light process.
Modules which depend on this module can be loaded by a regular monolith IDE, by an IDE running as a backend in the remote development
scenario, by a language server, and by JetBrains Light in the `LIGHT_MONOLITH` mode. They won't be loaded by an IDE running in
frontend mode (JetBrains Client) or in a remote light mode.

The marker is an environment-configured module. `ProductModeEnvironmentModules` sets its availability:

| MONOLITH | BACKEND | LANGUAGE_SERVER | LIGHT_MONOLITH | FRONTEND | LIGHT_REMOTE | LIGHT_WITH_RD_CONNECTION |
|---|---|---|---|---|---|---|
| + | + | + | + | - | - | - |

This is `intellij.platform.backend` plus `LIGHT_MONOLITH`.
Use a RUNTIME dependency on this module to gate a module to these modes.
