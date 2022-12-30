import java.util.Iterator;
import java.util.Random;
import java.util.stream.Stream;
import org.jetbrains.annotations.Nullable;

public class ConsumedStream {
  public static void test1() {
    Stream<String> stream = Stream.of("x");
    stream.forEach(System.out::println);
    <warning descr="Stream has already been linked or consumed">stream</warning>.forEach(System.out::println);
  }

  public static void test2() {
    Stream<String> stream = Stream.of("x");
    Stream<String> stream2 = stream.filter(String::isEmpty);
    Stream<String> stream3 = <warning descr="Stream has already been linked or consumed">stream</warning>.filter(x -> !x.isEmpty());
  }

  public static void test3() {
    Stream<String> stream = Stream.of("x");
    for (int i = 0; i < 10; i++) {
      <warning descr="Stream might have already been linked or consumed">stream</warning>.forEach(System.out::println);
    }
  }

  public static void test4() {
    Stream<String> stream = Stream.of("x");
    stream.forEach(System.out::println);
    if (Math.random() > 0.5) {
      <warning descr="Stream has already been linked or consumed">stream</warning>.forEach(System.out::println);
    }
  }

  public static void test5() {
    Stream<String> stream = Stream.of("x");
    switch (new Random().nextInt(10)) {
      case 0:
        stream.forEach(System.out::println);
      case 1:
        <warning descr="Stream might have already been linked or consumed">stream</warning>.forEach(System.out::println);
    }
  }

  public static void test6() {
    Stream<String> stream = Stream.of("x");
    try {
      method1();
      stream.forEach(System.out::println);
    } finally {
      <warning descr="Stream might have already been linked or consumed">stream</warning>.forEach(System.out::println);
    }
  }

  private static void method1() {}

  public static void test7() {
    Stream<String> stream = Stream.of("x");
    try {
      stream.forEach(System.out::println);
    } catch (Exception e) {
      <warning descr="Stream has already been linked or consumed">stream</warning>.forEach(System.out::println);
    }
  }

  public static void test1E() {
    Stream<String> stream = Stream.of("x");
    int x = new Random().nextInt(10);
    if (x < 2) {
      stream.forEach(System.out::println);
    }
    if (x < 1) {
      <warning descr="Stream has already been linked or consumed">stream</warning>.forEach(System.out::println);
    }
  }

  public static void test2E() {
    Stream<String> stream = Stream.of("x");
    Stream<String> stream2 = stream.filter(String::isEmpty);
    double random = Math.random();
    Stream<String> stream3 = <warning descr="Stream has already been linked or consumed">stream</warning>.filter(x -> !x.isEmpty());
  }

  public static void test3E() {
    Stream<String> stream = Stream.of("x");
    double random = Math.random();

    if (random > 0.5) {
      stream.forEach(System.out::println);
    }

    if (random > 0.9) {
      <warning descr="Stream has already been linked or consumed">stream</warning>.forEach(System.out::println);
    }
  }

  public static void test6E() {
    Stream<String> stream = Stream.of("x");
    double random2 = Math.random();
    Stream<String> stringStream = stream.filter(t -> true);
    boolean parallel = stream.isParallel();
    double random3 = Math.random();
    method(stream);
    <warning descr="Stream has already been linked or consumed">stream</warning>.filter(t -> true);
  }

  public static void test7E() {
    Stream<String> stream = Stream.of("x");
    Stream<String> parallel = stream.parallel().filter(t->true);
    <warning descr="Stream has already been linked or consumed">stream</warning>.forEach(System.out::println);
  }

  public static void test8E() {
    Stream<String> stream = Stream.of("x");
    stream.forEach(System.out::println);
    Stream<String> parallel = stream.parallel();
    <warning descr="Stream has already been linked or consumed">parallel</warning>.forEach(System.out::println);
  }

  private static void method(Stream<String> stream) {
    Iterator<String> iterator = stream.iterator();
  }

  public static Stream<String> test9E() {
    Stream<String> stringStream = Stream.of("1");
    Stream<String> stringStream1 = stringStream.filter(t -> true);
    return <warning descr="Stream has already been linked or consumed">stringStream</warning>;
  }

  public static Stream<String> test10E() {
    Stream<String> stringStream = Stream.of("1");
    Stream<String> stringStream1 = stringStream.filter(t -> true);
    return <warning descr="Stream has already been linked or consumed">stringStream.sequential()</warning>;
  }

  public static void test11E() {
    Stream<Integer> integerStream = test11Em();
    Stream<Integer> integerStream1 = integerStream.<warning descr="Method invocation 'filter' may produce 'NullPointerException'">filter</warning>(t -> true);
    Stream<Integer> integerStream2 = <warning descr="Stream has already been linked or consumed">integerStream</warning>.filter(t -> true);
  }

  private static void test12E(Stream<Object> stream) {
    stream.forEach(System.out::println);
    String text = "noError";
    try {
      <warning descr="Stream has already been linked or consumed">stream</warning>.forEach(System.out::println);
    } catch (IllegalStateException e) {
      text = "IllegalStateException";
    } catch (RuntimeException e) {
      text = "RuntimeException";
    }
    if (<warning descr="Condition 'text.equals(\"IllegalStateException\")' is always 'true'">text.equals("IllegalStateException")</warning>) {
      System.out.println("error");
    }
  }

  private static void test13E(int x) {
    Stream<String> stream = Stream.of("x");
    if (x == 0) {
      stream.forEach(System.out::println);
    }
    <warning descr="Stream might have already been linked or consumed">stream</warning>.forEach(System.out::println);
  }

  @Nullable
  public static Stream<Integer> test11Em() {
    return null;
  }

  public static void test1N() {
    Stream<String> stream = Stream.of("x");
    if (Math.random() > 0.5) {
      stream.forEach(System.out::println);
    } else {
      stream.distinct().forEach(System.out::println);
    }
  }

  public static void test2N() {
    Stream<String> stream = Stream.of("x");
    stream = stream.filter(x -> !x.isEmpty());
    stream.forEach(System.out::println);
  }

  public static void test3N() {
    Stream<String> stream = Stream.of("x");
    System.out.println(stream.isParallel());
    stream.forEach(System.out::println);
  }

  public static void test4N() {
    Stream<String> stream = Stream.of("x");
    switch (new Random().nextInt(10)) {
      case 0:
        stream.forEach(System.out::println);
        break;
      case 1:
        stream.forEachOrdered(System.out::println);
    }
  }

  public static void test5N() {
    Stream<String> stream = Stream.of("x");
    for (int i = 0; i < 10; i++) {
      if (Math.random() > 0.5) {
        stream.forEach(System.out::println);
        break;
      }
    }
  }

  public static void test1NE() {
    Stream<String> stream = Stream.of("x");
    boolean b = Math.random() > 0.5;
    if (b) {
      stream.forEach(System.out::println);
    }
    if (!b) {
      stream.forEach(System.out::println);
    }
  }

  public static void test2NE() {
    Stream<String> stream = Stream.of("x");
    int x = new Random().nextInt(10);
    if (x < 2) {
      stream.forEach(System.out::println);
    }
    if (x > 5) {
      stream.forEach(System.out::println);
    }
  }

  public static Stream<String> test3NE() {
    Stream<String> stringStream = Stream.of("1");
    return stringStream;
  }

  public static void test4NE() {
    Stream<String> stringStream = Stream.of("1");
    stringStream.filter(t -> true);
  }
}
