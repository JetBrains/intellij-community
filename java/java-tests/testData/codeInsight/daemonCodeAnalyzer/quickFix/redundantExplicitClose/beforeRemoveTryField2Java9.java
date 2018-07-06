// "Remove redundant close" "false"

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
      this.ac.clo<caret>se();
    }
  }
}