import java.util.stream.Stream;

class Test {
  private static class Destination{ }

  private final Destination destination = new Destination();

  public void main(Stream<String> stream){
    stream.filter(this::notNull);
  }

  private boolean not<caret>Null(String it) {
    return it != null;
  }
}