class ClassExt {

    /** @noinspection UnusedDeclaration*/
    public static <T, P1, P2> T newInstance(Class<T> clazz,
                                            Class<? super P1> t1, P1 p1,
                                            Class<? super P2> t2, P2 p2) {
        return null;
    }


}

abstract class TKey<T> {

    protected abstract Class<T> getType();
}


class GoodIsRed6 {


    public static <TK extends TKey<?>> TK createClone(TK tkey, String key) {


        Class<TK> clazz = null;

        return ClassExt.newInstance(clazz, String.class, key, Class.class, tkey.getType());
    }
}
