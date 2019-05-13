import java.util.stream.Stream;
class Test {

  void foo(final Stream<StubElement<?>> inStream){
    inStream.map(StubElement::getPsi)
      .map(s -> s.length());
  }

  class StubElement<T extends String> {
    T getPsi() {return null;}
  }
}
