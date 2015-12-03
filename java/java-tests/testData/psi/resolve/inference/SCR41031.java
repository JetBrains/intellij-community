class List<T>{
    T get () {return null;}
}

class CaptureTest <T> {
    private List<? extends List<T>> listOfSets;

    private void foo() {
        <ref>doGenericThing(listOfSets.get());
    }

    public <U> List<U> doGenericThing(List<U> set) {
        return null;
    }
}