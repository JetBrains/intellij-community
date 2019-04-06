import java.util.Random;

class JavaClass {
  private final Random rnd = new Random();

  public int getRandom13() {
    return rnd.nextInt(13);
  }

  public int getRandom42() {
    return rnd.nextInt(42);
  }
}

class JavaMain {
  public static void main(String[] args) {
    System.out.println(new JavaClass().getRandom42());
  }
}