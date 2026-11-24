# Java Debugger

## Tests

The sources are under `java/debugger/` in the public Community checkout and `community/java/debugger/` in the full checkout.
The main integration tests are private. In a Community-only checkout, report that the suite is unavailable.

In the full checkout, add regression tests beside related tests in `debugger/testSrc/` and test data in `debugger/testData/`.
Run the affected class from the root of the full checkout.

```bash
./community/tools/bt.cmd --module intellij.java.debugger.tests --filter <FQN>
```
