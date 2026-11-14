# prompt

Asks the questions of a dialog in a terminal. `inquire` draws each prompt on stderr, on macOS, Linux and Windows. The
crate is in this workspace because `@ddt` holds only the third-party crates of this workspace. The `update-ide` dialog
of the ultimate `dev-dist` tool uses it.

## Public items

| Item | Description |
|---|---|
| `select(&str, Vec<Item>, default: usize) -> Result<Option<usize>>` | A menu. The arrow keys move the cursor, typing filters the rows, and Enter takes the option. Returns the index, or `None` for Esc. |
| `confirm(&str, default: bool) -> Result<Option<bool>>` | A yes or no question. Returns `None` for Esc. |
| `text(&str) -> Result<Option<String>>` | One line of text, such as a path. Returns `None` for Esc. |
| `message(Tone, &str) -> String` | A message line with the mark of its tone, such as `✔` for a result. |
| `mark(Tone) -> &'static str` | The mark of a tone. |
| `struct Item { label, answer }` | One option: its row, and the text after the question once the user selected it. |
| `enum Tone { Info, Success, Warning, Error, Detail }` | The kind of a message. |

## Supported subset

- Ctrl+C at a prompt restores the terminal and ends the process. On macOS and Linux it ends with the default action of
  SIGINT. On Windows it ends with exit code 130.
- `NO_COLOR` turns the colors off. A Windows console without escape sequences gets no colors.
- A prompt needs a terminal. The caller checks stdin and stderr first.
