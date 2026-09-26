import java.util.stream.*;

class X {
  Object <weak_warning descr="Method 'test' is complex: data flow results could be imprecise">test</weak_warning>(Stream<String> s, Boolean b1, Boolean b2) {
    return s
      .flatMap(p1 -> IntStream.of(1, 2, 3)
                  .flatMap(p2 -> IntStream.of(4, 5)
                               .flatMap(p3 -> IntStream.of(6, 7)
                                            .flatMap(p4 -> Stream.of(b1, b2)
                                              .<error descr="Incompatible types. Found: 'java.util.stream.Stream<java.lang.Object>', required: 'java.util.stream.IntStream'">flatMap</error>(p5 -> Stream.of(b1, b2)
                                                .map(p6 -> null))))));
  }
}