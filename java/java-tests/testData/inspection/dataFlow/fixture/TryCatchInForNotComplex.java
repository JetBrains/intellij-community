class Bar {
  public static void main(String[] args) {
    for (String s : args) {
      try {
        System.out.println(s);
      }
      catch (Exception e) {
        System.out.println(e);
      }
    }
  }
}
