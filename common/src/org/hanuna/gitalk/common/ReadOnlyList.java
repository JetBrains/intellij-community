package org.hanuna.gitalk.common;

import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author erokhins
 */
public class ReadOnlyList<E> implements List<E> {
    @NotNull
    public static <E> ReadOnlyList<E> emptyList() {
        return new ReadOnlyList<E>(Collections.<E>emptyList());
    }

    @NotNull
    public static <E> ReadOnlyList<E> newReadOnlyList(@NotNull List<? extends E> list) {
        return new ReadOnlyList<E>(list);
    }

    private final List<? extends E> list;

    private ReadOnlyList(@NotNull List<? extends E> list) {
        this.list = list;
    }

    @Override
    public Iterator<E> iterator() {
        return Collections.unmodifiableList(list).iterator();
    }

    @Override
    public ListIterator<E> listIterator() {
        return Collections.unmodifiableList(list).listIterator();
    }

    @Override
    public ListIterator<E> listIterator(int index) {
        return Collections.unmodifiableList(list).listIterator();
    }

    @Override
    public List<E> subList(int fromIndex, int toIndex) {
        return Collections.unmodifiableList(list).subList(fromIndex, toIndex);
    }

    //------------------------------

    @Override
    public int size() {
        return list.size();
    }

    @Override
    public boolean isEmpty() {
        return list.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return list.contains(o);
    }

    @Override
    public Object[] toArray() {
        return list.toArray();
    }

    @Override
    public <E> E[] toArray(E[] a) {
        return list.toArray(a);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return list.containsAll(c);
    }

    @Override
    public E get(int index) {
        return list.get(index);
    }

    @Override
    public int indexOf(Object o) {
        return list.indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        return list.lastIndexOf(o);
    }

    //------------------------------

    @Override
    public boolean add(E e) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(int index, Collection<? extends E> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public E set(int index, E element) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void add(int index, E element) {
        throw new UnsupportedOperationException();
    }

    @Override
    public E remove(int index) {
        throw new UnsupportedOperationException();
    }
}
