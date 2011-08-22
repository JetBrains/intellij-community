public class Test {
    private String myStr;

    public String getMyStr() {
        return myStr;
    }

    void foo() {
        new Runnable(){
            @Override
            public void run() {
                  System.out.println(<selection>myStr</selection>);
            }
        };
    }
}

class X {
   public void n() {
       new Test().foo();
   }
}