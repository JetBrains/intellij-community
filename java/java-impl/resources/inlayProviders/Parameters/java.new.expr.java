class Test {
  public static void main(String[] args) {
    new Duckling("Franklin", Color.YELLOW);
  }
}

class Duckling {
  String name;
  Color color;

  public Duckling(String name, Color color) {
    this.name = name;
    this.color = color;
  }
}