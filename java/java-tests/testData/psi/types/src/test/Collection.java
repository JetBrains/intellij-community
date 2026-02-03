package test;

public interface Collection<E> {
    void add(E e);
    void remove(E e);
    boolean contains(E e);
    Iterator<E> iterator();
    void putAll(Collection<E> l);
}
