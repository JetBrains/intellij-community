package test;

public class Client {
    public static List<A.B> getList() { return null; }
    
    public static void method() {
        final List<A.B> l = getList();
    }
}