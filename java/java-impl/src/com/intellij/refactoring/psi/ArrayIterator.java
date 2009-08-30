package com.intellij.refactoring.psi;

import java.util.Iterator;
import java.util.NoSuchElementException;

class ArrayIterator<T> implements Iterator<T>{
    private final T[] contents;
    private int currentIndex = 0;
    private final Object lock = new Object();

    ArrayIterator(T[] contents){
        super();
        this.contents = contents.clone();
    }

    public boolean hasNext(){
        synchronized(lock){
            return currentIndex < contents.length;
        }
    }

    public T next(){
        synchronized(lock){
            if(currentIndex >= contents.length){
                throw new NoSuchElementException();
            }
            final T out = contents[currentIndex];
            currentIndex++;
            return out;
        }
    }

    public void remove(){

    }
}
