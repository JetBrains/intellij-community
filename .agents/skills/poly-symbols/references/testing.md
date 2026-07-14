# PolySymbols testing

Part of [poly-symbols](../SKILL.md). This is the test-writing companion: the base test case
hierarchy, the `CodeInsightTestFixture`/`PsiElement`/`Symbol` extension functions that make
Symbol-API-routed assertions concise, and a per-feature cookbook. For the mechanism these tests
exercise (query executor, scopes, references, declarations), see
[query-model.md](query-model.md). For migrating an *existing* PSI-based test suite onto this API
before migrating production code, see [migration.md](migration.md).

## Class hierarchy

`PolySymbolsTestCase(mode: HybridTestMode = BasePlatform) : HybridTestCase(mode)`
(`community/platform/polySymbols/testFramework/com/intellij/polySymbols/testFramework/PolySymbolsTestCase.kt`)
is the base. `HybridTestMode.BasePlatform` is a light/in-memory fixture (like
`BasePlatformTestCase`); `CodeInsightFixture` is heavyweight/real-files (needed when a test copies
real project files to disk — e.g. GDScript's SDK/`.godot` fixtures). Framework-specific bases layer
on top: `WebFrameworkTestCase(mode) : PolySymbolsTestCase(mode)` adds npm-module-style dependency
configuration and re-declares every `do*Test` with a `vararg modules: WebFrameworkTestModule`
parameter; `Angular2TestCase(testCasePath) : WebFrameworkTestCase()` adds TypeScript-service
parameterization. A language can also extend `PolySymbolsTestCase` directly with no intermediate
layer — GDScript's `GdPolySymbolsTestCase(testCasePath) : PolySymbolsTestCase(HybridTestMode.CodeInsightFixture)`
does this, adding one convenience helper (`doResolveSymbolTest`) on top.

Every subclass supplies `testDataRoot`, `testCasePath`, `defaultExtension`, `defaultDependencies`.
`getTestDataPath()` is finalized as `"$testDataRoot/$testCasePath"`.

`doConfiguredTest(...)` (`PolySymbolsTestCase.kt:129`) is the one primitive every other `do*Test`
helper wraps: it configures the fixture (dir-mode copy or single-file), runs any
`PolySymbolsTestConfigurator`s, forces a roots-change rescan so `PolyContext` is recomputed, waits
for indexes, then runs the passed `CodeInsightTestFixture.() -> Unit` lambda, optionally diffing
against a gold file/directory. Drop to `doConfiguredTest { }` directly whenever the sugar below
doesn't fit your scenario — see "Escape hatch" below.

## Cheat sheet — `PolySymbolsTestUtil.kt` extension functions

All of the following live in
`community/platform/polySymbols/testFramework/com/intellij/polySymbols/testFramework/PolySymbolsTestUtil.kt`.
**Always re-read this file before relying on a signature** — it is actively evolving; treat exact
parameter lists as illustrative, not gospel.

| Function | Purpose |
|---|---|
| `CodeInsightTestFixture.symbolAtCaret(): Symbol?` | The core caret-based resolver. Checks `PolySymbolDeclaration`s/own-references first, then unconditionally falls back to wrapping a plain PSI named element/reference at caret as a `Psi2Symbol`. See "Symbol-API-first, classic fallback" below. |
| `CodeInsightTestFixture.polySymbolAtCaret(): PolySymbol?` | `symbolAtCaret() as? PolySymbol` — use when you specifically want to assert *no* classic-PSI fallback happened. |
| `CodeInsightTestFixture.resolveSymbolReference(signature: String): Symbol` | Signature-based (throws if 0 or >1 matches) — the go-to for a single-assertion resolve test. |
| `CodeInsightTestFixture.multiResolveSymbolReference(signature: String): List<Symbol>` | Non-throwing variant. |
| `CodeInsightTestFixture.resolvePolySymbolReference(signature: String): PolySymbol` | Like `resolveSymbolReference` but asserts the result is a real `PolySymbol` (fails on a bare `Psi2Symbol`). |
| `CodeInsightTestFixture.resolveToPolySymbolSource(signature: String): PsiElement` | Resolves then asserts the symbol is `PsiLinkedPolySymbol` and returns its `linkedElement`. |
| `CodeInsightTestFixture.resolveReference(signature: String): PsiElement` / `multiResolveReference(signature): List<PsiElement>` | **Plain PSI** resolution (not Symbol-API-aware) — for asserting classic-PSI-only behavior, or in migration tests that need to show "resolves classically but not (yet) via Symbol API." |
| `PsiElement.psiSymbolReferences(): Collection<PsiSymbolReference>` | The element-level equivalent of classic `element.references` — own/EP-registered `PsiSymbolReference`s first, classic `PsiReference`s wrapped via `PsiSymbolService.asSymbolReference` otherwise. Use this (not `element.references`) when hand-walking a PSI tree in a test. |
| `Collection<PsiSymbolReference>.resolveToSymbols(): List<Symbol>` | Resolves a reference collection, unwrapping single-segment `PolySymbolMatch`es. |
| `Symbol.toPsiElementOrNull(): PsiElement?` | Unwraps either a `Psi2Symbol` (legacy resolve target) or a `PsiLinkedPolySymbol` (migrated kind) back to a `PsiElement`. The universal "get me the PSI, whichever mechanism answered" call. |
| `CodeInsightTestFixture.assertUnresolvedReference(signature, okWithNoRef, allowSelfReference)` | Negative-resolution assertion (Symbol API *and* classic). |
| `CodeInsightTestFixture.canRenameSymbolAtCaret(): Boolean` / `renameSymbolAtCaret(newName: String)` | See "Symbol-API-first, classic fallback" below — this is the one to call from test code instead of the fixture's own `renameElementAtCaret`. |
| `CodeInsightTestFixture.usagesAtCaret(scope, usagesTestHelper): List<String>` / `usagesAtOffsetBySignature(signature, scope, usagesTestHelper)` | Find-usages, dumped as sorted strings. Falls back to classic `elementAtCaret`-based search unconditionally when no symbol resolves — use these instead of the fixture's own `findUsages(PsiElement)`/`testFindUsages`. |
| `CodeInsightTestFixture.findUsages(target: SearchTarget): Collection<Usage>` | The Symbol-API `SearchTarget` overload — the sanctioned replacement for the fixture's own `findUsages(PsiElement)`. |
| `CodeInsightTestFixture.checkUsages(signature, goldFileName, ...)` / `checkFileUsages(...)` | Gold-file-diffing wrappers around the above. |
| `CodeInsightTestFixture.checkGotoDeclaration(fromSignature, declarationSignature, expectedFileName)` / `checkJumpToSource(...)` / `checkGTDUOutcome(expectedOutcome, signature)` | Navigation-behavior assertions (via `GotoDeclarationOrUsageHandler2`), not resolved-symbol assertions — use these when you care about end-user navigation behavior rather than which `PolySymbol` subclass resolved. |
| `CodeInsightTestFixture.checkDocumentationAtCaret(fileSuffix, directory)` / `checkNoDocumentationAtCaret()` | Quick-doc gold-HTML diffing. |
| `CodeInsightTestFixture.checkLookupItems(...)` / top-level `doCompletionItemsTest(fixture, fileName, ...)` | Completion rendering/diffing engines — see "Completion" below. |
| `UsefulTestCase.enableIdempotenceChecksOnEveryCache()` | Call in `setUp()` for any completion (or completion-adjacent) test suite — forces the platform's cache-idempotence self-check on every computation instead of random sampling, catching non-idempotent `CachedValueProvider`s deterministically instead of flakily. |
| `CodeInsightTestFixture.moveToOffsetBySignature(signature)` / `PsiFile.findOffsetBySignature(signature)` | The universal `<caret>`-marker-in-a-substring caret-positioning primitive every helper above builds on. Signature strings don't need to exist verbatim with `<caret>` in the configured file text — only the marker is stripped before substring-matching. |

## Symbol-API-first, classic-PSI-fallback — the load-bearing design point

`psiSymbolReferences()` / `symbolAtCaret()` / `usagesAtCaret()` / `usagesAtOffsetBySignature()` all
try own/EP-registered PolySymbol resolution first, and — unconditionally, no flag required —
transparently fall back to wrapping a classic PSI named element/reference as a `Psi2Symbol` when
nothing else answers. Practically: **the exact same test helper call keeps working unchanged**
whether the underlying reference kind is fully migrated to PolySymbols, mid-migration, or still
100% classic PSI. This is what lets a test suite be migrated onto this API *before* any production
migration happens (see [migration.md](migration.md)).

`renameSymbolAtCaret` has the sharpest instance of this pattern — read it once, it explains a lot:

```kotlin
fun CodeInsightTestFixture.renameSymbolAtCaret(newName: String) {
  val symbol = symbolAtCaret()
    ?.takeIf { PsiSymbolService.getInstance().extractElementFromSymbol(it) == null }
  if (symbol == null) {
    if (runCatching { getElementAtCaret() }.isSuccess) {
      renameElementAtCaret(newName)   // deliberate: defer to the classic mechanism
      return
    }
    else throw AssertionError("No Symbol at caret")
  }
  // ... real PolySymbol/RenameTarget/PsiLinkedPolySymbol path ...
}
```

A resolved symbol that's *merely* a `Psi2Symbol` wrapper (`extractElementFromSymbol` returns
non-null — no real `PolySymbol`/`RenameTarget`/`PsiLinkedPolySymbol` involved) is treated as "no
real symbol," and the function falls through to the fixture's own classic `renameElementAtCaret`.
This is a **deliberate, sanctioned** use of an otherwise-forbidden fixture method (see
[migration.md](migration.md) for the full forbidden-methods list) — it defers
reference-vs-declaration disambiguation to the platform's own well-tested `TargetElementUtil`
machinery for purely-classic scenarios, rather than reimplementing it by hand. `usagesAtCaret()`
does the analogous thing internally with `elementAtCaret`. **This exception is for shared test
infrastructure, not for individual test code** — a test should call
`renameSymbolAtCaret`/`usagesAtCaret`, never `renameElementAtCaret`/`elementAtCaret` directly.

