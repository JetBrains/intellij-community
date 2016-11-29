import java.util.stream.Stream;

class Test {
  private static class Destination{ }

  private final Destination destination = new Destination();

  public void main(Stream<String> stream, Test ref){
    stream.filter(ref::notNull);
  }

  private boolean not<caret>Null(String it) {
    return it != null;
  }
}