# distpath

The slash-path rules of the dev-distribution tools: the path inside a distribution, the jar entry name, the link target,
and the lexical functions `clean`, `dir` and `join`. The functions read no file and have no dependency except `anyhow`.
A host path is not in this crate, except for the input of `slash_path`.

`filemeta`, `jarpack`, `pluginpack`, `component` and the collector call these functions. A crate with its own error type
keeps the text of the refusal.

## Supported subset

The crate supports the paths and links that the payloads of the repository have. It refuses all other input with an
error that names the input.

| Refused input | Error |
| --- | --- |
| a distribution path that is empty, that holds `\`, `:` or NUL, or that has an empty, `.` or `..` segment | `invalid relative path` |
| a path or a link target that is not ASCII, or that holds `<`, `>` or `&` | `unsupported character`. For other text, `serde_json` writes the bytes that the former writer wrote, and ASCII case folding gives the former path identity. |
| a link target that resolves through another link. A cycle is also a chain. | `unsupported symbolic link chain` |
| a link target with an empty segment, for example `payload/` or `lib//payload` | `unsupported symbolic link target`. Java `Path` removes the extra slash, so a Java producer of the same tree cannot keep the spelling. |
| an empty, absolute or escaping link target, or one that holds `\`, `:` or NUL | `invalid symbolic link target` or `symbolic link escapes the directory` |
| two links with one identity, or a link below another link | `conflicting link destinations` |
| a jar entry name that is empty, absolute or not clean, that holds `\`, `:`, NUL, CR or LF, that is longer than 65535 bytes, or that is `__index__` | `unsafe entry name` |
| a portable path that is not a safe jar entry name | `unsafe relative path` |
| a portable path component with a trailing `.` or space, a control character, or one of `<>"\|?*` | `unsafe path component` |
| a portable path component whose base name is `CON`, `PRN`, `AUX`, `NUL`, `COM1`-`COM9` or `LPT1`-`LPT9`, in any case | `reserved path component` |

The checked-in dev plans, the tracked file names and the JCEF archives have no such name and no link chain. Their links
are relative file links with `..` segments and one directory link with a `./` prefix. No link in the git index has an
empty segment.

## Public items

| Item | Former counterpart | Description |
|---|---|---|
| `validate_path(&str) -> Result<()>` | `filemetadata.ValidatePath` | Accepts a relative slash path with no empty, `.` or `..` segment and no `\`, `:` or NUL. Refuses text outside the supported subset. |
| `check_supported_text(&str) -> Result<()>` | | Refuses text that is not ASCII or that holds `<`, `>` or `&`. |
| `path_identity(&str) -> Result<String>` | `filemetadata.PathIdentity` | The path with ASCII letters in lowercase. Two paths with one identity collide on a case-insensitive file system. Refuses text outside the supported subset. |
| `identity(&str) -> String` | | The same for a path that passed `check_supported_text`. |
| `clean_link_target(&str) -> String` | `filemetadata.CleanLinkTarget` | Removes `.` segments and repeated slashes from a relative target. Keeps `..`. Keeps an empty or absolute target. |
| `validate_links(&BTreeMap<String, String>) -> Result<()>` | `filemetadata.ValidateLinks` | Checks a set of links (link path to target) without file system access: escapes, aliases, a link below a link. Refuses a link chain and a target with an empty segment. |
| `validate_link_target(name: &str, target: &str) -> Result<()>` | | The target check of one link of `validate_links`. |
| `parent_of(&str) -> Option<&str>` | | The path before the last slash, or `None` for one segment. |
| `validate_entry_name(&str) -> Result<()>` | | Accepts a portable, relative jar entry name. The generated `__index__` is not a source entry. |
| `validate_relative_path(&str) -> Result<()>` | | Accepts a safe jar entry name that is also a portable file name on every host. |
| `clean(&str) -> String`, `dir(&str) -> String`, `join(&str, &str) -> String` | `path.Clean`, `path.Dir`, `path.Join` | The lexical path functions over slash paths. `join` takes two elements. |
| `slash_path(&Path) -> Option<String>` | | The components of a relative host path joined by `/`. `None` when a component is not UTF-8. The inventory walk of `filemeta` and the tree walks of `pluginpack` name their entries through it. |

`Result` is `anyhow::Result`. A refusal has no context, so `to_string()` and `{:#}` give the same text.
