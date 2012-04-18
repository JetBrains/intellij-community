public class RedundantIntCast
{
    public static void main(String[] args) {
        Integer i = 8;
        method((int)i, 1);  // int cast is marked as redundant
    }

    static void method(Object o, Object o1) {
        System.out.println("this method works on objects");
    }

    static void method(int i, int i1) {
        System.out.println("this method works on ints");
    }
}
