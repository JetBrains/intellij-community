// "Convert to 'ThreadLocal'" "true"
class NoWrapping {

    public static final ThreadLocal<Character> i = ThreadLocal.withInitial(() -> (char) 0);

  public static void main(String[] args) {
    i.set((char) (i.get() + 1));
  }
}