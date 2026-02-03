// "Remove redundant 'close()'" "true-preview"

class MyAutoCloseable implements AutoCloseable {
  @Override
  public void close() {

  }
}

class RemoveTry {
  public static void main(String[] args) {
    try(MyAutoCloseable ac = new MyAutoCloseable()) {
      System.out.println("Number of parameters?");
      if (args.length == 0) {
      }
    }
  }
}