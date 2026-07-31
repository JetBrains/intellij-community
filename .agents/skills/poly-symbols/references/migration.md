# PolySymbols migration

Part of [poly-symbols](../SKILL.md). This doc is about *changing* an existing PSI-based
integration to PolySymbols, not building a new one from scratch — read the main
[poly-symbols](../SKILL.md) skill first for the framework itself, and [testing.md](testing.md)
for the test-writing API this migration moves tests onto.

## The core principle: additive is a transient state, not a resting one

Per the main skill's ["additive, not authoritative"](../SKILL.md#the-rule-polysymbols-is-additive-not-automatically-authoritative)
rule, every real PolySymbols integration studied in this repo runs dual-track with legacy PSI for
some period. That's expected *during* a migration — but it is not a place to stop. The tell that a
kind has been left dual-track too long: duplicate Find Usages results (once from the legacy
`PsiReferenceContributor`-backed search, once from the Symbol-API path). If you see that, the fix
is to finish migrating that kind (Phase B below), not to suppress the duplicate.

## Phase A — preliminary tests migration

**Do this before touching any production reference-resolution code.** The goal is a test suite
that resolves through the Symbol API (falling back to classic PSI transparently for kinds not yet
migrated — see [testing.md](testing.md#symbol-api-first-classic-psi-fallback--the-load-bearing-design-point)),
so that later, per-kind production migrations (Phase B) have a pre-verified regression baseline and
never need the test itself to change again.

1. **Inventory.** `search_regex` the test tree for direct calls to the forbidden
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
   gap immediately if it's in scope. Do not let "the test infra migration is done" quietly become
   "we changed what these tests verify."

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
| `getElementAtCaret()` / `elementAtCaret` | `symbolAtCaret()?.toPsiElementOrNull()` |
| `findUsages(target: PsiElement)` / `testFindUsages(...)` / `testFindUsagesUsingAction(...)` | `usagesAtCaret()` / `usagesAtOffsetBySignature(signature)` |
| `renameElementAtCaret(newName)` / `renameElementAtCaretUsingHandler(newName)` / `testRename(...)` / `testRenameUsingHandler(...)` | `renameSymbolAtCaret(newName)` |
| `getReferenceAtCaretPosition(...)` / `getReferenceAtCaretPositionWithAssertion(...)` / `findSingleReferenceAtCaret()` | `resolveSymbolReference(signature)` / `symbolAtCaret()` |
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

A worked example of Phase A end to end, from the GDScript migration: `ResolveTestBase`'s dumper was
switched from `element.references` + `ref.resolve()` to `psiSymbolReferences()` +
`resolveReference()`; direct legacy-reference-class construction in
`ResolveNestedClassMethodsTest` and `RenameTest.renameElementAtCaret()` were replaced the same way;
dictionary-key tests (`GdDictionaryLuaStyleKeyTest`/`GdDictionaryStringKeyTest`, a reference kind
with **no** PolySymbols coverage planned at all) and TSCN↔GDScript cross-language rename tests
(`ScriptClassRenamingTest`/`ResourceFieldRenamingTest`, likewise no PolySymbols coverage yet) all
migrated cleanly with zero behavior change, proving the classic-fallback design works even for
kinds that will *never* get a PolySymbols implementation. Separately, the rewrite of
`ResolveTestBase` surfaced 4 genuinely new test failures purely by switching the resolve mechanism
on already-partially-migrated kinds (`GdRefIdRef`/`GdSetGetMethodIdRef`) — static-access filtering
and constructor-qualifier-chain resolution gaps that the classic-only dumper had never exercised.
Those were left as an open, explicitly-tracked decision rather than papered over — exactly the
step-5 discipline above.

## Phase B — per-kind production migration unit

Only start once Phase A's tests are green or their divergences are explicitly triaged. Migrate one
PSI element kind (or symbol kind) at a time; for each, do **all** of the following in the same
change:

1. Implement `PolySymbolOwnReferencesHost.buildOwnReferences()` on the host PSI class.
2. Delete the matching legacy `PsiReferenceContributor` + its `PsiReference` class.
3. Delete the corresponding `polySymbols.psiReferenceProvider` EP registration + provider class
   (own-references pre-empt EP-registered external references once non-empty — see
   [query-model.md](query-model.md#references--own-references-polysymbolownreferences) —
   so once a kind has own-references, its EP registration is dead code, not a supplement).
4. If declarations for that kind still lean on `PsiLinkedPolySymbol` purely as a bridge to the
   legacy PSI-reference mechanism you just deleted, switch them onto `PolySymbolDeclaredInPsi` + a
   hand-written `PolySymbolDeclarationProvider` instead — the bridge has no reason to exist once
   nothing needs it.
5. Update the Phase-A-ported test for that kind to assert fully through the Symbol API (drop any
   remaining plain-PSI fallback assertion for that kind specifically).

**Never leave a kind migrated-but-still-dual-track longer than one change.** That's exactly the
resting-dual-track state the "core principle" above warns against.

**Risk to check before dropping `PsiLinkedPolySymbol` for a kind**: it currently makes
raw-PSI-triggered Find Usages/Rename (invoked directly on a declaration, not via a resolved
reference) work "for free," by treating the linked element as a search target directly.
`PolySymbolDeclaredInPsi` does **not** do this automatically — it doesn't link the symbol to the
`PsiElement` for reverse lookups (see [query-model.md](query-model.md#declarations)). Before
removing the bridge for a kind, check whether any old-API `ReferencesSearch.search(PsiElement)`
caller (unused-code inspections, constructor-usage searchers, custom Find Usages handlers) depends
on it; if so, either add a `PsiLinkedPolySymbolProvider` for the reverse element→symbol lookup, or
migrate that caller too, before the same change lands.

**Verification loop**: run the specific test module after every atomic per-kind change. Don't batch
multiple kinds into one uncommitted change — if something regresses, you want it obvious which kind
caused it.

## Related skills

- **[poly-symbols](../SKILL.md)** — the framework, and the "additive, not authoritative" rule this
  migration exists to eventually resolve; see [testing.md](testing.md) for the test-writing API
  Phase A moves a test suite onto.
- **[symbols-api](../../symbols-api/SKILL.md)** — the `Symbol`/`PsiSymbolReference`/
  `PsiSymbolDeclaration` platform layer both phases build on.
