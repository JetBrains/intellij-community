public class NotRaw<T> {
    T g<caret>et(T t){
        T tt = t;
        if ( t == null) {
            return null;
        }           else
        return null;
    }

}

class Raw extends NotRaw {
    void foo() {
        Object o = get(null);
    }
}