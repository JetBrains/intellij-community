final class Test {
    static final Test A = new Test();

    private int i = 99;

    static {
        A.i = 10;
    }
}

enum Test1 {
    A, B, C;

    private int i = 99;

    static {
        A.i = 10;
        B.i = 20;
        System.out.println(A.i);
    }
}

abstract class Test2 {
    private static String FOO;

     static {
         try {
             FOO = null;
         } catch (Exception e) {
             e.printStackTrace();
         }
     }
}