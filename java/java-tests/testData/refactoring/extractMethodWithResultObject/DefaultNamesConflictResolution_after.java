class X {
  public static void main(String[] args) {
    boolean _user = true;
    String user = "foo";
      NewMethodResult x = newMethod(_user, user);
  }

    static NewMethodResult newMethod(boolean _user, String user) {
        System.out.println(_user + " " + user);
        return new NewMethodResult();
    }

    static class NewMethodResult {
        public NewMethodResult() {
        }
    }
}
