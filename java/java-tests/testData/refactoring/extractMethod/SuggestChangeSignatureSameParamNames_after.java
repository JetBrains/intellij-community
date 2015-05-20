public class Test {
    {
        int x = 0;

        newMethod(x, 1, 2);

        newMethod(x, 3, 4);
    }

    private void newMethod(int x, int x2, int x3) {
        System.out.println(x2);
        System.out.println(x3);
        System.out.println(x);
    }
}