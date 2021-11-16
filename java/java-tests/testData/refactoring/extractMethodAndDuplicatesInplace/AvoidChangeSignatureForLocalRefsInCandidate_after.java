public class Test{

    void test1(){
        extracted();
    }

    private void extracted() {
        String local = "local";
        System.out.println("one");
        System.out.println("two");
    }

    void test2(){
        String local = "local";
        System.out.println("one");
        System.out.println(local.toLowerCase());
    }
}