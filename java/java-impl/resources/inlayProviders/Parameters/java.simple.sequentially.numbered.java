class HintsDemo {

  public static void print(String s1, String s2, String s3) {
    Stream.of(s1, s2, s3)
      .map(String::toUpperCase)
      .forEach(System.out::println);
  }

  public static void main(String[] args) {
    print("first","second","third");
  }
}