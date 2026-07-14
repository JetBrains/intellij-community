---
name: poly-symbols
description: PolySymbols framework (com.intellij.polySymbols.PolySymbol) built on the Symbol API — implement multi-language "microsyntax" code insight (completion, references, docs, rename, find-usages) shared across HTML/CSS/JS and frameworks like Vue/Angular. Use when adding PolySymbol support for a new language, framework, or symbol kind, or when reviewing/extending an existing PolySymbols integration.
---

# PolySymbols

PolySymbols is a framework built on top of the platform's [Symbol API](../symbols-api/SKILL.md)
(`PolySymbol : Symbol`) for sharing symbol definitions and code-insight features (completion,
references, documentation, navigation, rename, find-usages, semantic highlighting) across
languages and frameworks. It was called "Web Symbols" in 2022.3–2025.1 and is still marked
experimental. Core module: `community/platform/polySymbols/` (`src`, `backend`, `src-web`).

Implementing a new integration typically means: build declaration/reference/completion providers
on the platform side as usual, plus a handful of PolySymbols contributor classes — find-usages,
documentation, and rename then work automatically through reference resolution to a `PolySymbol`.

## Interface cheat sheet

| Interface / class | Purpose |
|---|---|
| `PolySymbol` | Core element — `kind` (namespace + kindName) + `name`, plus optional icon/priority/modifiers/apiStatus/pattern |
| `PolySymbolScope` | A symbol that *contains* other symbols (an HTML element containing attributes, a JS class containing members) |
| `PolySymbolQueryExecutor` / `Factory` | Runs `nameMatchQuery`/`listSymbolsQuery`/`codeCompletionQuery` against contributed scopes |
| `PolySymbolQueryScopeContributor` | Registers `PolySymbolScope`s for a PSI location — the main extension point you implement |
| `PolySymbolQueryConfigurator` | Supplies `PolyContext` rules + symbol name-conversion rules |
| `PsiPolySymbolReferenceProvider` | Resolves a host `PsiElement` to a referenced `PolySymbol` — registered via EP, produces *external* references |
| `PolySymbolOwnReferences` | Alternative to `PsiPolySymbolReferenceProvider`: builds references to return from `PsiElement.getOwnReferences()` directly — a language's own canonical resolve, not EP-registered |
| `PolySymbolDeclarationProvider` | Supplies `PolySymbolDeclaration`s for a `PsiElement` (skip if `PsiLinkedPolySymbol` covers your case) |
| `PolySymbolsCompletionProviderBase` | Base class for a `CompletionProvider` that runs a `codeCompletionQuery` |
| `PolySymbolWithPattern` | A symbol expanded via a microsyntax pattern into a `PolySymbolMatch` |
| `PsiLinkedPolySymbol` | A symbol backed 1:1 by a real `PsiElement` — gets declarations/navigation/find-usages "for free" |
| `ReferencingPolySymbol` | Utility: makes one symbol kind stand in for/alias another kind |
| `PolyContext` | Gates scopes/configurators here, but is a general-purpose, performance-optimized context API usable well beyond PolySymbols — see **[poly-context](../poly-context/SKILL.md)** |
| Web Types | Static JSON symbol definitions — see **[references/web-types.md](references/web-types.md)** |

See **[references/query-model.md](references/query-model.md)** for the full query/scope/declaration/
reference/completion wiring, and **[references/patterns.md](references/patterns.md)** for the
pattern DSL and microsyntax matching.

## The rule: PolySymbols is additive, not automatically authoritative

**Every real integration studied in this repo keeps legacy PSI-based reference/completion code
running alongside PolySymbols — none resolve entirely through it.** This is not a migration
artifact you can ignore; it is the load-bearing design fact for anyone adding a new integration.
See **[references/case-studies.md](references/case-studies.md)** for full detail, but the pattern
repeats everywhere:

