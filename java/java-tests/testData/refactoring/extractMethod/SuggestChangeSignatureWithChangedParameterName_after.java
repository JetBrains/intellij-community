public class Test {
    {
        int x = 0;


        newMethod(x, x + 1);


        newMethod(x, x + 2);
    }

    private void newMethod(int p, int i) {
        System.out.println(p);
        System.out.println(i);
    }
}