class S {
  {
    String s;
    try {
        NewMethodResult x = newMethod();
        s = x.s;
    } finally {
    }
    System.out.print(s);
  }

    NewMethodResult newMethod() {
        String s;
        s = "";
        return new NewMethodResult(s);
    }

    static class NewMethodResult {
        private String s;

        public NewMethodResult(String s) {
            this.s = s;
        }
    }
}
