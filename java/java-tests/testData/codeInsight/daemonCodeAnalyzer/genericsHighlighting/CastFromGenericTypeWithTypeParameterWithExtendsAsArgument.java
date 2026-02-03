import java.util.Map;

class Test {
  public static <M extends Map<String, Integer>> void groupingBy(Supplier<M> mapFactory) {
    Supplier<Map<String, Integer>> mangledFactory = <warning descr="Unchecked cast: 'Test.Supplier<M>' to 'Test.Supplier<java.util.Map<java.lang.String,java.lang.Integer>>'">(Supplier<Map<String, Integer>>) mapFactory</warning>;
    System.out.println(mangledFactory);
  }

  interface Supplier<<warning descr="Type parameter 'G' is never used">G</warning>> {}
}

