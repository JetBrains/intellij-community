// "Move up into 'if' statement branches" "true-preview"
class Test {
  String[] field;
  

  Runnable run = new Runnable() {
    int x = (int) (Math.random() * 300.0);
    String[] field2;
    
    @Override
    public void run() {
      if (x > 0) {
        String field = "foo";
        System.out.println(field);
          Test.this.field = new String[]{"One", "two"};
          field2 = new String[]{"One", "two"};
      } else {
        String field2 = "bar";
          field = new String[]{"One", "two"};
          this.field2 = new String[]{"One", "two"};
      }
    }
  };
}