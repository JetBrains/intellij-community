# javaglob

Matches a jar entry name against a `java.nio` `glob:` pattern of the subset that the dev-dist plan uses.

## Dependency

```toml
javaglob.workspace = true
```

## API

```rust
pub struct JavaGlob; // Clone + Debug + Send + Sync

impl JavaGlob {
    pub fn compile(pattern: &str) -> Result<JavaGlob, GlobError>;
    pub fn matches(&self, name: &str) -> bool;
}

pub struct GlobError; // Clone + Debug + PartialEq + Eq + Display + std::error::Error
```

- The subset has literals, `*`, `**` and `{a,b}` groups. An alternative of a group can hold `*` and `**`.
- `compile` refuses `?`, `[`, `]`, `\`, a nested `{` and an unbalanced brace.
- `GlobError` displays as `glob "<pattern>": the dev-dist plan supports only *, ** and {a,b}, but the pattern has <problem>`. The problem names the character and its index, for example `'?' at 1`. Wrap it as the Go caller did, for example `invalid exclude: {error}`.
- `matches` covers the whole name and is case-sensitive. It first removes one trailing `/` of a clean name, which has no repeated `/`.
- `*` matches zero or more characters other than `/`. `**` matches across `/` and does not match a line terminator, as the JDK does.

## Go mapping

| Go | Rust |
|---|---|
| `javaglob.Compile(pattern) (Matcher, error)` | `JavaGlob::compile(pattern) -> Result<JavaGlob, GlobError>` |
| `Matcher.Match(name) bool` | `JavaGlob::matches(&self, name) -> bool` |

## Tests

`testdata/java-path-matcher.txt` holds the JDK answers that `testdata/RecordPathMatcher.java` recorded. Every recorded
pattern stays inside the subset.
A test reads every glob of the plan file corpus of the `planfile` crate, and it requires a recorded case for each.
Thus a new pattern in a plan file needs a new case in `RecordPathMatcher.java` and a new record.
The unit tests read the record at run time from `DDT_TESTDATA_DIR`, or from `CARGO_MANIFEST_DIR/testdata` under
`cargo test`. The corpus is at `../../planfile/testdata/corpus` from that directory, by a lexical path.
