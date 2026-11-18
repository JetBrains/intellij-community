# Finding log files

`idea.log` rotates: older segments are named `idea.1.log`, `idea.2.log`, etc.
Always glob `idea*.log` and sort by mtime to reconstruct chronological order or pick the most recent file.

## IDE Starter tests

IDE Starter writes each test's log to a dedicated directory:

```
<repo>/out/ide-tests/tests/<productCode>-<buildNumber>/<testName>/<class>/<index>_<method>/<launch>/frontend/log/idea*.log
```

Each level between `<testName>` and `log` is there only when it applies.
`buildNumber` is `LOCAL` for local runs and a numeric build ID on CI. Glob for both:

```python
from pathlib import Path

def find_log(test_name: str) -> Path:
    matches = sorted(
        (repo_root / 'out' / 'ide-tests' / 'tests').glob(
            f'IU-*/{test_name}/**/log/idea*.log'
        ),
        key=lambda p: p.stat().st_mtime,
    )
    if not matches:
        raise FileNotFoundError(
            f'No idea.log found for test "{test_name}". '
            'Run the test first, then re-execute this cell.'
        )
    path = matches[-1]  # most recently modified
    print(f'{test_name}: {path}')
    return path
```

Each IDE Starter test name maps to its own directory, so log files from different tests are already separated — no marker parsing is needed.

## Regular (non-Starter) tests

Regular tests that run from the IDE write to the shared system log directory:

```
system/test/testlog/idea*.log
```

The log is **not** cleared between test methods — all tests in a run share the same rotating file set.
To extract records for a specific test, delimit by `LogTestName` markers:

```
=======================
Started a test: ClassName::testMethodName
=======================
...log lines for this test...
=======================
The test finished successfully: ClassName::testMethodName
=======================
```

`LogTestName` (`com.intellij.testFramework.junit5.LogTestName`) is a JUnit 5 `InvocationInterceptor`.
Add `@ExtendWith(LogTestName::class)` to the test class, or it may already be registered through the `META-INF/services/org.junit.jupiter.api.extension.Extension` file of the test framework.
If `LogTestName` is not applicable (JUnit 4, no application context, etc.), emit equivalent markers manually with a `LOG.info` call at the start and end of the test body.

Parsing between markers:

```python
import re

def extract_test_section(log_text: str, test_display_name: str) -> str:
    """Return log lines between the Started and finished/failed markers for one test."""
    start = re.search(r'Started a test: ' + re.escape(test_display_name), log_text)
    if not start:
        return ''
    tail = log_text[start.end():]
    end = re.search(
        r'The test (?:finished successfully|failed): ' + re.escape(test_display_name),
        tail,
    )
    return tail[:end.start()] if end else tail
```
