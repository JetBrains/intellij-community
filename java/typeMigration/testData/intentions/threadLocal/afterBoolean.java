// "Convert to 'ThreadLocal'" "true"
class X {
    public static final ThreadLocal<Boolean> B = ThreadLocal.withInitial(() -> true);

  public static void main(String[] args) {
    System.out.println(!B.get());
  }
}