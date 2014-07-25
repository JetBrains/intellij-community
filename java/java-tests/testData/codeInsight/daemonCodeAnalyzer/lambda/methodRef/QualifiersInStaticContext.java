public class Test {
    interface I {
        void foo();
    }

    private Object o;
  
    public static void main(String[] args){
        I i = <error descr="Non-static field 'o' cannot be referenced from a static context">o</error>::wait;
    }
}