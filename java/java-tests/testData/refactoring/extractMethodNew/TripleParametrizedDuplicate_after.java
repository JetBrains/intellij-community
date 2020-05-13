import java.util.Arrays;

class Triple {
    void foo(int... x) {

        newMethod(x, 1);


        newMethod(x, 2);

        newMethod(new int[]{}, 1);
    }

    private void newMethod(int[] x, int i) {
        System.out.println(Arrays.toString(x)); // original fragment
        System.out.println(i);
    }
}