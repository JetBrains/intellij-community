# Java completion

The Java completion of `intellij.java.impl`.

## Test

```bash
./tests.cmd --module intellij.java.tests --test com.intellij.java.codeInsight.completion.NormalCompletionTest
```

## New completion

Write a new completion as a `ModCompletionItemProvider`, not as a `CompletionContributor`.

- Extend `JavaModCompletionItemProvider` in
  `java-impl/src/com/intellij/java/completion/modcommand/`
- Implement `provideItems(CompletionContext, ModCompletionResult)` and pass each item to the sink.
  `LabelReferenceItemProvider` is the smallest example. `KeywordCompletionItemProvider` and
  `ReferenceItemProvider` are the large ones.
- Register the class as `modcompletion.completionItemProvider` with `language="JAVA"` in
  `java-backend/resources/META-INF/JavaPlugin.xml`.
- `isEnabled()` follows the `ide.completion.modcommand` registry key, which is off. Override it and
  return `true` when the provider is stable.

## Structure

The classic contributors below stay for the code that is not migrated yet.

- `JavaCompletionContributor` runs the basic completion, and `JavaSmartCompletionContributor` the
  smart type completion.
- `JavaClassNameCompletionContributor` and `JavaMemberNameCompletionContributor` complete a name.
  `JavaGenerateMemberCompletionContributor` offers a generated member, and
  `JavaDocCompletionContributor` works inside a Javadoc comment.
- `JavaCompletionSorting` installs the relevance sorter, and `PreferByKindWeigher`,
  `PreferMostUsedWeigher`, `RecursionWeigher` and `LoggerWeigher` supply the order. Add a weigher
  for a new order rule.
- `commands/impl` and `ml` are all Kotlin, so a new file there stays Kotlin. The root package mixes
  Java and Kotlin, so a new file in it is Java.

## Test layout

- `java-tests/testSrc/com/intellij/java/codeInsight/completion/` holds the tests.
  `com/intellij/codeInsight/completion` of the same module holds file-path tests only.
- `LightFixtureCompletionTestCase` is the base class, and `NormalCompletionTestCase` adds the base
  path and the `doTest` helpers of the `normal` data set.
- The data is in `java-tests/testData/codeInsight/completion/<area>/`.

## Links

- Tree rules: `../../../../../../AGENTS.md` (Java)
