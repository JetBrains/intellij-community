# intellij.platform.eel.provider

Historically one of the first EEL modules. It cannot be removed: 200+ modules
depend on it.

Today it mainly holds project-level glue around EEL: resolving a `Project`'s
environment (`Project.getEelDescriptor()` / `getEelMachine()`), project-scoped
temp files and system folders, remote-dev host paths, and blocking wrappers for
non-coroutine callers.

Please do not add new code here by default:

- API goes to `intellij.platform.eel` / `intellij.platform.eel.nioFs`
  (they sit below `util`/`core` in the module graph, so the API cannot depend
  on `core` and must stay there).
- Implementations go to `intellij.platform.eel.impl` /
  `intellij.platform.eel.impl.base`.
