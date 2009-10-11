/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
