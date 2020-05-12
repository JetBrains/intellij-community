class RedundantCastTest {

  public static void main(String[] args) {
    Integer bar = null;
    print(() -> args == null || args.length == 0 ? bar : (Integer) 1);
  }

  private static void print(I i) {
    Integer myI = i.get();
  }
  
  interface I {
    Integer get();
  }
}