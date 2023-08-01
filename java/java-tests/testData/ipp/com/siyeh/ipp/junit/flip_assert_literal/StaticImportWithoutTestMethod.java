import static org.junit.Assert.assertTrue;

class StaticImportWithoutTestMethod {

  void t() {
    <caret>assertTrue(false);
  }
}