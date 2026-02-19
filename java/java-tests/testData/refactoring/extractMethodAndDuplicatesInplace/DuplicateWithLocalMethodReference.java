public class DuplicateWithLocalMethodReference {

    void example(){}

    void test(){
        <selection>System.out.println();
        example();</selection>

        System.out.println();
        example();
    }
}