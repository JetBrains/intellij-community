package pkg;

import static pkg.Point.point;

public class SomeClass {
  public Point getSomePoint() {
    return point(<caret>)
    //     ^^
  }
}