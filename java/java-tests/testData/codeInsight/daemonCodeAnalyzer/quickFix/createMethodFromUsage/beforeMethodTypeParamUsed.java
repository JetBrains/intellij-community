// "Create method 'f'" "true"
class A {
    <T> T foo(){
       B<T> x = f<caret>();
    }
}

class B<K>{}
