class X {
  public static void main(String[] args) {
    boolean _user = true;
    String user = "foo";
      newMethod(_user, user);
  }

    private static void newMethod(boolean user, String user2) {
        System.out.println(user + " " + user2);
    }
}
