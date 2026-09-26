import java.util.Arrays;
import java.util.stream.Stream;

public class InLambda {
  {
    Stream.of("a,b,c").flatMap(l -> Arrays.stream(l.split(","))<caret>)
  }

}
