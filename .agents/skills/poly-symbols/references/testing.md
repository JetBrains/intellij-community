# PolySymbols testing

Part of [poly-symbols](../SKILL.md). For the mechanism these tests
exercise (query executor, scopes, references, declarations), see
[query-model.md](query-model.md). For migrating an *existing* PSI-based test suite onto this API
before migrating production code, see [migration.md](migration.md).

## Class hierarchy

`PolySymbolsTestCase(mode: HybridTestMode = BasePlatform) : HybridTestCase(mode)`
(`community/platform/polySymbols/testFramework/src/com/intellij/polySymbols/testFramework/PolySymbolsTestCase.kt`)
is the base. `HybridTestMode.BasePlatform` is a light/in-memory fixture (like
`BasePlatformTestCase`); `CodeInsightFixture` is heavyweight/real-files (needed when a test copies
real project files to disk — e.g. GDScript's SDK/`.godot` fixtures). Framework-specific bases layer
on top: `WebFrameworkTestCase(mode) : PolySymbolsTestCase(mode)` adds npm-module-style dependency
configuration and re-declares every `do*Test` with a `vararg modules: WebFrameworkTestModule`
parameter; `Angular2TestCase(testCasePath) : WebFrameworkTestCase()` adds TypeScript-service
parameterization. A language can also extend `PolySymbolsTestCase` directly with no intermediate
layer. For JUnit 5, `PolySymbolsFixture` (`polySymbolsFixture(...)`, `PolySymbolsFixture.kt` in the
same directory) replaces `PolySymbolsTestCase`. GDScript's `GdFixture(context, testCasePath) : PolySymbolsFixture(...)`
(`dotnet/Plugins/godot-support/gdscript/src/test/kotlin/com/jetbrains/godot/gdscript/GdFixture.kt`)
extends it directly, adding one convenience helper (`doResolveSymbolTest`) on top.

Every subclass supplies `testDataRoot`, `testCasePath`, `defaultExtension`, `defaultDependencies`.
`getTestDataPath()` is finalized as `"$testDataRoot/$testCasePath"`.

`doConfiguredTest(...)` (`PolySymbolsTestCase.kt:134`) is the one primitive every other `do*Test`
helper wraps: it configures the fixture (dir-mode copy or single-file), runs any
`PolySymbolsTestConfigurator`s, forces a roots-change rescan so `PolyContext` is recomputed, waits
for indexes, then runs the passed `CodeInsightTestFixture.() -> Unit` lambda, optionally diffing
against a gold file/directory.

## Cheat sheet — `PolySymbolsTestUtil.kt` extension functions

All of the following live in
`community/platform/polySymbols/testFramework/src/com/intellij/polySymbols/testFramework/PolySymbolsTestUtil.kt`.
**Always re-read this file before relying on a signature.**

| Function | Purpose |
|---|---|
| `CodeInsightTestFixture.symbolAtCaret(includePsiSymbols: Boolean = false): Symbol?` | The core caret-based resolver. Checks `PolySymbolDeclaration`s/own-references first. Only with `includePsiSymbols = true` does it fall back to wrapping a plain PSI named element/reference at caret as a `Psi2Symbol`. See "Symbol-API-first, classic fallback" below. |
| `CodeInsightTestFixture.polySymbolAtCaret(): PolySymbol?` | `symbolAtCaret() as? PolySymbol`. Use it when the symbol at caret must be a `PolySymbol`, not another `Symbol`. |
| `CodeInsightTestFixture.resolveSymbolReference(signature: String): Symbol` | Signature-based (throws if 0 or >1 matches) — the go-to for a single-assertion resolve test. |
| `CodeInsightTestFixture.multiResolveSymbolReference(signature: String): List<Symbol>` | Non-throwing variant. |
| `CodeInsightTestFixture.resolvePolySymbolReference(signature: String): PolySymbol` | Like `resolveSymbolReference` but asserts the result is a real `PolySymbol` (fails on a bare `Psi2Symbol`). |
| `CodeInsightTestFixture.resolveToPolySymbolSource(signature: String): PsiElement` | Resolves then asserts the symbol is `PsiLinkedPolySymbol` and returns its `linkedElement`. |
| `CodeInsightTestFixture.resolveReference(signature: String): PsiElement` / `multiResolveReference(signature): List<PsiElement>` | **Plain PSI** resolution (not Symbol-API-aware) — for asserting classic-PSI-only behavior, or in migration tests that need to show "resolves classically but not (yet) via Symbol API." |
| `PsiElement.psiSymbolReferences(): Collection<PsiSymbolReference>` | The element-level equivalent of classic `element.references` — own/EP-registered `PsiSymbolReference`s first, classic `PsiReference`s wrapped via `PsiSymbolService.asSymbolReference` otherwise. Use this (not `element.references`) when hand-walking a PSI tree in a test. |
| `Collection<PsiSymbolReference>.resolveToSymbols(): List<Symbol>` | Resolves a reference collection, unwrapping single-segment `PolySymbolMatch`es. |
| `Symbol.toPsiElementOrNull(): PsiElement?` | Unwraps either a `Psi2Symbol` (legacy resolve target) or a `PsiLinkedPolySymbol` (migrated kind) back to a `PsiElement`. The universal "get me the PSI, whichever mechanism answered" call. |
| `CodeInsightTestFixture.assertUnresolvedReference(signature, okWithNoRef, allowSelfReference)` | Negative-resolution assertion (Symbol API *and* classic). |
| `CodeInsightTestFixture.renameSymbolAtCaret(newName: String, searchCommentsAndText: Boolean = false)` | See "Symbol-API-first, classic fallback" below — this is the one to call from test code instead of the fixture's own `renameElementAtCaret`. |
| `CodeInsightTestFixture.usagesAtCaret(scope, usagesTestHelper): List<String>` / `usagesAtOffsetBySignature(signature, scope, usagesTestHelper)` | Find-usages, dumped as sorted strings. Falls back to classic `elementAtCaret`-based search unconditionally when no symbol resolves — use these instead of the fixture's own `findUsages(PsiElement)`/`testFindUsages`. |
| `CodeInsightTestFixture.findUsages(target: SearchTarget): Collection<Usage>` | The Symbol-API `SearchTarget` overload — the sanctioned replacement for the fixture's own `findUsages(PsiElement)`. |
| `CodeInsightTestFixture.checkUsages(signature, goldFileName, ...)` / `checkFileUsages(...)` | Gold-file-diffing wrappers around the above. |
| `CodeInsightTestFixture.checkGotoDeclaration(fromSignature, declarationSignature, expectedFileName)` / `checkJumpToSource(...)` / `checkGTDUOutcome(expectedOutcome, signature)` | Navigation-behavior assertions (via `GotoDeclarationOrUsageHandler2`), not resolved-symbol assertions — use these when you care about end-user navigation behavior rather than which `PolySymbol` subclass resolved. |
| `CodeInsightTestFixture.checkDocumentationAtCaret(fileSuffix, directory)` / `checkNoDocumentationAtCaret()` | Quick-doc gold-HTML diffing. |
| `CodeInsightTestFixture.checkLookupItems(...)` / top-level `doCompletionItemsTest(fixture, fileName, ...)` | Completion rendering/diffing engines — see "Completion" below. |
| `UsefulTestCase.enableIdempotenceChecksOnEveryCache()` | Call in `setUp()` for any completion (or completion-adjacent) test suite — forces the platform's cache-idempotence self-check on every computation instead of random sampling, catching non-idempotent `CachedValueProvider`s deterministically instead of flakily. |
| `CodeInsightTestFixture.moveToOffsetBySignature(signature)` / `PsiFile.findOffsetBySignature(signature)` | The universal `<caret>`-marker-in-a-substring caret-positioning primitive every helper above builds on. Signature strings don't need to exist verbatim with `<caret>` in the configured file text — only the marker is stripped before substring-matching. |

## Symbol-API-first, classic-PSI-fallback — the load-bearing design point

`psiSymbolReferences()`, `usagesAtCaret()` and `usagesAtOffsetBySignature()` try own/EP-registered
PolySymbol resolution first. When nothing answers, they fall back to classic PSI unconditionally,
with no flag. `symbolAtCaret()` falls back to a classic PSI named element/reference, wrapped as a
`Psi2Symbol`, only with `includePsiSymbols = true`. Practically: **the exact same test helper call keeps working unchanged**
whether the underlying reference kind is fully migrated to PolySymbols, mid-migration, or still
100% classic PSI. This is what lets a test suite be migrated onto this API *before* any production
migration happens (see [migration.md](migration.md)).

`renameSymbolAtCaret` renames the `linkedElement` of a `PsiLinkedPolySymbol` with a `RenameProcessor`.
When there is no symbol at caret, or a symbol of another type gives no rename target, it falls back to classic rename.
It finds the element with `TargetElementUtil.findTargetElement` and runs a `RenameProcessor`. So it
defers reference-vs-declaration disambiguation to the platform's own well-tested `TargetElementUtil`
machinery for purely-classic scenarios, rather than reimplementing it by hand. `usagesAtCaret()`
falls back to the fixture's `elementAtCaret`. This is a **deliberate, sanctioned** use of an
otherwise-forbidden fixture method (see [migration.md](migration.md) for the full forbidden-methods
list). **This exception is for shared test infrastructure, not for individual test code.** A test
should call `renameSymbolAtCaret`/`usagesAtCaret`, never `renameElementAtCaret`/`elementAtCaret` directly.

## Feature cookbook

**Resolve** — assert *which symbol subclass* resolved (useful while a symbol model is still being
built and multiple candidate classes could legitimately match):
```kotlin
private val gd: GdSdkFixture by gdSdkFixture("gdscript/reference")

@Test
fun testResolveTypeHintSdkClass() =
    gd.doResolveSymbolTest("var position_2d: <caret>Vector2", GdSdkClassSymbol::class.java, "Vector2")
```
(GDScript's `doResolveSymbolTest` calls `resolveSymbolReference(signature)`, unwraps `PolySymbolDelegate`
wrappers with `unwrapAllDelegates()`, then checks `assertInstanceOf(expectedClass, resolved)` and the
name. This short pattern is worth copying into the test base of a new language.)

**Navigation** — assert end-user-visible behavior instead, once the model is settled:
```kotlin
fun testExportAs() = doGotoDeclarationTest("exportAs: \"<caret>test\"")
fun testComponentStandardElementSelector() = doConfiguredTest {
  checkGTDUOutcome(GotoDeclarationOrUsageHandler2.GTDUOutcome.GTD)   // no PSI declaration target at all
}
```

**Completion** — `doLookupTest(...)` diffs rendered lookup items against gold `.items[.N].txt`
files (one lookup string per line, e.g. `ngOnInit (tailText='(){...}'; typeText='void'; priority=1.0; bold)`).

**Find usages**:
```kotlin
fun testPrivateComponentField() = doFindUsagesTest()
```
`Angular2PsiLinkedPolySymbolProvider`-style setups (a `PsiLinkedPolySymbolProvider` recovering a
`PolySymbol` for a raw declaration element) are exercised transparently through this same call.

**Rename**:
```kotlin
fun testRenameProperty() = doSymbolRenameTest("new_prop")
```

**Highlighting** — `doHighlightingTest(checkSymbolNames = true)` additionally validates
`PolySymbolHighlightingCustomizer`-driven symbol-kind highlighting via
`ExpectedHighlightingData.checkSymbolNames()`: mark each colored identifier inline with
`<symbolName descr="EXTERNAL_NAME">text</symbolName>` (`EXTERNAL_NAME` = the `TextAttributesKey`'s own
external id) directly in the source fixture - there's no separate gold file. Write the fixture
unmarked, run once to get a `FileComparisonFailedError` with the markers auto-inserted around every
symbol the real annotator actually colored, bake that back in, re-run to confirm.

**Prefer this over calling `PolySymbolHighlightingCustomizer.getSymbolTextAttributes()`/
`getSymbolKindTextAttributes()` directly from test code** - a direct call bypasses
`PolySymbolHighlightingAnnotator` entirely, so it can't catch EP-registration-order bugs (multiple
customizers compose first-non-null-wins), a customizer silently falling through to a kind-only/
host-class fallback when it shouldn't, or the "raw, unwrapped `nameMatchQuery` result" gotcha
described in the `PolySymbolHighlightingCustomizer` section of [query-model.md](query-model.md).

