class X {
  public static void main(String[] args) {
    boolean _user = true;
    String user = "foo";
      newMethod(_user, user);
  }

    private static void newMethod(boolean _user, String user) {
        System.out.println(_user + " " + user);
    }
}
