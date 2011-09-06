// "Pull method 'foo' up and make it abstract" "true"
public class Test{
    void main(){
        new Int(){
            @Override
            void foo(){

            }
        };
    }
}

abstract class Int {
    abstract void foo();
}