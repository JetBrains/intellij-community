// "Replace 'collect(toUnmodifiableList())' with 'toList()'" "false"
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
  public static void main(String[] args) {
    List<CharSequence> list = Stream.of(args).<caret>collect(Collectors.toUnmodifiableList());
  }
}