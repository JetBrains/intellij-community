public class Test {
  public static void main(String[] args) {
    final Memento m = Test::isPlaying;
    System.out.println(m.isPlaying());
  }

  private static boolean isPlaying() {
    return false;
  }

  public static interface Memento {
    boolean isPlaying();
  }
}
