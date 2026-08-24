### bazel-rules

Contains Bazel rules for building IntelliJ Platform plugins.

#### ij_plugin rule

Defines `ij_plugin` Bazel rule that can be used to build IntelliJ Platform plugins.
This is a work in progress, API of the rule is subject to change, please do not start migrating your plugins to it yet.
See [IJPL-251444](https://youtrack.jetbrains.com/issue/IJPL-251444) for the current status.

The rule packages plugins by sending work requests to `ij-plugin-packager`, which runs as a multiplex
[persistent worker](https://bazel.build/remote/persistent), so all `ij_plugin` targets in a build share a single JVM.
`ij-plugin-packager/tests/smoke` contains a minimal plugin which is built to check that the rule and the worker work.