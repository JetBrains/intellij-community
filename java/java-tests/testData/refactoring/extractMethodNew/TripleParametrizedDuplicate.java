import java.util.Arrays;

class Triple {
    void foo(int... x) {
        <selection>
        System.out.println(Arrays.toString(x)); // original fragment
        System.out.println(1);
        </selection>

        System.out.println(Arrays.toString(x)); // first duplicate
        System.out.println(2);

        System.out.println(Arrays.toString(new int[]{})); // second duplicate
        System.out.println(1);
    }
}