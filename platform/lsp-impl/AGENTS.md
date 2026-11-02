# LSP Client

The LSP Client connects LSP servers to the IntelliJ Platform. In this file, "the LSP Client" means the `platform/lsp` API module and the `platform/lsp-impl` module. The `LspClient` class is different: one instance is one connection to one server.

By default, the LSP Client starts the server, sends requests when the user works in the editor, and shows the results. You can turn off each editor feature separately. Then you can use the LSP Client as a library: send requests to the server from your code and process the results.

## Integrations

We recommend one IntelliJ plugin for each integration. The plugin provides the server executable, implements `LspIntegrationProvider`, and registers it in the `com.intellij.platform.lsp.integrationProvider` extension point.

Users can also configure a server connection on the Settings page. A plugin can add a server with default settings to that page through the `LspIntegrationSettingsProvider`. Besides that, there's no imperative API to modify these user settings, though many integrations provide their own technology-specific settings.

There are three kinds of integration in our LSP Client:

1. A server for plain text files, or for files with flat PSI, for example TextMate files. Other editors work this way.
2. An auxiliary server for a language with full PSI support, for example a linter. The server does its own analysis and adds results to the PSI features.
3. A custom integration of a PSI language with a primary language server, for example WebStorm frameworks and TypeScript Go.

Kinds 2 and 3 can cause interference between subsystems and unexpected behavior. The LSP Client must support all our use cases and all servers at the same time. Sometimes this limits what it can do. For example, the LSP Client tries to combine results from many language servers and language plugins. An extension from another plugin can block this.

## Before you change the LSP Client

Do not blindly change the LSP Client to fix one integration. Writing code is cheap, but it is expensive to make sure that a change does not break other integrations, including plugins outside the monorepo. Some choices in the LSP Client look wrong but are deliberate.

1. Make sure that the problem is not a bug in the server. This can be difficult to establish, because the LSP specification is not always clear.
2. Fix the problem in the product plugin. Most customization points are in `LspClientDescriptor`. Each editor feature in `LspCustomization` has a `*Disabled` value. You can turn the feature off, register your own platform extension, and call the server through `LspClient`.
3. Prepare the separate the ticket for the user: project IJPL, subsystem "Core. LSP". Explain in detail what is wrong in the LSP Client, and link the specification if possible.

## Tests

Add tests for each change. The tests must not depend on real products or real language plugins. A product can stop using a part of the LSP Client and delete its tests. In that case nothing would test that part, so the LSP Client must have its own tests. Isolated tests are also easier to read and debug.

Use the programmable fake server in `tests/testSrc/common/`. Set it up with `fakeLspIntegrationFixture`.

## Coding guidelines

Please avoid putting any new logic in LspClientImpl file, because it's a central class and that already deals with plenty of concerns. Logic should be stored in any other file, Kotlin offers plenty of tools for that, either extensions on LspClientImpl can be created, like LspServerCapabilities.kt, or it's also ok to add another property to LspClientImpl, like LspDynamicCapabilities.kt

## Updating lsp4j

The lsp4j library modules are in `community/libraries/lsp4j/`. To update the library:

1. Change the version in the `.iml` files of all four modules: `lsp4j`, `jsonrpc`, `debug`, and `jsonrpc-debug`. Then run the JPS-to-Bazel generator, as the root `AGENTS.md` says.
2. Fix the compilation errors in the whole monorepo. Many products use lsp4j directly.
3. In an ultimate checkout, run the `generate-lsp4j-annotations` skill. It writes the nullability annotations in `community/lib/annotations/eclipse_lsp4j*/`. It reads the source JARs of the new version from `~/.m2/repository`, and it commits each annotations file separately.

The lsp4j types are part of the `platform/lsp` API. The list is in `platform/lsp/exposed-third-party-api.txt`. A breaking change in lsp4j can also break plugins outside the monorepo.
