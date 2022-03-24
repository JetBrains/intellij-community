public class Test{

    void test1(){
        String local = "local";
        System.out.println("one");
        System.out.println("two");
    }

    void test2(){
        <selection>String local = "local";
        System.out.println("one");
        System.out.println(local.toLowerCase());</selection>
    }
}