class C<T> {
    protected <U> U method<caret>(){
    }
}

class C1 extends C<String> {
    protected <V> V method(){
    }
}