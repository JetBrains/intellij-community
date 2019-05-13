import java.util.stream.Stream;
public class Outer {

  class Inner { }

  private Inner[] innerArr;

  public Outer(Stream<Inner> numbers) {
    this.innerArr = numbers.toArray(Inner[]::new)<caret>;
  }
}