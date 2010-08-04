class A<T extends List<String>> extends List<List<Integer>> {
    int method(T x)  {
        return x.size() >> 2;
    }
}