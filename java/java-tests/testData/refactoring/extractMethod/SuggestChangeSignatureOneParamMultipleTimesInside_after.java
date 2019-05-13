public class Test {
    {
        int x = 0;

        newMethod(x, "foo");

        newMethod(x, "bar");
    }

    private void newMethod(int x, String foo) {
        System.out.println(foo);
        System.out.println(foo);
        System.out.println(x);
    }
}