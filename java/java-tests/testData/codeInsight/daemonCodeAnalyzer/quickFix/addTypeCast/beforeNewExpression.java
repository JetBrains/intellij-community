// "Cast expression to 'Scratch.Y'" "false"
class Scratch {

  public static class X { }

  public static class Y extends X { }

  public static void main(String[] args) {
    Y y = new <caret>X();
  }
}