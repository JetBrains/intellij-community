# cli

The command line of the dev-distribution tools. Every tool reads its arguments through this crate, so every tool
accepts one form and refuses the rest with one text. The crate prints an error, and each tool keeps its exit codes.

## Supported subset

- An option is `--key=value` or `--flag`. The value starts after the first `=` and can be empty.
- A positional argument is an argument that does not start with `-`. Only a tool that calls `positionals` takes one.
- A tool names an option with its leading `--`, as the command line states it.

## Refusals

| Input | Refused by | Text |
|---|---|---|
| A short option such as `-x`, a bare `-` or `--`, or `--=value` | `parse` | `expected an option in the form --key=value, but got "<argument>"` |
| An argument that is not UTF-8 | `parse` | `the argument "<argument>" is not valid UTF-8` |
| A second occurrence of an option that is not a list | `take`, `require`, `flag` | `--<key> must be specified at most once` |
| `--<key>` without `=` for an option that takes a value | `take`, `take_all`, `require` | `--<key> takes a value, as in --<key>=<value>` |
| `--<flag>=<value>` for a flag | `flag` | `--<flag> takes no value` |
| An absent or empty required option | `require` | `--<key> is required` |
| An option that the tool did not take | `finish` | `unknown option: --<key>`, or `unknown options: --<a>, --<b>` sorted by name |
| A positional argument that the tool did not take | `finish` | `expected an option in the form --key=value, but got "<argument>"` |

An empty value of an optional option is `Some("")`. The tool decides whether it refuses it or reads it as absent.

## Public items

| Item | Description |
|---|---|
| `parse(impl IntoIterator<Item = OsString>) -> anyhow::Result<Options>` | Reads the command line after the program name. |
| `struct Options` | The options and the positional arguments that the tool did not take yet. |
| `Options::take(&mut self, &str) -> anyhow::Result<Option<String>>` | The value of an option that occurs at most once. |
| `Options::take_all(&mut self, &str) -> anyhow::Result<Vec<String>>` | Every value of a list option, in order. |
| `Options::require(&mut self, &str) -> anyhow::Result<String>` | The value of an option that occurs once with a value that is not empty. |
| `Options::flag(&mut self, &str) -> anyhow::Result<bool>` | Whether the flag occurs. |
| `Options::positionals(&mut self) -> Vec<String>` | The positional arguments, in order. |
| `Options::finish(self) -> anyhow::Result<()>` | Refuses the options and the positional arguments that the tool did not take. |
| `report(&mut dyn Write, &anyhow::Error)` | Prints `ERROR: {error:#}` and a line break. |
