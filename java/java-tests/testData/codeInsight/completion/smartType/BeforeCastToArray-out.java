class Foo {
    public static final Foo[] EMPTY_ARRAY = new Foo[0];

    public Foo[] foo(boolean b){
        return EMPTY_ARRAY;<caret>(Bar[]);
    }

}
