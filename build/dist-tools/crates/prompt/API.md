# prompt

Asks the questions of a dialog in a terminal. `cliclack` draws each prompt, each message line and each wait on stderr
with its stock theme, on macOS, Linux and Windows. The crate is in this workspace because `@dt` holds only the
third-party crates of this workspace. The `update-ide` dialog of the ultimate `dist` tool uses it.

## Public items

| Item | Description |
|---|---|
| `select(&str, Vec<Item>, default: usize) -> Result<Option<usize>>` | A menu of at most 12 rows on screen. The arrow keys move the cursor, typing filters the rows, and Enter takes the option. Returns the index, or `None` for Esc or Ctrl+C. |
| `confirm(&str, default: bool) -> Result<Option<bool>>` | A yes or no question: a menu of the rows "Yes" and "No", with the cursor on the default. Only Enter answers, and a typed letter does not. Returns `None` for Esc or Ctrl+C. |
| `text(&str) -> Result<Option<String>>` | One line of text, such as a path. The prompt refuses an empty line. Returns `None` for Esc or Ctrl+C. |
| `message(Tone, &str)` | Writes a message line with the symbol of its tone. A `Detail` line continues the frame without a symbol. |
| `intro(&str)` | Writes the first line of the frame of a dialog, with its title. |
| `outro(&str)` | Writes the last line of the frame of a dialog that reached its end. |
| `outro_cancel(&str)` | Writes the last line of the frame of a dialog that stopped before its end. |
| `wait(&str) -> Wait` | Starts a spinner with the text. |
| `Wait::done(self, &str)` | Stops the spinner and writes the text as the line of a finished step. |
| `struct Item { label, hint }` | One option: its row, and the text after the row under the cursor. The row stays below the question after the answer. |
| `enum Tone { Info, Success, Warning, Error, Detail }` | The kind of a message. |

## Supported subset

- Every prompt, message line and wait goes to stderr.
- On macOS and Linux the terminal has no echo and no signal keys while a prompt runs. `console` sets raw mode only
  while it reads one key, so a pasted path would echo into the frame between two reads. Ctrl+C between two reads is
  then the byte 0x03 in the input, and the next read cancels the prompt. The terminal is stdin when it is a terminal,
  else `/dev/tty`, as `console` reads it. The prompt restores the saved settings at its end. Windows needs no change,
  because `console` reads the console input events there, and they do not echo.
- After a prompt without an answer, `cliclack` writes the last line of the frame itself.
- Esc and Ctrl+C at a prompt both give `None`. The prompt cannot tell them apart. Ctrl+C outside a prompt, such as
  during a wait, ends the process as SIGINT does.
- `NO_COLOR` turns the colors off, also with an empty value. `CLICOLOR=0` turns them off, and a `CLICOLOR_FORCE`
  other than `0` turns them on.
- The colors follow stderr: a stderr without colors turns them off, also when stdout is a terminal.
- A prompt needs a terminal. The caller checks stdin and stderr first. A prompt without a terminal is an error.
