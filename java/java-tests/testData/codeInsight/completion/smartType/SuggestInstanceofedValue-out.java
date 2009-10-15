public class TestCase extends Zzz {
  public TestCase ttt(Zzz ooo) {
    if (ooo instanceof TestCase) {
      return (TestCase) ooo;<caret>
    }
  }
}

class Zzz {
}