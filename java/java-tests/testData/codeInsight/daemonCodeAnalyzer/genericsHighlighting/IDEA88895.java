import java.util.Iterator;

class WildcardGenericAndPrivateField {

    private Object field;

    public Iterator<? extends WildcardGenericAndPrivateField> iterator() {
        return null;
    }

    public void methodDoesNotCompile() {
        Iterator<? extends WildcardGenericAndPrivateField> iterator = iterator();
        while ( iterator.hasNext() ) {
            Object o = iterator.next().<error descr="'field' has private access in 'WildcardGenericAndPrivateField'">field</error>;
        }
    }

    public void methodCompiles() {
        Iterator<? extends WildcardGenericAndPrivateField> iterator = iterator();
        while ( iterator.hasNext() ) {
            WildcardGenericAndPrivateField next = iterator.next();
            Object o = next.field;
        }
    }

}