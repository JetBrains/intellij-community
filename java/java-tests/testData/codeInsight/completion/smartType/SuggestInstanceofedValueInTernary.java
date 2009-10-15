public class TestCase extends Zzz {
  public TestCase ttt(Zzz ooo) {
    return ooo.g() instanceof TestCase ? o<caret>
  }
}

class Zzz {
  Object g();
}