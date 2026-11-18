# PolySymbols migration

Part of [poly-symbols](../SKILL.md). [testing.md](testing.md) holds the test-writing API that this
migration moves tests onto.

## The core principle: additive is a transient state, not a resting one

Dual-track with legacy PSI is expected *during* a migration, but it is not a place to stop. The tell that a
kind has been left dual-track too long: duplicate Find Usages results (once from the legacy
`PsiReferenceContributor`-backed search, once from the Symbol-API path). If you see that, the fix
is to finish migrating that kind (Phase B below), not to suppress the duplicate.

## Phase A — preliminary tests migration

**Do this before touching any production reference-resolution code.** The goal is a test suite
that resolves through the Symbol API (falling back to classic PSI transparently for kinds not yet
migrated — see [testing.md](testing.md#symbol-api-first-classic-psi-fallback--the-load-bearing-design-point)),
so that later, per-kind production migrations (Phase B) have a pre-verified regression baseline and
never need the test itself to change again.

1. **Inventory.** Run `accio locate '<regex>' --mode regex --scope <test dir>/` for direct calls to the forbidden
   `CodeInsightTestFixture` methods (table below). Exclude any test suite that's already fully on
   `PolySymbolsTestCase`/named `*PolySymbol*` — those are tracked as already migrated, out of scope
   for this pass.
2. **Rewrite shared PSI-walking test infra first, if one exists.** If a shared base class walks
   PSI trees and calls classic `PsiReference.resolve()` directly (e.g. a "dump every reference and
   its resolve status" test base), switch it to `element.psiSymbolReferences()` +
   `PsiSymbolReference.resolveReference()` (both from
   [testing.md](testing.md#cheat-sheet--polysymbolstestutilkt-extension-functions)),
   preserving the exact gold-file output shape. This one change is what lets *every* test built on
   that base keep passing unchanged as production code later moves individual ref kinds from
   classic `PsiReferenceContributor` → EP `psiReferenceProvider` → own-references — the dumper
   doesn't care which mechanism answered, only whether something did.
3. **Replace each forbidden call site** per the mapping table below.
4. **Re-run every migrated test — sequentially, never concurrently.** Running multiple `tests.cmd`
   invocations at the same time corrupts the shared Bazel incremental-build dependency graph/string
   table. Symptoms actually seen: `java.io.IOException: Mapping for number N does not exist.
   Current string table size: M entries.` and `java.lang.IllegalStateException: Unable to find
   dependency '//.../..._test_lib.jar'`. If you hit either, it is almost certainly this — retry the
   same runs sequentially rather than debugging the code.
5. **Triage any behavior divergence explicitly. Never silently update a gold file just to make a
   migrated test pass.** The Symbol API consults any already-registered
   `polySymbols.psiReferenceProvider` EP provider *before* falling back to classic PSI. A test that
   previously only ever exercised the classic mechanism can therefore newly resolve through an EP
   provider that doesn't yet enforce the same correctness rules the classic mechanism did (common
   culprits: static-vs-instance access filtering applied only in completion and not in reference
   resolution; qualifier-chain resolution through a constructor call not fully modeled). That's a
   **real, pre-existing production gap being surfaced by the test-infra change**, not a bug in the
   migration. Decide explicitly, per case: update the gold file *and* record the gap somewhere
   durable, `@Ignore` the specific test/assertion with a comment pointing at the gap, or fix the
   gap immediately if it's in scope.

### Forbidden `CodeInsightTestFixture` methods

Each of these resolves its target via classic PSI/caret lookup directly, bypassing the Symbol API
entirely — a test calling one of them exercises exactly the pre-Symbol-API path this migration
exists to move off of, even after production code has migrated that kind to own-references:

`findSingleReferenceAtCaret`, `getReferenceAtCaretPosition`, `getReferenceAtCaretPositionWithAssertion`,
`testRename`, `testRenameUsingHandler`, `testFindUsages`, `testFindUsagesUsingAction`,
`findUsages(PsiElement)`, `getElementAtCaret`/`elementAtCaret`, `renameElementAtCaret`,
`renameElementAtCaretUsingHandler`.

Replacement mapping:

| Forbidden call | Replace with |
|---|---|
| `getElementAtCaret()` / `elementAtCaret` | `symbolAtCaret(includePsiSymbols = true)?.toPsiElementOrNull()` |
| `findUsages(target: PsiElement)` / `testFindUsages(...)` / `testFindUsagesUsingAction(...)` | `usagesAtCaret()` / `usagesAtOffsetBySignature(signature)` |
| `renameElementAtCaret(newName)` / `renameElementAtCaretUsingHandler(newName)` / `testRename(...)` / `testRenameUsingHandler(...)` | `renameSymbolAtCaret(newName)` |
| `getReferenceAtCaretPosition(...)` / `getReferenceAtCaretPositionWithAssertion(...)` / `findSingleReferenceAtCaret()` | `resolveSymbolReference(signature)` / `symbolAtCaret(includePsiSymbols = true)` |
| Direct instantiation of a legacy `PsiReference` class (e.g. `SomeReference(element).resolveDeclaration()`) | `resolveSymbolReference(element)`-style helpers — never construct the legacy reference class from test code |

Two sanctioned exceptions, both because they're the *execution* primitive rather than a
*resolution* mechanism:
- **`renameElement(element, newName)`** — the bare mechanical rename-execution call given an
  already-known element — is fine; it's exactly what `renameSymbolAtCaret` calls internally once
  it has resolved the correct target via the Symbol API (or deliberately deferred to classic
  resolution — see [testing.md](testing.md#symbol-api-first-classic-psi-fallback--the-load-bearing-design-point)).
  The rule above is about how the target is *found*, not this final step.
- **`findUsages(target: SearchTarget)`** (the `PolySymbolsTestUtil.kt` overload, distinct from the
  forbidden `findUsages(PsiElement)`) is the sanctioned Symbol-API replacement.

A worked example of Phase A step 2: GDScript's `GdResolveFixture`
(`dotnet/Plugins/godot-support/gdscript/src/test/kotlin/com/jetbrains/godot/gdscript/GdResolveFixture.kt`)
dumps every reference of a file through `psiSymbolReferences()` and `resolveReference()`.

## Phase B — per-kind production migration unit

Only start once Phase A's tests are green or their divergences are explicitly triaged. Migrate one
PSI element kind (or symbol kind) at a time; for each, do **all** of the following in the same
change:

1. Make the host PSI class's own interface implement `PolySymbolOwnReferenceHost`, then override
   `getOwnReferences()` on the impl class and build the result with the `polySymbolOwnReferences { ... }`
   DSL — see [query-model.md](query-model.md#references--own-references-polysymbolownreferences) for
   the exact shape.
2. Delete the matching legacy `PsiReferenceContributor` + its `PsiReference` class.
3. Delete the corresponding `polySymbols.psiReferenceProvider` EP registration + provider class:
   own references pre-empt it ([query-model.md](query-model.md#references--own-references-polysymbolownreferences)).
4. If declarations for that kind still lean on `PsiLinkedPolySymbol` purely as a bridge to the
   legacy PSI-reference mechanism you just deleted, switch them onto `PolySymbolDeclaredInPsi` + a
   hand-written `PolySymbolDeclarationProvider` instead — the bridge has no reason to exist once
   nothing needs it. **Expect to lose "usage search finds the raw PSI declaration" behavior when you
   do this** (`PolySymbolDeclaredInPsi`'s own doc comment says so explicitly) — if that matters for
   the kind you're migrating, budget for a hand-written `ReferencesSearch` executor to recover it.
   Example: GDScript's `GdOwnReferencesSearcher`, a generic word-search-then-resolve-own-references
   executor ([case-studies.md](case-studies.md#gdscript)).
5. Update the Phase-A-ported test for that kind to assert fully through the Symbol API (drop any
   remaining plain-PSI fallback assertion for that kind specifically).

**Risk to check before dropping `PsiLinkedPolySymbol` for a kind**: it currently makes
raw-PSI-triggered Find Usages/Rename (invoked directly on a declaration, not via a resolved
reference) work "for free," by treating the linked element as a search target directly.
`PolySymbolDeclaredInPsi` does **not** do this automatically — it doesn't link the symbol to the
`PsiElement` for reverse lookups (see [query-model.md](query-model.md#declarations)). Before
removing the bridge for a kind, check whether any old-API `ReferencesSearch.search(PsiElement)`
caller (unused-code inspections, constructor-usage searchers, custom Find Usages handlers) depends
on it; if so, either add a `PsiLinkedPolySymbolProvider` for the reverse element→symbol lookup, or
migrate that caller too, before the same change lands.

## Related skills

- **[symbols-api](../../symbols-api/SKILL.md)** — the `Symbol`/`PsiSymbolReference`/
  `PsiSymbolDeclaration` platform layer both phases build on.
