public class TestCase extends Zzz {
  public TestCase ttt(Zzz ooo) {
    return ooo.g() instanceof TestCase ? (TestCase) ooo.g() : <caret>
  }
}

class Zzz {
  Object g();
}