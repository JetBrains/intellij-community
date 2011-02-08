class List<T> {}

class Test {
    <T> T extract(List<T> l) {
        return null;
    }
    
    void foo (List<double[]> l) {
       <ref>extract(l);
    }
}

