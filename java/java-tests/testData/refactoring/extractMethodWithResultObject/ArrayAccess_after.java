class Test {
  void foo(String[] ss) {
     for(int i = 0; i < ss.length; i++) {
         NewMethodResult x = newMethod(ss, i);
     }
  }

    NewMethodResult newMethod(String[] ss, int i) {
        System.out.println(ss[i]);
        System.out.println(ss[i] + ss[i]);
        return new NewMethodResult();
    }

    static class NewMethodResult {
        public NewMethodResult() {
        }
    }
}