For a test base that predates `PolySymbolsTestCase`, the manual fallback is
`ExpectedHighlightingData(doc, false, true, true, false)`, then `checkSymbolNames()` and `init()` on it, then
`(fixture as CodeInsightTestFixtureImpl).collectAndCheckHighlighting(data)`. This is still the real pipeline,
without the `PolySymbolsTestCase` convenience wrapper.

**Documentation**: `checkDocumentationAtCaret()` diffs against `<name>.expected.html`.
`doLookupTest(checkDocumentation = true)` diffs per-completion-item docs against `<name>#<lookup string>.html`.

**Escape hatch** — when the `do*Test` sugar's file/caret assumptions don't fit (e.g. a synthetic,
non-physical file), drop to `doConfiguredTest { }` and call the underlying primitives directly.

## Gotchas

- **`Angular2CodeCompletionTest`** (`contrib/Angular/angular-tests/test/org/angular2/resharper/`)
  is an unrelated ReSharper-style legacy completion harness (`Angular2ReSharperCompletionTestBase`)
  — don't confuse it with real PolySymbols completion tests despite the similar name.
- **Own references (`PolySymbolOwnReferenceHost`, see [query-model.md](query-model.md#references--own-references-polysymbolownreferences))
  work unchanged with the generic helpers above.** GDScript's `GdRefElement` hierarchy and TSCN's
  `TscnNamedElement` both resolve via own references (`polySymbolOwnReferences { ... }`). TSCN's
  `ScriptClassRenamingTest`/`ResourceFieldRenamingTest`
  (`dotnet/Plugins/godot-support/gdscript/src/test/kotlin/com/jetbrains/godot/tscn/refactoring/rename/`)
  drive an own-references-backed rename purely through `CodeInsightTestFixture.renameSymbolAtCaret(...)`,
  with no own-references-specific test plumbing at all.

## Related skills

- **[symbols-api](../../symbols-api/SKILL.md)** — the underlying `Symbol`/`PsiSymbolReference`
  platform layer.
- **[writing-tests](../../writing-tests/SKILL.md)** — general JUnit conventions in this codebase.
- **[testing](../../testing/SKILL.md)** — running the resulting tests via `tests.cmd`.
