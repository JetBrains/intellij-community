// "Remove redundant close" "true"

class MyAutoCloseable implements AutoCloseable {
  @Override
  public void close() {

  }
}

class RemoveTry {
  public static void main(MyAutoCloseable ac) {
    try(ac) {
      System.out.println("asdasd");
    }
  }
}