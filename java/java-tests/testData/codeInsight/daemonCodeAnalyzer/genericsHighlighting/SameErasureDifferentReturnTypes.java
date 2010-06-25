/** @noinspection UnusedDeclaration*/
interface Matcher<T> {

    boolean matches(java.lang.Object object);

    void _dont_implement_Matcher___instead_extend_BaseMatcher_();
}

interface ArgumentConstraintPhrases {
    <T> T with(Matcher<T> matcher);
    boolean with(Matcher<Boolean> matcher);
    byte with(Matcher<Byte> matcher);
    short with(Matcher<Short> matcher);
    int with(Matcher<Integer> matcher);
    long with(Matcher<Long> matcher);
    float with(Matcher<Float> matcher);
    double with(Matcher<Double> matcher);
}

class ExpectationGroupBuilder implements ArgumentConstraintPhrases {

    public <T> T with(final Matcher<T> matcher) {
        return null;
    }

    public boolean with(final Matcher<Boolean> matcher) {
        return false;
    }

    public byte with(final Matcher<Byte> matcher) {
        return 0;
    }

    public short with(final Matcher<Short> matcher) {
        return 0;
    }

    public int with(final Matcher<Integer> matcher) {
        return 0;
    }

    public long with(final Matcher<Long> matcher) {
        return 0;
    }

    public float with(final Matcher<Float> matcher) {
        return 0;
    }

    public double with(final Matcher<Double> matcher) {
        return 0;
    }
}
