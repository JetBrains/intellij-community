class ErrorTest {
  private void m() {
    try {
      throw new Error();
    }
    catch (Exception x) {
      throw x;
    }
  }
}