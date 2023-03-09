// "Create method 'f'" "true-preview"
class A {
    <T> T foo(){
       B<T> x = f<caret>();
    }
}

class B<K>{}
