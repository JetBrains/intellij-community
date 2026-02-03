interface I {
    int indexOf(Enum<?> e);
}
abstract class C<T> implements I {
    @Override
    public int indexOf(Enum<?> e) {
    }
}


class C2 extends C {
    {
        inde<caret>x
    }
}