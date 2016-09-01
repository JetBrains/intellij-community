import java.util.stream.Stream;

class Example {
  public static void main(String[] args) {
    Stream.of(args).filter(arg -> arg.startsWith("a"))
      .findFirst().ifPresent((args.length == 1 ? value -> {} : (value -> {
      System.out.println(<warning descr="Condition 'value == null' is always 'false'">value == null</warning> ? "none" : value);
    })));
  }
}
