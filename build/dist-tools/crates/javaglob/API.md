# javaglob

Matches a jar entry name against a `java.nio` `glob:` pattern of the subset that the distribution plan uses. Rustdoc states
`JavaGlob::compile` and `JavaGlob::matches`.

## Supported subset

- The subset has literals, `*`, `**` and `{a,b}` groups. An alternative of a group can hold `*` and `**`.
- `compile` refuses `?`, `[`, `]`, `\`, a nested `{` and an unbalanced brace.
- The error of `compile` reads `glob "<pattern>": the distribution plan supports only *, ** and {a,b}, but the pattern has <problem>`. The problem names the character and its index, for example `'?' at 1`. A caller adds its context, for example `invalid include: {error:#}`.
- `matches` covers the whole name and is case-sensitive. It first removes one trailing `/` of a clean name, which has no repeated `/`.
- `*` matches zero or more characters other than `/`. `**` matches across `/` and does not match a line terminator, as the JDK does.

## Tests

`testdata/java-path-matcher.txt` holds the JDK answers that `testdata/RecordPathMatcher.java` recorded for the
constructs of the subset. Every recorded pattern stays inside the subset. The unit tests read the record through
`testkit::testdata_dir`: from `DT_TESTDATA_DIR` under Bazel, or from `CARGO_MANIFEST_DIR/testdata` under `cargo test`.

The globs of the plan files have their own record in `build/dist-tools/plan-corpus/testdata` of the ultimate root. The
plan corpus test requires a recorded case for each of them, so a new pattern in a plan file needs a new case in that
`RecordPathMatcher.java` and a new record.