- **JS/TS/HTML/CSS** (the platform's own built-in support) grafts PolySymbols onto pre-existing
  extension points (`XmlElementDescriptorProvider`, `css.elementDescriptorProvider`,
  `JSReferenceExpression.resolve()`) and *explicitly steps aside* for standard/spec symbols —
  e.g. `HtmlElementSymbolDescriptorsProvider.getElementDescriptor()` returns `null` when the query
  result `hasOnlyStandardHtmlSymbols()`, deferring to the bundled RelaxNG HTML5 schema; the legacy
  `CssElementDescriptorProviderImpl` is kept registered with `order="last"` as the CSS fallback;
  `TypeScriptReferenceExpressionResolver` only consults PolySymbols for unqualified references
  inside injected/embedded expression hosts. Standard tag/attribute/property lists are **not**
  Web Types data — they're the pre-existing RelaxNG schema and webref CSS XML.
- **Vue and Angular** — the docs' own "heaviest adopters" — are close to fully PolySymbols-driven
  for their *template/markup* surface (components, directives/selectors, props/inputs, slots,
  events, modifiers), but Vuex, `<style src>`/`ref=`/file-path references, and CSS `v-bind()`
  bindings (Vue), and the Angular2 expression-language layer, `templateUrl`/pipe-name references
  (Angular) all still run through plain `PsiReferenceContributor`/`CompletionContributor` code with
  no PolySymbols involvement at all.
- **GDScript**, the newest integration, is explicitly dual-track by its own admission — its
  in-tree README (`dotnet/Plugins/godot-support/gdscript/.../polySymbols/README.md`) states the PSI
  and PolySymbols implementations both run today and produce **duplicate Find Usages results**
  until the legacy path is deleted. Local variables, parameters, for-loop bindings, resource-path
  references, and the entire TSCN language have no PolySymbols coverage yet.

**Actionable takeaway:** for every new integration or feature, decide *explicitly* whether
PolySymbols is the resolution path of record for that PSI element/feature, or merely a
supplementary layer contributing extra symbols on top of an existing mechanism. If a legacy
`PsiReferenceContributor`/`CompletionContributor` and a new PolySymbols registration can both fire
on the *same* host `PsiElement`, either gate one off (a feature flag, a `context` check, an early
`return null`/empty-list from whichever should defer) or accept and document the overlap — don't
assume the platform will pick one for you.

## Wiring checklist — adding PolySymbol support

1. **Opt the language in**: register `polySymbols.enableInLanguage language="..."` (EP
   `com.intellij.polySymbols.enableInLanguage`) so `PsiExternalReferenceHost`s in that language are
   eligible for PolySymbols reference resolution at all.
2. **Contribute scopes**: implement `PolySymbolQueryScopeContributor`
   (EP `com.intellij.polySymbols.queryScopeContributor`) — map PSI locations to `PolySymbolScope`s
   via the registrar DSL. This is almost always the first and most important class you write.
   Details: [references/query-model.md](references/query-model.md).
3. **Resolve references**: implement `PsiPolySymbolReferenceProvider` per host `PsiElement` type
   (EP `com.intellij.polySymbols.psiReferenceProvider`, one registration per `hostElementClass`).
   Alternative: implement `PsiElement.getOwnReferences()` directly via the `PolySymbolOwnReferences`
   builder when PolySymbols should be the language's own canonical resolve rather than an
   EP-contributed layer (e.g. replacing a legacy `PsiReferenceContributor`) — see
   [references/query-model.md](references/query-model.md#references--own-references-polysymbolownreferences).
   Don't register both for the same host — own references pre-empt external ones.
4. **Supply declarations**: if a symbol maps 1:1 onto a real `PsiElement` and lives purely in the
   PolySymbols model, implement `PolySymbolDeclaredInPsi` plus a `PolySymbolDeclarationProvider`
   (EP `com.intellij.polySymbols.declarationProvider`) that builds the symbol — this is the default.
   Reach for `PsiLinkedPolySymbol` + `polySymbols.psiLinkedSymbol host="..."` only when that same
   `PsiElement` must *also* keep working as a target for a **legacy, non-PolySymbols**
   find-usages/rename mechanism you're running alongside (a bridge for partial migrations, not a
   general shortcut — see [references/query-model.md](references/query-model.md#declarations)).
   For symbols with no backing `PsiElement` at all (synthetic/SDK symbols), write a
   `PolySymbolDeclarationProvider` by hand.
5. **Wire completion**: register an ordinary `completion.contributor` whose provider extends
   `PolySymbolsCompletionProviderBase` and calls `queryExecutor.codeCompletionQuery(...)`.
6. **Optional**: `PolySymbolQueryConfigurator` for context rules/name-conversion rules;
   `PolySymbolQueryResultsCustomizerFactory` to post-filter/remap query results;
   `polySymbols.webTypes` if standard-library symbols can be shipped as static JSON instead of code
   (see [references/web-types.md](references/web-types.md)); a `PolySymbolFramework`
   (`polySymbols.framework`) + `PolyContextProvider` (`polySymbols.context`) if you're introducing a
   new framework identity (see [poly-context](../poly-context/SKILL.md)).
7. Use `PolySymbolWithPattern`/the pattern DSL when the language/framework layers a microsyntax on
   top of base syntax (directive-style attribute names, event-modifier chains). See
   [references/patterns.md](references/patterns.md).

## Case studies

Four real integrations in this repo, each ending in an explicit PolySymbols-vs-legacy verdict — read
[references/case-studies.md](references/case-studies.md) for the full writeup with file:line
evidence.

| Integration | Where | One-line verdict |
|---|---|---|
| GDScript | `dotnet/Plugins/godot-support/gdscript/.../polySymbols/` | Dual-track: SDK/engine symbols are PolySymbols-first; user-code locals, resource refs, and TSCN are legacy-only, both paths run concurrently on some elements |
| JS/TS, HTML, CSS | `plugins/JavaScriptLanguage/web-platform/`, `community/xml`, `plugins/css` | PolySymbols is grafted onto legacy extension points and steps aside for anything standard/spec-defined |
| Vue | `contrib/vuejs/vuejs-backend/src/org/jetbrains/vuejs/web/` | Template/markup surface (components/directives/props/slots/events) is ~fully PolySymbols; Vuex, refs, CSS bindings are not |
| Angular | `contrib/Angular/angular-backend/src/org/angular2/web/` | Markup/selector surface is ~fully PolySymbols; the Angular2 expression-language layer and some file/pipe-name refs are not |

## Related skills

- **[symbols-api](../symbols-api/SKILL.md)** — the `Symbol` foundation this framework builds on.
- **[poly-context](../poly-context/SKILL.md)** — framework/environment detection.
- Official docs: [Poly Symbols](https://plugins.jetbrains.com/docs/intellij/polysymbols.html),
  [Implementing Poly Symbols](https://plugins.jetbrains.com/docs/intellij/polysymbols-implementation.html),
  [Poly Symbols Integration with Language Features](https://plugins.jetbrains.com/docs/intellij/polysymbols-integration.html).

## Supporting files

Load only when needed:

- [Query model](references/query-model.md) — query executor, scopes, contributors, configurators, declarations, references, completion, search/rename/nav hookups.
- [Patterns](references/patterns.md) — the pattern DSL, `PolySymbolMatch`/name segments, `ReferencingPolySymbol`, the Vue directive worked example.
- [Case studies](references/case-studies.md) — GDScript, JS/TS/HTML/CSS, Vue, Angular, in depth.
- [Web Types](references/web-types.md) — static JSON symbol definitions.
- [Testing](references/testing.md) — writing tests for a PolySymbols integration: the `PolySymbolsTestCase` hierarchy, the `PolySymbolsTestUtil.kt` cheat sheet, feature-by-feature recipes.
- [Migration](references/migration.md) — migrating a PSI-based feature to PolySymbols, tests-first: the preliminary-tests-migration recipe, forbidden `CodeInsightTestFixture` methods, the per-kind production migration unit.
