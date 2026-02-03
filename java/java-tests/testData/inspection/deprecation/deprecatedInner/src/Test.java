public class Test {
    /**
     * @deprecated
      */
   boolean foo(){
        return false;
   }

   Runnable runnable = new Runnable() {
       public void run() {
           Test.this.foo();
       }
   };

   boolean bar(boolean g){
       new Runnable(){

           public void run() {
              new Runnable(){

                  public void run() {
                      Test.this.foo();
                  }
              };
           }
       };
       return false;
   }

   Test(boolean g, boolean h){
     if (g && h){}
   }


    public Test() {
    }



    public static void main(String[] args) {
        Test test = new Test();
        test.bar(test.foo());
        Test y11 = new Test(test.foo(), test.bar(test.foo()));
        System.out.println(y11);
    }

}
