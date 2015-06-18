import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

class Test {

  interface IntStream1 {
    Stream<Integer> map(IntFunction<Integer> mapper);
    IntStream1 map(IntUnaryOperator mapper);

    Stream<Integer> boxed();
  }

  void fooBar(IntStream1 instr){
    Supplier<Stream<Integer>> si = () -> instr.<error descr="Ambiguous method call: both 'IntStream1.map(IntFunction<Integer>)' and 'IntStream1.map(IntUnaryOperator)' match">map</error> ((i) -> (( <error descr="Operator '%' cannot be applied to '<lambda parameter>', 'int'">i % 2</error>) == 0) ? i : -i).boxed();
    System.out.println(si);
    Supplier<Stream<Integer>> si1 = () -> instr.map <error descr="Ambiguous method call: both 'IntStream1.map(IntFunction<Integer>)' and 'IntStream1.map(IntUnaryOperator)' match">(null)</error>.boxed();
    System.out.println(si1);
  }
}

class TestInitial {
  void fooBar(){
    Supplier<Stream<Integer>> si = () -> IntStream.range(0, 20).map((i) -> ((i % 2) == 0) ? i : -i).boxed();
    System.out.println(si);
  }
}


