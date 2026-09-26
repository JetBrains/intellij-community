# Standalone code highlighting

The standalone Jewel module ships a lightweight, dependency-free syntax highlighter. It's mainly useful for non-IDE
apps that render Markdown or display code.

## How it works

`SimpleCodeHighlighter` implements `CodeHighlighter` from the `foundation` module. `ProvideMarkdownStyling` wires it
up by default, so fenced code blocks in rendered Markdown are highlighted automatically.

The rule engine works in three steps:

1. Each language maps to a `LanguageGrammar`, which is an ordered list of `TokenRule`s.
2. At each position in the input, the tokenizer picks the rule whose match starts earliest. Ties go to whichever
   rule appears first.
3. Each rule maps capture group indices to `TokenType` values, which `SyntaxHighlightColors` turns into styles.

Built-in grammars for languages are stored in `intui.standalone.code.highlighting.languages`.

## Sourcing patterns

Before writing regex patterns from scratch, check whether the IntelliJ TextMate bundles already cover the language.
They're at:

```
plugins/textmate/lib/bundles/<language>/syntaxes/<language>.tmLanguage.json
```

The Kotlin and Java grammars in this module came from:

```
plugins/textmate/lib/bundles/kotlin/syntaxes/kotlin.tmLanguage.json
plugins/textmate/lib/bundles/java/syntaxes/java.tmLanguage.json
```

The fields you need: `match` (single-line pattern), `begin`/`end` (block patterns like strings and comments), and
`captures` (group-to-scope mappings). Look for scopes that map to `TokenType` values: `keyword.*`, `string.*`,
`comment.*`, `storage.type`/`support.type`, `support.type.property-name` (data-language keys), `constant.*`,
`constant.numeric`, and `entity.name.function`.

