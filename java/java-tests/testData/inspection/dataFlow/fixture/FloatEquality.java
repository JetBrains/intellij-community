class Fun {
  public static void main(String[] args) throws Exception {
    float f = 1f;
    int x = <warning descr="Condition 'f == 1f' is always 'true'">f == 1f</warning> ? 1 : 2;
  }

}