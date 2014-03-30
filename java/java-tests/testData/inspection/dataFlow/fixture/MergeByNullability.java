import org.jetbrains.annotations.Nullable;

abstract class Some {
  void foo(Object o1, Object o2, Object o3, Object o4, Object o5, Object o6, Object o7, Object o8, Object o9) {
    String p1 = unknown() ? bar(o1) : "";
    consume(p1 == null ? "" : p1);

    String p2 = unknown() ? bar(o2) : "";
    consume(p2 == null ? "" : p2);

    String p3 = unknown() ? bar(o3) : "";
    consume(p3 == null ? "" : p3);

    String p4 = unknown() ? bar(o4) : "";
    consume(p4 == null ? "" : p4);

    String p5 = unknown() ? bar(o5) : "";
    consume(p5 == null ? "" : p5);

    String p6 = unknown() ? bar(o6) : "";
    consume(p6 == null ? "" : p6);

    String p7 = unknown() ? bar(o7) : "";
    consume(p7 == null ? "" : p7);

    String p8 = unknown() ? bar(o8) : "";
    consume(p8 == null ? "" : p8);

    String p9 = unknown() ? bar(o9) : "";
    consume(p9 == null ? "" : p9);

  }

  abstract void consume(String s);

  abstract boolean unknown();

  @Nullable abstract String bar(Object o);
}