Scopes with no `TokenType` equivalent are meant to be skipped: `meta.*` (structural context), `punctuation.*`, and
`invalid.*` (error highlighting, which this highlighter deliberately doesn't do).

When a scope could plausibly map to more than one `TokenType`, check what the TextMate plugin itself does before
deciding. `plugins/textmate/src/.../highlighting/TextMateDefaultColorsProvider.java` maps each scope prefix to a
`TextAttributesKey`; `platform/core-api/src/com/intellij/openapi/editor/DefaultLanguageHighlighterColors.java` gives
that key its fallback; and `platform/platform-resources/src/DefaultColorSchemesManager.xml` holds the values for both
Default and Darcula. Two mappings there are easy to get wrong from intuition: `storage.type` is a **keyword**, not a
type, so C's `int` and `size_t` are keywords; and `keyword.operator` is `DEFAULT_OPERATION_SIGN`, a key of its own that
neither default scheme colors, which is what `TokenType.OPERATOR` exists for.

If the IntelliJ bundle doesn't cover your language, check the
[shikijs/textmate-grammars-themes](https://github.com/shikijs/textmate-grammars-themes) repo or the
[VSCode extension marketplace](https://github.com/microsoft/vscode/tree/main/extensions).

tmLanguage grammars use [Oniguruma](https://macromates.com/manual/en/regular_expressions) RegEx library. See [Regex engine limitations](#regex-engine-limitations) for what breaks
when porting to Java's regex engine.

### Watch for patterns that rely on the grammar tree

A tmLanguage grammar is a set of regexes **plus a tree** deciding which of them are eligible where. We only have
the regexes: our rule list is flat and every rule is tried at every position. So a pattern that was safe upstream
because it could only be reached in one context may misfire once it's tried everywhere.

For example, CSS scopes `#tag-names` under `#selector` and `#property-values` under `#rule-list`, so they can
never both apply. Flattened, the ~20 words that are in both lists collide, and `display: table` colors `table` as
an HTML tag.

When a pattern depends on context, re-encode that context in the pattern, or leave the rule out. Either way, say
which you did in the file header, and mark any lookaround you add as yours rather than the bundle's.

Add a comment at the top of the file pointing to the source:

```kotlin
// Patterns adapted from plugins/textmate/lib/bundles/rust/syntaxes/rust.tmLanguage.json
```

## Adding a new language grammar

### 1. Create a grammar file

Create a file in:

```
int-ui/int-ui-standalone/src/main/kotlin/org/jetbrains/jewel/intui/standalone/code/highlighting/languages/
```

Name it `<Language>LanguageGrammar.kt` and declare a top-level `internal val`:

```kotlin
// Patterns adapted from plugins/textmate/lib/bundles/rust/syntaxes/rust.tmLanguage.json
internal val RUST =
    LanguageGrammar(
        name = "rust",
        aliases = listOf("rs"),
        rules = listOf(
            // Comments first — prevent keywords from matching inside them
            TokenRule.comment("""\/\/[^\n]*"""),
            TokenRule.comment("""\/\*[\s\S]*?\*\/"""),
            // Strings
            TokenRule.string(""""(?:[^"\\]|\\.)*""""),
            // Function declarations: fn <name>
            TokenRule.functionDeclaration("""\b(fn)\s+([A-Za-z_][A-Za-z0-9_]*)"""),
            // Keywords
            TokenRule.keyword("""\b(fn|let|mut|pub|use|mod|struct|enum|impl|trait|for|while|loop|if|else|match|return|self|super|crate|as|in|where|async|await|move|dyn|ref|type|const|static|unsafe|extern)\b"""),
            // Constants
            TokenRule.constant("""\b(true|false|None|Some|Ok|Err)\b"""),
            // Types
            TokenRule.type("""\b(String|str|i8|i16|i32|i64|i128|isize|u8|u16|u32|u64|u128|usize|f32|f64|bool|char|Vec|Option|Result|Box|Rc|Arc)\b"""),
            // Function call sites — after keywords so control flow keywords don't match
            TokenRule.functionCall("""\b([A-Za-z_][A-Za-z0-9_]*)\s*(?=\()"""),
            // Numbers
            TokenRule.number("""\b0x[0-9a-fA-F_]+\b"""),
            TokenRule.number("""\b0b[01_]+\b"""),
            TokenRule.number("""\b[0-9][0-9_]*(?:\.[0-9_]+)?(?:[eE][+-]?[0-9]+)?(?:_?[a-z0-9]+)?\b"""),
        ),
    )
```

The `name` and `aliases` values are matched case-insensitively against the language tag in fenced code blocks
(e.g., ` ```rust ` or ` ```rs `).
To add the proper extension names a language has, check both the `package.json` file in the TextMate bundle in the
plugins folder (e.g, `plugins/textmate/lib/bundles/<language>/package.json`) and find the field
`contributes.languages[].extensions`. Also check [GitHub's linguist languages file](https://github.com/github-linguist/linguist/blob/main/lib/linguist/languages.yml),
if you really want to be thorough.

### 2. Registering a LanguageGrammar

#### Registering as a built-in Jewel language
For languages that Jewel should use directly, add your `LanguageGrammar` to `BuiltInLanguageGrammars.kt` in the `all` list:

```kotlin
internal object BuiltInLanguageGrammars {
    val all: List<LanguageGrammar> by lazy { listOf(KOTLIN, JAVA, RUST) }
}
```

When opening the PR, run a sample of real code through the grammar and check every `TokenType` the language can
produce. Reading the patterns is not enough — the flat-model misfires described above only show up in the output.
Here's a very simple template for testing:

```
Keywords (control flow, declarations, modifiers):
<add examples>

Types:
<add examples>

Constants and language literals:
<add examples>

Builtins (support.type and support.function: stdlib types, well-known globals):
<add examples>

Method declarations + calls:
<add examples>

Property keys (JSON/YAML/TOML keys, CSS property names, HTML attributes):
<add examples>

Operators (`+`, `==`, `&&`, `|`, and word operators like `instanceof`, `typeof`, `in`):
<add examples>

Comments:
<add examples>

Numbers:
<add examples>

Strings:
<add examples>
```

It goes without saying but do leave out the sections a language doesn't have. Most have no property keys, and Kotlin has
no operator rules, for instance.

#### Registering as an additional language or overriding the LanguageGrammar for a built-in language

To highlight a language that isn't built in or override an existing one, just pass your `LanguageGrammar`
to `SimpleCodeHighlighter`:

```kotlin
val myGrammar = LanguageGrammar(
  name = "toml",
  rules = listOf(
    TokenRule.comment("""#[^\n]*"""),
    TokenRule.string(""""(?:[^"\\]|\\.)*""""),
    TokenRule.keyword("""\b(true|false)\b"""),
  ),
)

val highlighter = SimpleCodeHighlighter(
  colors = SyntaxHighlightColors.light(),
  additionalGrammars = listOf(myGrammar), // <--- Add your custom language grammars here!
)
```

### 3. Write tests

Create a test file in:

```
int-ui/int-ui-standalone-tests/src/test/kotlin/org/jetbrains/jewel/intui/code/highlighting/languages/
```

Name it `<Language>GrammarTest.kt`. Use `testColors` from `CodeHighlightingTestUtils` so your tests check the
tokenizer, not the color values:

```kotlin
internal class RustGrammarTest {
    private val highlighter = SimpleCodeHighlighter(testColors)

    private suspend fun highlight(code: String) = highlighter.highlight(code, "rust").first()

    @Test
    fun `line comment is colored as comment`() = runTest {
        assertEquals(Color.Gray, highlight("// a comment").colorAt(0))
    }

    @Test
    fun `fn declaration colors keyword and function name separately`() = runTest {
        val result = highlight("fn my_func() {}")
        assertEquals(Color.Red, result.colorAt(0))    // "fn" → keyword
        assertEquals(Color.Yellow, result.colorAt(3)) // "my_func" → function call
    }

    // ...
}
```

## Rule ordering

Order matters more than it looks. Put comments or strings in the wrong position and keywords will match inside them.
The general pattern:

| Order | Rule type | Reason |
|-------|-----------|--------|
| 1st | Comments | Must come first, or keywords and strings will match inside them |
| 2nd | Strings | Must come before keywords, or keywords inside strings get colored |
| 3rd | Function declarations | Matches `fn foo` before the keyword rule can grab just `fn` |
| 4th | Keywords | |
| 5th | Constants | |
| 6th | Types | |
| 7th | Function call sites | After keywords in Kotlin, so `if(` stays KEYWORD rather than FUNCTION_CALL |
| Last | Numbers | |

The table is a **starting point**, not a recipe. Every language has quirks you won't find until you test against real code.
Java's `functionCall`-first ordering is just one of them — run your grammar against actual snippets before assuming it works.

## Factory functions

`TokenRule.Companion` has factory functions for the common cases. Every factory takes a regex pattern string; the
pattern is compiled once when the `TokenRule` is created.

| Factory | Captures | Description |
|---------|----------|-------------|
| `comment(pattern)` | group 0 → COMMENT | Entire match colored as comment |
| `string(pattern)` | group 0 → STRING | Entire match colored as string |
| `keyword(pattern)` | group 0 → KEYWORD | Entire match colored as keyword (bold) |
| `type(pattern)` | group 0 → TYPE | Entire match colored as type |
| `constant(pattern)` | group 0 → CONSTANT | Entire match colored as constant |
| `number(pattern)` | group 0 → NUMBER | Entire match colored as number |
| `builtin(pattern)` | group 0 → BUILTIN | Entire match colored as builtin |
| `propertyKey(pattern)` | group 0 → PROPERTY_KEY | Entire match colored as a data-language key (JSON/YAML/TOML) |
| `operator(pattern)` | group 0 → OPERATOR | Entire match colored as an operator (`+`, `==`, `&&`, `\|`) |
| `functionCall(pattern)` | group 1 → FUNCTION_CALL | Group 1 must isolate the function name |
| `functionDeclaration(pattern)` | group 1 → KEYWORD, group 2 → FUNCTION_CALL | Group 1 = keyword, group 2 = name |
| `typeDeclaration(pattern)` | group 1 → KEYWORD, group 2 → TYPE | Group 1 = keyword, group 2 = type name |

If the regex has fewer groups than the factory expects, the missing groups are silently skipped and no span is emitted.

For anything not covered by the factories, construct `TokenRule` directly with a `captures` map:

```kotlin
TokenRule(
    pattern = """(\w+)\s*:\s*(\w+)""",
    captures = mapOf(1 to TokenType.CONSTANT, 2 to TokenType.TYPE),
)
```

## Regex engine limitations

Patterns run through Java's `java.util.regex`. Most tmLanguage patterns work as written. These do not:

- **POSIX character classes** (`[[:alpha:]]`, etc.) -> use `[a-zA-Z]` etc. instead.
- **`\p{Surrogate}`** -> use `\p{Cs}`.
- **Multi-codepoint escapes** (`\x{FEFF FFFE FFFF}`) -> split into one escape each.
- **Open-ended repetition** (`\d{,2}`) -> Oniguruma reads that as `{0,2}`; Java raises "Illegal repetition".
  Write the zero.
- **`#` and spaces inside a character class under `(?x)`** -> Java strips both, Oniguruma does not. The C
  bundle's printf rule contains `[#0\- +']`, where the `#` opens a comment that swallows the rest of the line
  and leaves the class unclosed. Escape them: `[\#0\-\ +']`.
- **Named backreferences**, **conditional patterns**, **subroutine calls** and **recursive patterns** -> not
  supported. Plain numeric backreferences (`\2`) are fine, which is what lets a heredoc rule match its own
  closing delimiter.
- **`\G`** -> anchors to the search start, so it pins a rule to the cursor and breaks earliest-match-wins. Drop
  it and re-encode whatever context it stood for.

Java is more permissive than the usual rule of thumb in several places, so test before rewriting a pattern. It
accepts variable-length lookbehind, including `+` and `*` (`(?<=fun\s+)x` both compiles and matches), and
alternations of differing length inside one (`(?<=^|[;&]|then )`). It also accepts `(?-mix:...)`, possessive
quantifiers, atomic groups, `\p{Cntrl}`, `\x{85}` and nested class unions like `[\x{85}[^abc]]`.

`additionalGrammars` is searched before the built-in list, so you can also use it to override a built-in grammar for
an existing language.
