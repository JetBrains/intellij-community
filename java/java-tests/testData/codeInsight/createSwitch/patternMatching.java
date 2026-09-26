class X {
  public static double getPerimeter(Shape shape) throws IllegalArgumentException {
    shape<caret>
  }
}
interface Shape { }
record Rectangle(double length, double width) implements Shape { }
record Circle(double radius) implements Shape { }