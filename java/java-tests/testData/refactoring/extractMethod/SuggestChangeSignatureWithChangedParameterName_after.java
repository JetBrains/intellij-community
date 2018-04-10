public class Test {
    {
        int x = 0;


        newMethod(x, 1);


        newMethod(x, 2);
    }

    private void newMethod(int p, int i) {
        System.out.println(p);
        System.out.println(p + i);
    }
}