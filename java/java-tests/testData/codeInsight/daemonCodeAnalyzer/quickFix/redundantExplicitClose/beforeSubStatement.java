// "Remove redundant 'close()'" "true-preview"

class MyAutoCloseable implements AutoCloseable {
  @Override
  public void close() {

  }
}

class RemoveTry {
  public static void main(String[] args) {
    try(MyAutoCloseable ac = new MyAutoCloseable()) {
      if (args.length == 0) {
        System.out.println("No parameters");
        ac.close<caret>();
      } else if (args.length == 1) {
        System.out.println("One parameter: " + args[0]);
      } else if (args.length > 1) {
      }
    }
  }
}