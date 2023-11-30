class Shape {}
class Triangle  extends Shape { int calculateArea() { return 169660; } }
record Point(int i, int j) {}
enum Color { RED, GREEN, BLUE; }
record Rect(Point point1, Point point2) {
}


class Foo {
  void foo1(Object o) {
    switch (o) {
      case Integer i -> System.out.printf("int %d", i);
      case Long l -> System.out.printf("long %d", l);
      case Double d -> System.out.printf("double %f", d);
      case String s -> System.out.printf("String %s", s);
      case Triangle t when t.calculateArea() > 100 -> System.out.println("Large triangle");
      case Triangle t, <error descr="Invalid case label combination: 'null' can only be used as a single case label or paired only with 'default'">null</error> -> System.out.println("Small triangle or null");
      case Color c -> System.out.println("Color with " + Color.values().length + " values");
      case Point p -> System.out.println("Record class: " + "blah blah blah");
      case int[] ia -> System.out.println("Array of ints of length" + ia.length);
      case Rect(Point point1, Point (int i2, int j2)) when j2 > point1.i() -> System.out.println(i2 + j2);
      default -> System.out.println("Non-triangle");
    }
  }

  void foo2(Object o) {
    switch (o) {
      case Integer i:
        System.out.printf("int %d", i);
        break;
      case Long l:
        System.out.printf("long %d", l);
        break;
      case Double d:
        System.out.printf("double %f", d);
        break;
      case String s:
        System.out.printf("String %s", s);
        break;
      case Triangle t when t.calculateArea() > 100:
        System.out.println("Large triangle");
        break;
      case <error descr="Invalid case label combination: 'null' can only be used as a single case label or paired only with 'default'">null</error>, Triangle t:
        System.out.println("Small triangle or null");
        break;
      case Color c:
        System.out.println("Color with " + Color.values().length + " values");
        break;
      case Point p:
        System.out.println("Record class: " + "blah blah blah");
        break;
      case int[] ia:
        System.out.println("Array of ints of length" + ia.length);
        break;
      default:
        System.out.println("Non-triangle");
    }
  }

  int foo3(Integer i) {
    return switch (i) {
      case 1, 2, 3, 4, 5, <error descr="Invalid case label combination: 'null' can only be used as a single case label or paired only with 'default'">null</error>, default -> 42;
      case 42 -> 666;
    };
  }

  int foo4(Integer i) {
    return switch (i) {
      default -> 42;
    };
  }

  int foo5(Integer i) {
    return switch (i) {
      default -> 42;
    };
  }

  int foo6(Object obj) {
    return switch (obj) {
      case Point(int x, int y) -> x;
      default -> 0;
    };
  }
}

class Bar extends Foo {
  @Override
  void foo1(Object o) {
    switch (o) {
      case Integer i -> System.out.printf("int %d", i);
      case Long blahBlahBlah -> System.out.printf("long %d", blahBlahBlah);
      case Double d -> System.out.printf("double %f", d);
      case String ssssss -> System.out.printf("String %s", ssssss);
      case Triangle t when t.calculateArea() > 100 -> System.out.println("Large triangle");
      case null  -> System.out.println("Small triangle or null");
      case  Triangle t -> System.out.println("Small triangle or null");
      case Color c -> System.out.println("Color with " + Color.values().length + " values");
      case Point p -> System.out.println("Record class: " + "blah blah blah");
      case int[] ia -> System.out.println("Array of ints of length" + ia.length);
      case Rect(Point p1, Point (int x2, int y2)) when y2 > p1.i()  -> System.out.println(x2 + y2);
      default -> System.out.println("Non-triangle");
    }
  }

  @Override
  void <warning descr="Method 'foo2()' is identical to its super method">foo2</warning>(Object o) {
    switch (o) {
      case Integer i:
        System.out.printf("int %d", i);
        break;
      case Long blahBlahBlah:
        System.out.printf("long %d", blahBlahBlah);
        break;
      case Double d:
        System.out.printf("double %f", d);
        break;
      case String ssssss:
        System.out.printf("String %s", ssssss);
        break;
      case Triangle t when t.calculateArea() > 100:
        System.out.println("Large triangle");
        break;
      case <error descr="Invalid case label combination: 'null' can only be used as a single case label or paired only with 'default'">null</error>, Triangle t:
        System.out.println("Small triangle or null");
        break;
      case Color c:
        System.out.println("Color with " + Color.values().length + " values");
        break;
      case Point p:
        System.out.println("Record class: " + "blah blah blah");
        break;
      case int[] ia:
        System.out.println("Array of ints of length" + ia.length);
        break;
      default:
        System.out.println("Non-triangle");
    }
  }

  @Override
  int <warning descr="Method 'foo3()' is identical to its super method">foo3</warning>(Integer i) {
    return switch (i) {
      case <error descr="Invalid case label combination: 'null' can only be used as a single case label or paired only with 'default'">null</error>, 4, default, 1, 5, 3, 2 -> 42;
      case 42 -> 666;
    };
  }

  @Override
  int foo4(Integer i) {
    return switch (i) {
      default -> 13;
    };
  }

  @Override
  int foo5(Integer i) {
    return switch (i) {
      case 42 -> 42;
      default -> 42;
    };
  }

  @Override
  int foo6(Object obj) {
    return switch (obj) {
      case Point(int x, int y) -> y;
      default -> 0;
    };
  }
}