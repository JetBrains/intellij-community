---
name: Plugin Class Log
description: Requirements for the debug log of the plugin class loaders that -Dplugin.classloader.debug turns on, its records, and its cost.
style: plain-1
targets:
  - ../src/com/intellij/ide/plugins/cl/PluginClassLog.kt
  - ../src/com/intellij/ide/plugins/cl/PluginClassLoader.kt
  - ../../../tools/vm/crates/avl-vm/src/bench/pluginlog.rs
---

# Plugin Class Log

Status: Accepted
Date: 2026-10-19

## Summary

The plugin class log is a file that names the owner of each class that a plugin class loader defines. The system
property `plugin.classloader.debug=<file>` turns it on. The IDE writes the file, and a tool reads it. The start-up
bench `vm bench` is the reader in this repository. It joins the log with the JVM class-load log by the class name.

The log also tells which code woke each loader: the stack of the first class of the loader. It also tells the time of
the definition of each class, and the cost of the log itself.

A loader is the class loader of the main module or of one content module of a plugin.

## Goals

- A reader can tell the plugin and the content module of each defined class.
- A reader can tell the code that loaded the first class of each loader.
- The log adds the smallest cost that it can to a class load.

## Non-goals

- A format that a person reads. A tool reads the file.
- The thread of a class. The JVM class-load log of the reader gives it.
- A stack for each class. Only the first class of a loader has one.

## Requirements

- The file must be UTF-8 text with one record per line and a tab between the fields.
  [@test] ../testSrc/com/intellij/ide/plugins/PluginClassLogTest.kt
- The first line must be the header `#plugin-classes`, a tab, and the version `2`.
  [@test] ../testSrc/com/intellij/ide/plugins/PluginClassLogTest.kt
- Each loader must have one `L` record before its first `C` or `E` record.
  [@test] ../testSrc/com/intellij/ide/plugins/PluginClassLogTest.kt
- Each class that a loader defines must have one `C` record.
  [@test] ../testSrc/com/intellij/ide/plugins/PluginClassLogTest.kt
- Records of different threads must stay whole lines.
  [@test] ../testSrc/com/intellij/ide/plugins/PluginClassLogTest.kt
- The stack of the first class of each live loader must come as `S` records at the exit.
  [@test] ../testSrc/com/intellij/ide/plugins/PluginClassLogTest.kt
- A stack must skip the frames of the class-loading machinery and keep at most 40 frames.
  [@test] ../testSrc/com/intellij/ide/plugins/PluginClassLogTest.kt
- The last line must be the `#cost` trailer.
  [@test] ../testSrc/com/intellij/ide/plugins/PluginClassLogTest.kt
- A record after the exit must not reach the file.
  [@test] ../testSrc/com/intellij/ide/plugins/PluginClassLogTest.kt
- The IDE must write the file only when its buffer is full, on a define error, and at the exit.
  [@test] ../testSrc/com/intellij/ide/plugins/PluginClassLogTest.kt
- The reader must refuse an unknown version, and it must read a log without a header as the old shape.
  [@test] ../../../tools/vm/crates/avl-vm/src/bench/pluginlog/tests.rs
- Without the property, the IDE must load no class of the log. See [Testing / Local Run](#testing--local-run).

## Data & Backend

```
#plugin-classes	2
L	<loader id>	<module or ->	<plugin id>
C	<loader id>	<findClass nanos>	<class>
E	<loader id>	<class>	<message>
S	<loader id>	<class>.<method>(<file>:<line>)
#cost	<records>	<write nanos>	<stacks>	<stack nanos>
```

| Record | Meaning |
| --- | --- |
| `L` | A loader. The id is unique in the file. The module is `-` for the main module of a plugin. The plugin id is the last field, and it can hold a space. |
| `C` | A class that the loader defined. The nanos are the time of the definition in this loader, without the parent loaders. |
| `E` | A class that the loader failed to define. The message is on one line, without tabs. A reader does not count it as a class. |
| `S` | One frame of the stack of the first class of the loader, the top frame first. The file is `Unknown Source` when the class file has none. |
| `#cost` | The defined classes, the nanos of their records, the captured stacks, and the nanos of their capture. |

- The order of the `C` records of one loader is the order of their definitions.
- The `S` records of all loaders come after the other records, and before the trailer.
- A loader that the IDE unloaded before the exit has no `S` records. Its `L` and `C` records stay.
- A log of an IDE that did not exit normally can lack the `S` records and the trailer.

## Error Handling

- The IDE logs an error when it cannot open the file, and it runs without the log.
- A failed write to the disk loses records. It does not fail a class load.

## Testing / Local Run

No test checks that the log loads no class while it is off. Check it by hand after a change of the log or of its
callers. Build `//build:idea_dist`, and start it without `plugin.classloader.debug` and with
`-Xlog:class+load:file=<file>`. After the welcome screen shows, `<file>` must name no class
`com.intellij.ide.plugins.cl.PluginClassLog*`. A typed field of the log on the class-loading path breaks this rule
in compiled code only, so a short run can miss it. Let the IDE run until it is idle.

## Open Questions / Risks

- The stack of a first class keeps the classes of its frames until the exit or until its loader goes. So a plugin
  whose frame woke another loader can stay in memory after its unload.
