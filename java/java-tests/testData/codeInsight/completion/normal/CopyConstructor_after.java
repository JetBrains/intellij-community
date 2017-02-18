class CopyConstructor {
    private final String ad;

    public CopyConstructor(CopyConstructor other) {
        this.ad = other.ad<caret>
    }
}