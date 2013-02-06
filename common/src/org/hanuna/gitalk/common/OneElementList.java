package org.hanuna.gitalk.common;

import org.jetbrains.annotations.Nullable;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

/**
 * @author erokhins
 */
public class OneElementList<E> extends AbstractList<E> {
    private static Object NULL_ELEMENT = new Object();

    /**
     * @return list, if list.size() > 1 and OneElementList another
     */
    public static <E> List<E> shortlyList(List<E> list) {
        if (list.size() > 1) {
            return list;
        } else {
            OneElementList<E> oneElementList = new OneElementList<E>();
            if (list.size() != 0) {
                oneElementList.add(list.get(0));
            }
            return oneElementList;
        }
    }

    public static <E> List<E> buildList(E e) {
        List<E> list = new OneElementList<E>();
        list.add(e);
        return list;
    }

    private Object elementOrList = null;

    private void checkRange(int index) {
        int size = size();
        if (index < 0 || index >= size) {
            throw  new IllegalArgumentException("Size is: " + size + ", but index: " + index);
        }
    }

    private Object elementToObj(E e) {
        if (e == null) {
            return NULL_ELEMENT;
        } else {
            return e;
        }
    }

    private E objToElement(Object obj) {
        assert obj != null;
        if (obj == NULL_ELEMENT) {
            return null;
        } else {
            return (E) obj;
        }
    }

    @Nullable
    private List<E> getList() {
        List<E> list;
        try {
            list = (List<E>) elementOrList;
        } catch (ClassCastException e) {
            list = null;
        }
        return list;
    }

    @Override
    public E get(int index) {
        checkRange(index);
        List<E> list = getList();
        if (list == null) {
            return objToElement(elementOrList);
        } else {
            return list.get(index);
        }
    }

    @Override
    public int size() {
        if (elementOrList == null) {
            return 0;
        }
        List<E> list = getList();
        if (list == null) {
            return 1;
        } else {
            return list.size();
        }
    }

    @Override
    public E remove(int index) {
        checkRange(index);
        List<E> list = getList();
        if (list == null) {
            E e = objToElement(elementOrList);
            elementOrList = null;
            return e;
        } else {
            return list.remove(index);
        }
    }

    @Override
    public boolean add(E e) {
        if (elementOrList == null) {
            elementOrList = elementToObj(e);
            return true;
        }
        List<E> list = getList();
        if (list == null) {
            list = new ArrayList<E>(2);
            list.add(objToElement(elementOrList));
            list.add(e);
            elementOrList = list;
        } else {
            list.add(e);
        }
        return true;
    }
}
