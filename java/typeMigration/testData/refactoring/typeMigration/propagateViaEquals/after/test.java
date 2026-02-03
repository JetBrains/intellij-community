public class Test {
    public void meth(Long p1, Long p2) {
        p2.hashCode();
        System.out.println(p2.toString());
        if (p1.equals(p2)) {
            System.out.print("dummy action" + p2);
        }
    }
}