
public class IDEADEV25923 {
    public void foo(Object... params) {}

    {
        foo(new Object[2]);
        foo(<warning descr="Redundant array creation for calling varargs method">new Object[0]</warning>);
    }
}