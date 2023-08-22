/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import java.util.Iterator;
import java.util.NoSuchElementException;

final class Tree<T> {

    private final T elem;
    private final Tree<T> parent;

    private Tree(T elem, Tree<T> parent) {
        this.elem = elem;
        this.parent = parent;
    }

    /** Iterator from current tree element to it's root. */
    public Iterator<T> toRoot() {
        return new Iterator<T>() {
            Tree<T> curr = Tree.this;

            @Override
            public void remove() {}

            @Override
            public boolean hasNext() {
                return curr.parent != null;
            }

            @Override
            public T next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }

                final T res = curr.elem; // IDEA inspection: Local variable 'res' is redundant (IDEA-163786)
                curr = curr.parent;
                return res;
            }
        };
    }
}