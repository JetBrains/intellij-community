---
name: debugging
description: Debug IntelliJ IDE failures.
---

# Debugging

## Documentation

- [Logger FAQ](../../../../docs/IntelliJ-Platform/4_man/Logger.md)
- [How to Investigate Freezes](../../../../docs/IntelliJ-Platform/4_man/Performance/How-To-Investigate-Freezes.md)
- [Exception Analyzing FAQ](../../../../docs/IntelliJ-Platform/4_man/Exception-Analyzing-FAQ.md)

## Searching idea.log

**Log Levels:**
- `FINE` = debug level (from `LOG.debug { }`)
- `INFO` = info level (from `LOG.info()`)

**Category Format:**
`#<abbreviated.package.path>.<ClassName>` for a class under `com.intellij`, `com.jetbrains` or `org.jetbrains`. The package path of another class stays in full.

Example: `com.intellij.openapi.wm.impl.status.IdeStatusBarImpl` → `#c.i.o.w.i.s.IdeStatusBarImpl`

**Search Example:**
```bash
# Debug logs from IdeStatusBarImpl
grep "FINE - #c.i.o.w.i.s.IdeStatusBarImpl" idea.log
```
