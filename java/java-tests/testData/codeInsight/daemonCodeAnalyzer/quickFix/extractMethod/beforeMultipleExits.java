// "Extract method" "false"

class Main {
  static  boolean test() {
    throw new RuntimeException();
  }

  private void notExtractable() {
    while (true) {
      <selection>if (test()) {
        System.out.println("ads");
        break;
      } else {
        System.out.println("Asd");
        if (test()) {
          return;
        }
        System.out.println("ASdasd");
        </selection>
      }
      somethingElse();
    }
  }

  private static void somethingElse() {
  }
}