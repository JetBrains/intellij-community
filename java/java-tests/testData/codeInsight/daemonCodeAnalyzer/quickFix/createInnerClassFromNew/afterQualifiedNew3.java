// "Create inner class 'ArrayList'" "true"
public class Test {
  public static void main() {
    new B.ArrayList();
  }
}

class B {
    public static class ArrayList {
    }
}