## Feature cookbook

**Resolve** — assert *which symbol subclass* resolved (useful while a symbol model is still being
built and multiple candidate classes could legitimately match):
```kotlin
fun testResolveTypeHintSdkClass() =
    doResolveSymbolTest("var position_2d: <caret>Vector2", GdSdkClassSymbol::class.java, "Vector2")
```
(GDScript's `doResolveSymbolTest` wraps `assertInstanceOf(myFixture.resolvePolySymbolReference(signature), expectedClass)`
plus a name check — a two-line pattern worth copying into any new language's test base.)

**Navigation** — assert end-user-visible behavior instead, once the model is settled:
```kotlin
fun testExportAs() = doGotoDeclarationTest("exportAs: \"<caret>test\"")
fun testComponentStandardElementSelector() = doConfiguredTest {
  checkGTDUOutcome(GotoDeclarationOrUsageHandler2.GTDUOutcome.GTD)   // no PSI declaration target at all
}
```

**Completion** — `doLookupTest(...)` diffs rendered lookup items against gold `.items[.N].txt`
files (one lookup string per line, e.g. `ngOnInit (tailText='(){...}'; typeText='void'; priority=1.0; bold)`).
Call `enableIdempotenceChecksOnEveryCache()` in `setUp()` for any completion suite.

**Find usages**:
```kotlin
fun testPrivateComponentField() = doFindUsagesTest()
```
`Angular2PsiLinkedPolySymbolProvider`-style setups (a `PsiLinkedPolySymbolProvider` recovering a
`PolySymbol` for a raw declaration element) are exercised transparently through this same call —
`usagesAtCaret()` tries PolySymbol-based search first via `PsiLinkedPolySymbolReferenceSearcher`,
falling back to classic `elementAtCaret`-based search only if nothing resolves.

**Rename**:
```kotlin
fun testRenameProperty() = doSymbolRenameTest("new_prop")
```
Works identically whether the target is a real `PolySymbol` or a plain PSI element — the branching
lives inside the helper, not the test.

**Highlighting** — `doHighlightingTest(checkSymbolNames = true)` additionally validates
`PolySymbolHighlightingCustomizer`-driven symbol-kind highlighting via
`ExpectedHighlightingData.checkSymbolNames()`. For a test base that predates `PolySymbolsTestCase`,
the manual fallback is `ExpectedHighlightingData(doc, false, true, true, false).checkSymbolNames()`
+ `(fixture as CodeInsightTestFixtureImpl).collectAndCheckHighlighting(data)`.

**Documentation**: `checkDocumentationAtCaret()` (or `doLookupTest(checkDocumentation = true)` for
per-completion-item docs) diffs against `<name>.expected.html`.

**Escape hatch** — when the `do*Test` sugar's file/caret assumptions don't fit (e.g. a synthetic,
non-physical file), drop to `doConfiguredTest { }` and call the underlying primitives directly.
Worked example, GDScript's `GdSdkPolySymbolRefactoringTest.testFindUsagesFromSdkMethod`, simulating
`GdSdkPolySymbol.getNavigationTargets()`'s synthetic-file behavior via a user-data key on a
stand-in physical file:
```kotlin
doConfiguredTest(dir = true, configureFileName = "Node2D.gd") {
  (file as? GdFile)?.putUserData(GdSdkPolySymbol.SYNTHETIC_SDK_CLASS_KEY, "Node2D")
  checkGTDUOutcome(GotoDeclarationOrUsageHandler2.GTDUOutcome.SU)
  checkListByFile(usagesAtCaret(scope = null, usagesTestHelper = UsagesTestHelper.Default),
                  "findUsagesFromSdkMethod/usages.txt", false)
}
```

## Gotchas

- **`Angular2CodeCompletionTest`** (`contrib/Angular/angular-tests/test/org/angular2/resharper/`)
  is an unrelated ReSharper-style legacy completion harness (`Angular2ReSharperCompletionTestBase`)
  — don't confuse it with real PolySymbols completion tests despite the similar name.
- **`PolySymbolOwnReferencesHost` currently has zero test coverage anywhere in the repo**, and no
  implementer either, as of this writing. The helpers above *should* work unchanged against an
  own-references host by construction (they resolve via generic `PsiSymbolReference`/`Symbol`
  machinery, not EP-specific plumbing) — but this is unverified until a first real adopter proves
  it. Flag it as such rather than asserting it "just works."

## Related skills

- **[poly-symbols](../SKILL.md)** — the framework these tests exercise; see
  [migration.md](migration.md) for migrating an existing PSI-based test suite onto this API.
- **[symbols-api](../../symbols-api/SKILL.md)** — the underlying `Symbol`/`PsiSymbolReference`
  platform layer.
- **[writing-tests](../../writing-tests/SKILL.md)** — general JUnit conventions in this codebase.
- **[testing](../../testing/SKILL.md)** — running the resulting tests via `tests.cmd`.
