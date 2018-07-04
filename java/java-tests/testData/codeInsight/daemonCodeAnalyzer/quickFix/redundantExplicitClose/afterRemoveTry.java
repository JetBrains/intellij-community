// "Remove redundant close" "true"

class MyAutoCloseable implements AutoCloseable {
  @Override
  void close() {

  }
}

class RemoveTry {
  public static void main(String[] args) {
    try(MyAutoCloseable ac = new MyAutoCloseable()) {
      System.out.println("asdasd");
    }
  }
}