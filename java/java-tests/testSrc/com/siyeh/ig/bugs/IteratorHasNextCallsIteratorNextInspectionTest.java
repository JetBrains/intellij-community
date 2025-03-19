/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
@SuppressWarnings({"IteratorHasNextCallsIteratorNext", "override"})
public class IteratorHasNextCallsIteratorNextInspectionTest extends LightJavaInspectionTestCase {

  public void testHasNextCallsNext() {
    doTest("import java.util.*;" +
           "class MyIterator<T> implements Iterator<T> {" +
           "    private Iterator<T> iterator;" +
           "    MyIterator(Iterator<T> iterator) {" +
           "        this.iterator = iterator;" +
           "    }" +
           "    public boolean hasNext() {" +
           "        return /*'Iterator.hasNext()' contains call to 'next()'*/next/**/() != null;" +
           "    }" +
           "    public T next() {" +
           "        return iterator.next();" +
           "    }" +
           "    public void remove() {" +
           "        iterator.remove();" +
           "    }" +
           "}");
  }

  public void testHasNextCallsPrevious() {
    doTest("import java.util.*;" +
           "abstract class MyIterator<T> implements ListIterator<T> {" +
           "    private ListIterator<T> iterator;" +
           "    MyIterator(ListIterator<T> iterator) {" +
           "        this.iterator = iterator;" +
           "    }" +
           "    public boolean hasNext() {" +
           "        return /*'Iterator.hasNext()' contains call to 'previous()'*/previous/**/() != null;" +
           "    }" +
           "    public boolean hasPrevious() {" +
           "        return this./*'Iterator.hasPrevious()' contains call to 'previous()'*/previous/**/() != null;" +
           "    }" +
           "    public T next() {" +
           "        return iterator.next();" +
           "    }" +
           "    public T previous() {" +
           "        return iterator.previous();" +
           "    }" +
           "}");
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new IteratorHasNextCallsIteratorNextInspection();
  }
}