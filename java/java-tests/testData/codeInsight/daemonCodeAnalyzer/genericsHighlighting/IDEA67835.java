import java.util.List;

class B {
    void bar(List<String> x){
        foo(x);
    }
    <T> T foo(List<? super T> x){
        return null;
    }
}