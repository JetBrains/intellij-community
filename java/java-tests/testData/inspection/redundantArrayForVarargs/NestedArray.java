public class NestedArray {
    public NestedArray() {}

    public void method( Object ... args ) {
        for (int i = 0; i < args.length; i++) {
            System.out.println("args["+i+"] = " + args[i]);
        }
    }

    public void main(String[] args) {
        String[] params = new String[]{ "0", "1" };
        method(new Object[]{params});
        method(<warning descr="Redundant array creation for calling varargs method">new Object[]{"2", params}</warning>);
        method(<warning descr="Redundant array creation for calling varargs method">new Object[]{params, params}</warning>);
    }
}