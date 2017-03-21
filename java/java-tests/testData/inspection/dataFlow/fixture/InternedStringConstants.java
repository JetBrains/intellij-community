class Example {

  public static final String FIRST = "f";
  public static final String SECOND = "d";
  public static final String THIRD = FIRST + SECOND;

  public static void main(String[] args) {
    System.out.println(<warning descr="Condition 'THIRD == \"fd\"' is always 'true'">THIRD == "fd"</warning>);
  }
}