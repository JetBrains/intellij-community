
public class IDEADEV15215 {

    public static void main(String[] args) {
        foo(new byte[]{ 1, 2, 3 });
    }

    private static void foo(byte... bytes) {
        System.out.println("Test.foo(byte...)");
        for (int i = 0; i < bytes.length; i++) {
            System.out.println("bytes[" + i + "] = " + bytes[i]);
        }
    }


    
    public static void extra(String... args) {
      extra(<warning descr="Redundant array creation for calling varargs method">new String[]{"vvv","aaa"}</warning>);
    }
}