class Client {
  public static void main(String[] args) {
    String s = new Derived().field;
		System.out.println(s);
  }
}