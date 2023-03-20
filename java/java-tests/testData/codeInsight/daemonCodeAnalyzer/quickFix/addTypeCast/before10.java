// "Cast expression to 'A.Iterator<java.lang.String>'" "true-preview"
class A {
    interface Iterator<T> {        
    }

    class List<T> {
        Iterator<T> iterator() {
            return null;
        }
    }

    void method() {
        List l = new List();
        <caret>Iterator<String> it = (Iterator<Object>)l.iterator();
    }
}