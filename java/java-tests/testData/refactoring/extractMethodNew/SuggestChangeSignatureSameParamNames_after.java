public class Test {
    {
        int x = 0;

        newMethod(x, 1, 2);

        newMethod(x, 3, 4);
    }

    private void newMethod(int x, int i, int i2) {
        System.out.println(i);
        System.out.println(i2);
        System.out.println(x);
    }
}