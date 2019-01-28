public class Test1 {
    public static class A{
        protected void foo(){}
    }

    public static void main(String[] args){
        new A(){
            protected void foo(){
                super.<caret>foo();
            }
        };
    }
}
