---
name: symbols-api
description: Use IntelliJ Symbol API for declarations, references, search, rename, and navigation outside PolySymbols.
---

# Symbol API

`com.intellij.model.Symbol` (`community/platform/core-api/src/com/intellij/model/Symbol.java`) is
the platform's generic replacement for "resolve a `PsiReference` to a `PsiElement`". A `Symbol`
represents a semantic element in *some* model — a language, a framework, a database schema — and
decouples navigation/find-usages/rename/documentation from PSI. **A `Symbol` is not required to be
backed by a `PsiElement`, and it is incorrect to try to obtain one from a `Symbol`.**

Canonical examples from the docs: a Java local variable (backed by `PsiVariable`), a compiled class
from JDK stubs (not tied to any project), a Spring bean (created dynamically by framework support,
project-bound but not PSI-backed), a database column (defined by a data source, not PSI-backed and
not project-bound).

**Before hand-wiring any of the mechanisms on this page, look at
[poly-symbols](../poly-symbols/SKILL.md) first.** `PolySymbol : Symbol`, and the PolySymbols
framework exists specifically to save you from writing this boilerplate by hand — its query
executor, completion-provider base class, and default `searchTarget`/`renameTarget`/
`getDocumentationTarget`/`getNavigationTargets` implementations give you declarations, references,
completion, search, rename, and navigation with a fraction of the code this page documents.
Treat PolySymbols as the **default, go-to way to implement a new language or framework's symbol
support** — it is not limited to cross-language microsyntax sharing, that's simply the feature that
falls out for free once the query/pattern machinery exists. Reach for the raw `Symbol`/
`PsiSymbolReference` API on this page directly only when a symbol genuinely doesn't fit PolySymbols'
model: a single, self-contained resolve with no query/completion surface of its own (a
format-string placeholder resolved from one call expression, a markdown link label), or something
not project-bound / not PSI-backed at all (a compiled-class symbol from JDK stubs, a database
column) where the extra query/scope layer would add nothing.

## Contract and lifecycle

```java
public interface Symbol {
  @NotNull Pointer<? extends Symbol> createPointer();
  @Override boolean equals(Object obj);
  @Override int hashCode();
}
```

- **`equals`/`hashCode` must reflect semantic identity**, not object identity — the platform uses
  `Symbol` as a cache key and de-duplicates concurrently-computed instances that are `equal`.
- **Lifecycle is a single read action.** A `Symbol` is safe to pass between APIs within one read
  action, but must not be referenced across read actions. Call `createPointer()` while still valid,
  store the `Pointer`, and call `Pointer.dereference()` in the next read action to get a fresh (or
  the same, if still valid) instance.
- All of this applies unchanged to `PolySymbol`, which extends `Symbol`.

## Declarations

Model where a symbol is declared in a PSI tree via `PsiSymbolDeclaration`
(`community/platform/core-api/src/com/intellij/model/psi/PsiSymbolDeclaration.java`):
`getDeclaringElement()`, `getRangeInDeclaringElement()`, `getSymbol()`. A symbol can have zero
declarations (files — "only references"), one (a Java local variable), or several (a C# partial
class, a property key declared in multiple files).

