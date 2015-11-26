public class Test {
    public void meth(String p1, String p2) {
        p2.hashCode();
        System.out.println(p2.toString());
        if (p1.equals(p2)) {
            System.out.print("dummy action" + p2);
        }
    }
}