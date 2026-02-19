class Test {
  enum Colors {
    RED,
    GREEN,
    BLUE,
    YELLOW,
  }
  public Test(Colors color) {
    if (color == Colors.RED || color == Colors.GREEN) {
      switch (color) {
        case RED -> System.out.println("Red");
        case <warning descr="Switch label 'BLUE' is unreachable">BLUE</warning> -> System.out.println("Blue");
        case GREEN -> System.out.println("Green");
        case <warning descr="Switch label 'YELLOW' is unreachable">YELLOW</warning> -> System.out.println("Yellow");
      }
    }
  }
}