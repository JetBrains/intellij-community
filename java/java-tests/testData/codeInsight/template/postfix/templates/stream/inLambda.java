import java.util.stream.Stream;

public class InLambda {
  {
    Stream.of("a,b,c").flatMap(l -> l.split(",").stream<caret>)
  }

}
