class Cast<T> implements SemElement {

    {
        final SemKey<? extends Cast> key = null;
        final Cast semElement =    getSemElement(key);
    }

    public <T extends SemElement> T getSemElement(SemKey<T> key) {
        return null;
    }


    class SemKey<T extends SemElement> {}
}

interface SemElement {}
