public class Sample2 {

    void test1(){
        extracted();
    }

    private static void extracted() {
        int a = 1;
        int b = 2;
        a = 42;
        System.out.println(a);
    }

    void test2(){
        extracted();
    }
}