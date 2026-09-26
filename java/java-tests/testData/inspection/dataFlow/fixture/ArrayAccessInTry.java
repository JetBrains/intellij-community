class Scratch {
  public static void main(String[] files) {
    try {
      System.out.println(files[0]);
      files[1] = "hello";
    }
    catch (RuntimeException e) {
      System.out.println(e);
    }
  }
}