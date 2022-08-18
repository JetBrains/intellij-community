// "Remove redundant 'close()'" "true-preview"

class MyAutoCloseable implements AutoCloseable {
  @Override
  public void close() {

  }
}

class RemoveTry {
  final MyAutoCloseable ac;

  public void main(RemoveTry other) {
    try(other.ac) {
      System.out.println("asdasd");
    }
  }
}