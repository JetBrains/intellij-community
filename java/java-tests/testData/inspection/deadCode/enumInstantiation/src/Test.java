public enum Test
{
    FIRST, SECOND;

    public Test invert() {
        return this == FIRST ? SECOND : FIRST;
    }
}