# Java completion

The Java completion of `intellij.java.impl`.

## Test

```bash
./tests.cmd --module intellij.java.tests --test com.intellij.java.codeInsight.completion.NormalCompletionTest
```

## Structure

- `JavaCompletionContributor` runs the basic completion, and `JavaSmartCompletionContributor` the
  smart type completion.
- `JavaClassNameCompletionContributor` and `JavaMemberNameCompletionContributor` complete a name.
  `JavaGenerateMemberCompletionContributor` offers a generated member, and
  `JavaDocCompletionContributor` works inside a Javadoc comment.
- `JavaCompletionSorting` installs the relevance sorter, and `PreferByKindWeigher`,
  `PreferMostUsedWeigher`, `RecursionWeigher` and `LoggerWeigher` supply the order. Add a weigher
  for a new order rule.
- 38 of the 128 files are Kotlin. `commands/impl` and `ml` are all Kotlin, so a new file there stays
  Kotlin. The root is mixed, so a new file in it is Java.

## Test layout

- `java-tests/testSrc/com/intellij/java/codeInsight/completion/` holds the tests.
  `com/intellij/codeInsight/completion` of the same module holds two file-path tests only.
- `LightFixtureCompletionTestCase` is the base class, and `NormalCompletionTestCase` adds the base
  path and the `doTest` helpers of the `normal` data set.
- The data is in `java-tests/testData/codeInsight/completion/<area>/`.

## Links

- Tree rules: `../../../../../../AGENTS.md` (Java)
