# Debugging

## Documentation

- [Logger FAQ](../docs/IntelliJ-Platform/4_man/Logger.md) - Frequently asked questions about logging
- [How to Investigate Freezes](../docs/IntelliJ-Platform/4_man/Performance/How-To-Investigate-Freezes.md) - Diagnosing IDE freezes
- [Exception Analyzing FAQ](../docs/IntelliJ-Platform/4_man/Exception-Analyzing-FAQ.md) - Analyzing and categorizing exceptions

## Searching idea.log

When debugging IntelliJ code, search `idea.log` using these patterns:

**Log Levels:**
- `FINE` = debug level (from `LOG.debug { }`)
- `INFO` = info level (from `LOG.info()`)

**Category Format:**
`#<abbreviated.package.path>.<ClassName>`

Example: `com.intellij.openapi.wm.impl.status.IdeStatusBarImpl` → `#c.i.o.w.i.s.IdeStatusBarImpl`

**Search Examples:**
```bash
# Debug logs from IdeStatusBarImpl
grep "FINE - #c.i.o.w.i.s.IdeStatusBarImpl" idea.log

# Info logs from IdeStatusBarImpl
grep "INFO - #c.i.o.w.i.s.IdeStatusBarImpl" idea.log

# All logs from a class
grep "#c.i.o.w.i.s.IdeStatusBarImpl" idea.log
```

## End-to-End Behavior

When fixing issues where a user-facing entry point (script, command, API) doesn't behave as expected:

1. **Trace the full execution chain** from entry point to actual executor before making changes
2. **Identify all layers** that handle the relevant behavior - each layer may swallow, transform, or ignore it
3. **Verify the fix at the entry point** the user actually invokes, not just at the layer you modified
4. **Add debugging output** if still unsure - add to all layers to trace the issue
