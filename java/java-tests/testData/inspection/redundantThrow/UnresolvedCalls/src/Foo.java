import java.io.IOException;

class Foo {
  void unresolved() throws IOException {
    bar();
  }
  
  void unresolvedCtor() throws IOException {
    new Bar();
  }
  
  void resolvedCtorToClass() throws IOException {
    new Foo();
  }
  
  void arrayCreation() throws IOException {
    new int[10];
  }
}