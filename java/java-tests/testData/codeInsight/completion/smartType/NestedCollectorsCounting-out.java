import java.util.stream.*;

class X {

  {
    Stream<String> stringStream = Stream.of("1", "2", "2");
    stringStream.collect(Collectors.groupingBy(s -> s, Collectors.counting())<caret>);
  }

}