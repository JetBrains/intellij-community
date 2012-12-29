package org.hanuna.gitalk.common;

import org.jetbrains.annotations.NotNull;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

/**
 * @author erokhins
 */
public final class ShortList<E> extends AbstractList<E> {
    private static final Object NULL_ELEMENT = new Object();
    private static final Object LIST_FLAG = new Object();

    public static <E> ShortList<E> build(List<E> list) {
        ShortList<E> shortList = new ShortList<E>();
        switch (list.size()) {
            case 0:
                shortList.firstElement = null;
                shortList.secondOrList = null;
                break;
            case 1:
                shortList.firstElement = shortList.elementToObj(list.get(0));
                break;
            case 2:
                shortList.firstElement = shortList.elementToObj(list.get(0));
                shortList.secondOrList = shortList.elementToObj(list.get(1));
                break;
            default:
                shortList.firstElement = LIST_FLAG;
                shortList.secondOrList = list;
        }
        return shortList;
    }

    /**
     * @return list, if list.size() > 2 and ShortList another
     */
    public static <E> List<E> shortlyList(List<E> list) {
        if (list.size() > 2) {
            return list;
        } else {
            return build(list);
        }
    }

    /**
     * if list contain 1 element:
     *      firstElement = this element (or NULL_ELEMENT, if firstElement = null)
     *      secondOrList = null
     * if list contain 2 elements:
     *      firstElement = first
     *      secondOrList = second
     * if more 2 elements
     *      firstElement = LIST_FLAG
     *      secondOrList = List<E>
     */

    private Object firstElement = null;
    private Object secondOrList = null;

    /**
     * @return null, if no add List
     */
    @NotNull
    private List<E> getList() {
        if (firstElement != LIST_FLAG) {
            throw new IllegalStateException("request list, but state isn't list");
        }
        return (List<E>) secondOrList;
    }

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

    @Override
    public boolean add(E e) {
        int size = size();
        assert size >= 0;
        switch (size) {
            case 0:
                firstElement = elementToObj(e);
                secondOrList = null;
                break;
            case 1:
                secondOrList = elementToObj(e);
                break;
            case 2:
                List<E> newList = new ArrayList<E>(3);
                newList.add(objToElement(firstElement));
                newList.add(objToElement(secondOrList));
                newList.add(e);
                firstElement = LIST_FLAG;
                secondOrList = newList;
                break;
            default:
                List<E> list = getList();
                list.add(e);
        }
        return true;
    }

    @Override
    public E remove(int index) {
        checkRange(index);
        int size = size();
        assert size >= 0;
        E element;
        switch (size) {
            case 0:
                throw new IllegalStateException("size = 0, but request remove");
            case 1:
                element = objToElement(firstElement);
                firstElement = null;
                secondOrList = null;
                break;
            case 2:
                if (index == 0) {
                    element = objToElement(firstElement);
                    firstElement = secondOrList;
                    secondOrList = null;
                } else {
                    element = objToElement(secondOrList);
                    secondOrList = null;
                }
                break;
            case 3:
                List<E> list = getList();
                element = list.remove(index);
                firstElement = elementToObj(list.get(0));
                secondOrList = elementToObj(list.get(1));
                break;
            default:
                List<E> list1 = getList();
                element = list1.remove(index);
        }
        return element;
    }

    @Override
    public E get(int index) {
        checkRange(index);
        if (firstElement == LIST_FLAG) {
            return getList().get(index);
        }
        switch (index) {
            case 0:
                return objToElement(firstElement);
            case 1:
                return objToElement(secondOrList);
            default:
                List<E> list = getList();
                return list.get(index);
        }
    }

    @Override
    public int size() {
        if (firstElement == null) {
            return 0;
        }
        if (firstElement == LIST_FLAG) {
            return getList().size();
        }

        // firstElement is REAL element
        if (secondOrList != null) {
            return 2;
        } else {
            return 1;
        }
    }

}
