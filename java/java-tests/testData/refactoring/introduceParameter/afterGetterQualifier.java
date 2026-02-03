public class Test {
    private String myStr;

    public String getMyStr() {
        return myStr;
    }

    void foo(String anObject) {
        new Runnable(){
            @Override
            public void run() {
                  System.out.println(anObject);
            }
        };
    }
}

class X {
   public void n() {
       final Test test = new Test();
       test.foo(test.getMyStr());
   }
}