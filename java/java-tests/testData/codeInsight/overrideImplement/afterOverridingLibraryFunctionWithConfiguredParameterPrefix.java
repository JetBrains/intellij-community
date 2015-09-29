interface IntFunction<T> {
  int apply(T t);
}

class A implements IntFunction<String> {
    public int apply(String inS) {
        return 0;
    }
}