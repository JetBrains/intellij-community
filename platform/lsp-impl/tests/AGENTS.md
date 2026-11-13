# LSP Client tests

See also the "Tests" section in `../AGENTS.md`. The fake server and its fixture are in `testSrc/common/`.

## Drive the feature the way the IDE does

- Use the methods of `codeInsightFixture`, or other platform test helpers and fixtures, the way the tests of other languages in the monorepo do. Look at how those tests check the same feature before writing a new helper. When a more specific fixture exists, prefer it, for example:
  - `codeVisionFixture` for code lenses
  - `registryKeyFixture` for registry values
  - `StructureViewComponentTestUtil` for the structure view
  - the `intellij.platform.navbar.testFramework` helpers for the navigation bar
- The LSP test framework in `platform/lsp/testFramework` has helpers that wait for the server, for example `checkHighlightingRetrying`, `awaitFileOpenedByLspServer` and `awaitDiagnosticsFromLspServer`. The fixtures of this module are in `testSrc/common/`.
- Don't create LSP Client classes in a test and call them: providers, caches, collectors, tree structures, customizers. That skips the extension point lookup, the threading and the UI code that a user goes through. Checking the type of an object that the platform returned is fine.
- When there is no helper for a feature, call the code that the platform runs for it (for example `StickyLinesCollector` for sticky lines), and leave a comment saying so.
- The client applies most results in the background, after the request that triggered them. Run the feature again and check until the result matches, with `waitUntilAssertSucceeds`, as `checkInlaysRetrying` in `LspInlayHintTest` and `LspDocumentColorTest` does for inlays.

## Assert what the IDE shows

- It's not enough to check that the server got a request or a notification. Check that the client handled the answer, using the data of the IDE feature: the usage view tree, the hierarchy tree, highlighters, crumbs, structure view nodes, the document text.
- Use the `expectRequest` predicate to check the request parameters, such as the URI and position.

## The fake server

- Declare one `fakeLspIntegrationFixture` per test class: in the outer class or in each `@Nested` class, never both.
- Keep the tests of one user-visible feature in one file, even when the feature uses more than one LSP method, for example `textDocument/completion` and `completionItem/resolve`. When some tests need other server capabilities, declare the fixture in each `@Nested` class, not in a new test file.
- A message that no expectation matches fails the test, except the document sync messages. Expect it, or configure the server capabilities so the client doesn't send it. Pass its method to `allowUnmatched` only when neither works, with a comment saying why.
- The client sends some requests right after `didOpen`, for example the diagnostic pull and the semantic tokens. Expect them on `unstartedServerSession()` before the file opens: create the file with `addFileToProject`, set up the expectations, then open it.
- An `expectRequest` that is never answered keeps the test running until it times out. When a test checks that no request was sent, `cancel()` the returned `Deferred` at the end.
- To check that no message was sent, send a marker message after the action and wait for the server to get it. Messages arrive in order, so an earlier message would have come first. Avoid fixed delays: a fixed delay can't fail when the message is only slow, so it hides bugs.
- Answer with `null` using `nullResponse()`, hold a request with `UnansweredRequest`, and throw `ResponseErrorException` to answer with an error.

## Other rules

- The test project is shared within a class, so give every test its own file name. A reused name gets the file renamed.
- Close the tool window content that a test opens (hierarchies, usage views) after the test.
- A test that fails because of a product bug stays in the file, marked `@Disabled("<what is wrong>")`. Check that it fails for that reason.
- Don't add `// given / when / then` comments, or comments that repeat the test name.

## Running

The tests run only under Bazel:

```
./bazel.cmd test @community//platform/lsp-impl/tests:tests_test --test_filter=com.intellij.platform.lsp.<Class> --test_output=errors
```

- Filter by the outer class name. A `Outer$Nested` filter, a comma list or a regex alternation matches nothing.
- Run `--runs_per_test=3` to check a new test for flakiness.
- All the test files build as one target, so one broken file stops every run. Run `./bazel.cmd build @community//platform/lsp-impl/tests:tests_test_lib` first.
