import java.util.Arrays;

class A {
    public void test() {
        String[] strs = {"scnd", "third"};
        System.out.println(Arrays.asList("frst", strs, "4th"));
    }
}