# Java

Rules for this directory and every directory below it. They override the root `AGENTS.md`.

56 more `intellij.java.*` modules live outside this tree, under `../plugins/` and `../platform/`.
This file does not reach them.

## Language

This overrides the "Prefer Kotlin for new files" rule of the
[code-style skill](../.agents/skills/code-style/SKILL.md).

- Write new code in modern Java, inside the language level of the module. The feature table is in
  [Modern Java](../.agents/skills/code-style/references/modern-java.md).
- Add a Kotlin file only for code that needs `suspend` or `Flow`.
- Exception: `debugger/` takes either language. Pick the one that fits the code you extend.
- Exception: keep the language of a package that is already all Kotlin, such as
  `codeInsight/completion/commands/impl` in `java-impl`.
- Keep an existing Java file in Java unless the task asks for a conversion.
- Kotlin in `intellij.java.impl`, `intellij.java.analysis` and `intellij.java.analysis.impl` runs
  with `x_explicit_api_mode = "strict"`. Declare the visibility and the return type of each public
  declaration.

## Language level

The default is Java 25, from `languageLevel` in `../.idea/misc.xml`.

Java 8: `intellij.java.psi`, `intellij.java.psi.impl`, `intellij.java.frontback.psi`,
`intellij.java.frontback.psi.impl`, `intellij.java.syntax`, `intellij.java.indexing.serializers`,
`intellij.java.rt`, `intellij.java.compiler.instrumentationUtil`,
`intellij.java.compiler.instrumentationUtil.java8`, `intellij.java.compiler.antTasks`,
`intellij.java.guiForms.compiler`.

Java 11: `intellij.java.langInjection.jps`.

The `LANGUAGE_LEVEL` attribute of the module `.iml` is the source, and no attribute means the
project default. Do not change it for a style change. `BUILD.bazel` mirrors it through a `kN`
kotlinc preset, and the generator owns that file.

## Packages

The root package of a module must be `com.<module-name>`, because a per-module classloader breaks on
a split package. A new package in `intellij.java.impl` goes under `com.intellij.java.impl`, or under
a prefix that `non-standard-root-packages.txt` already holds. That file asks for no new line, and a
new line also needs a new hash in `non-standard-root-package-hashes.txt`. Rename the package
instead.

`IntelliJProjectPackageNamesTest` and both files live under `tests/ideaProjectStructure/` in the
monorepo only. A community checkout cannot run the check, but the rule holds there.

## Modules

- Put a contract in the API module and the behavior in the impl module: `java-psi-api` with
  `java-psi-impl`, `java-analysis-api` with `java-analysis-impl`.
- `java-impl` holds the code insight: the completion, the intentions, and the refactorings.
- `java-frontend`, `java-backend` and `java-frontback-impl` serve the split mode. Read the
  `remote-dev-code-placement` skill before you move code between them.
- `intellij.java.tests` owns `testSrc` and `testData`.

## Tests

```bash
./tests.cmd --module intellij.java.tests --test <FQN>
```
