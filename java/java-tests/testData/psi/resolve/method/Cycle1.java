public class Cycle1{
    public interface A{
        void foo();
    }

    public interface B{
        void foo();
    }

    public static class C
        implements A{
        void foo(){}
    }

    public static class D extends C
        implements B{
        void foo(int ggg){}
        
        void foo1(){
            <ref>foo();
        }
    }
}
