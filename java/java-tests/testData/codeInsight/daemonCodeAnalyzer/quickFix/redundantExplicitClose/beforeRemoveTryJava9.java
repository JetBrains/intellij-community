// "Remove redundant 'close()'" "true-preview"

class MyAutoCloseable implements AutoCloseable {
  @Override
  public void close() {

  }
}

class RemoveTry {
  public static void main(MyAutoCloseable ac) {
    try(ac) {
      System.out.println("asdasd");
      (ac).close<caret>();
    }
  }
}