import static java.util.stream.Stream.concat;
import static java.util.stream.Stream.of;

class Test {
  {
    concat(of(""), of("")); 
  }
}