Two ways to supply declarations:
- Register a `PsiSymbolDeclarationProvider` (`getDeclarations(element, offsetInElement)`) at EP
  `com.intellij.psi.declarationProvider`. The platform calls it for each `PsiElement` from the leaf
  at the caret up to the containing file, with `offsetInElement` as a hint (`-1` means "all
  declarations").
- Implement `PsiSymbolDeclaration` directly on the `PsiElement`.

## References — three kinds

References are modeled by `PsiSymbolReference`
(`community/platform/core-api/src/com/intellij/model/psi/PsiSymbolReference.java`):
`getElement()`, `getRangeInElement()`, `resolveReference(): Collection<? extends Symbol>` (empty
collection = unresolved), and `resolvesTo(target)` (override to short-circuit before a full
resolve, e.g. by comparing text first). For the common single-target case, extend
`SingleTargetReference`.

| Kind | When | Mechanism |
|---|---|---|
| **Own** | Reference is intrinsic to the language itself (e.g. `x` in `x * 2` referencing `var x = 42`) | `PsiElement.getOwnReferences()` |
| **External** | Reference is *not* recognized by the host language, contributed by another plugin (e.g. a filename string in `new File("users.txt")`) | Host implements `PsiExternalReferenceHost`; other plugins register a `PsiSymbolReferenceProvider` |
| **Implicit** | Reference only supports resolving *to* a target (navigation, hover doc) — not find-usages/rename starting from the target (e.g. `var` in `var x = new Person()`) | `ImplicitReferenceProvider` |

Language support should not assume external references are absent — they may be contributed by any
plugin. Own vs. external is a modeling choice about who "owns" the reference semantics, not a
technical distinction in `PsiSymbolReference` itself.

### External references — `PsiSymbolReferenceProvider`

Interface (`community/platform/core-api/src/com/intellij/model/psi/PsiSymbolReferenceProvider.java`):

```java
public interface PsiSymbolReferenceProvider {
  Collection<? extends PsiSymbolReference> getReferences(PsiExternalReferenceHost element, PsiSymbolReferenceHints hints);
  Collection<? extends SearchRequest> getSearchRequests(Project project, Symbol target);
}
```

Register via `PsiSymbolReferenceProviderBean` at EP `com.intellij.psi.symbolReferenceProvider`,
declaring `hostLanguage`, `hostElementClass`, `targetClass` (common supertype of resolved symbols),
`referenceClass` (defaults to `PsiSymbolReference`; narrow it so the platform can skip providers
that can't possibly answer a query for a more specific reference type), and `implementationClass`.

Worked examples in this repo:
- `community/java/java-impl/src/com/siyeh/ig/format/StringFormatSymbolReferenceProvider.java` —
  resolves `%s`/`{0}`-style placeholders inside `String.format`/`printf`/`MessageFormat` call
  arguments back to the corresponding call argument, complete with its own `Symbol`
  (`FormatArgumentSymbol`-style), `NavigatableSymbol`, and a `SearchTarget`/rename-capable design —
  read this file end to end as the reference implementation for "symbol lives inside a string
  literal, references live inside sibling call arguments."
- `community/jvm/jvm-analysis-impl/src/com/intellij/analysis/logging/resolve/LoggingArgumentSymbolReferenceProvider.kt` —
  the same idea for JVM logging framework `{}`/`{0}` placeholders across Java/Kotlin.
- Markdown link-label resolution (linked from the official docs as a third sample) follows the same
  shape one level simpler: label text in `[text][label]` resolves to the `[label]: url` definition.

### Implicit references — `ImplicitReferenceProvider`

(`community/platform/core-api/src/com/intellij/model/psi/ImplicitReferenceProvider.java`, EP
`com.intellij.psi.implicitReferenceProvider`):

```java
public interface ImplicitReferenceProvider {
  default PsiSymbolReference getImplicitReference(PsiElement element, int offsetInElement) { ... }
  default Collection<? extends Symbol> resolveAsReference(PsiElement element) { return emptyList(); }
}
```

Usually you only override `resolveAsReference` — the default `getImplicitReference` wraps a
non-empty result in an `ImmediatePsiSymbolReference`. This enables navigation and hover-link
highlighting on the target `Symbol`, but the platform will never find this "reference" via
find-usages or rename-from-target, because it was never indexed as a searchable reference. Called
for every element from the caret leaf up to the file — keep it cheap.

PolySymbols' `PsiPolySymbolReferenceProvider` is built on top of exactly this external-reference
mechanism, but replaces the manual `getReferences`/`getSearchRequests` plumbing with a single
`getReferencedSymbol(element): PolySymbol?` you implement once — resolution, search, and rename all
follow from the returned symbol. Prefer it over hand-writing `PsiSymbolReferenceProvider` unless you
have a concrete reason not to — see the "References" section of
[poly-symbols/references/query-model.md](../poly-symbols/references/query-model.md).

## Hooking a Symbol into platform features

A bare `Symbol` only gives you resolve. To light up the rest of the IDE, implement (or delegate to)
these on your `Symbol`/`PolySymbol`:

| Feature | Interface | Notes |
|---|---|---|
| Navigation | `NavigatableSymbol` (`community/platform/core-api/src/com/intellij/navigation/NavigatableSymbol.java`) | `getNavigationTargets(project)`; `SymbolNavigationService` helps build `NavigationTarget`s |
| Find usages | `SearchTarget`/`SearchTargetSymbol` (`community/platform/lang-impl/src/com/intellij/find/usages/api/SearchTarget.kt`) | `presentation()`, `usageHandler`, `maximalSearchScope`, `textSearchRequests`; register a factory at `com.intellij.lang.symbolSearchTarget` if you don't want the `Symbol` itself to implement it |
| Rename | `RenameableSymbol`/`RenameTarget` (`community/platform/lang-impl/src/com/intellij/refactoring/rename/symbol/RenameableSymbol.kt`) | or register a `SymbolRenameTargetFactory` at `com.intellij.rename.symbolRenameTargetFactory` |
| Documentation | `DocumentationTarget` | via `PsiSymbolReferenceService`/language-specific hookup |

`PolySymbol` (see [poly-symbols](../poly-symbols/SKILL.md)) already wires all four of these through
`searchTarget`/`renameTarget`/`getDocumentationTarget`/`getNavigationTargets` properties with
sensible defaults. This is the general pattern across this whole page: almost everything above is
what `PolySymbol` implements *for* you. Implement `PolySymbol` and get all four; implementing raw
`Symbol` means writing every row of the table above by hand for every symbol kind you add.

## Related

- **[poly-symbols](../poly-symbols/SKILL.md)** — start here for new language/framework symbol
  support. It's built on `Symbol` and is the intended default implementation path, not a
  specialized add-on — reach for the mechanisms on this page directly only when a symbol falls
  outside its model (see the decision note above).
- **[poly-context](../poly-context/SKILL.md)** — general-purpose, performance-optimized context detection; PolySymbols is one consumer, not a dependency.
- Official docs: [Symbols](https://plugins.jetbrains.com/docs/intellij/symbols.html),
  [Declarations and References](https://plugins.jetbrains.com/docs/intellij/declarations-and-references.html).
