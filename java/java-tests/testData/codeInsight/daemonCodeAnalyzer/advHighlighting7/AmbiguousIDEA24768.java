/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package pck;

import java.util.ArrayList;
import java.util.List;

class IdeaBug {

  public static void main(String[] args) {
    ClassA.copyOf(new ArrayList<String>());
  }

  private static class ClassA<E> extends ClassB<E> {

    <error descr="'copyOf(Iterable<? extends E>)' in 'pck.IdeaBug.ClassA' clashes with 'copyOf(Iterable<? extends E>)' in 'pck.IdeaBug.ClassB'; both methods have same erasure, yet neither hides the other">public static <E extends Comparable<? super E>> ClassA<E> copyOf(
        Iterable<? extends E> elements)</error> {
      System.out.println("Hello from ClassA");
      return null;
    }
  }

  private static class ClassB<E> {

    public static <E> ClassA<E> copyOf(Iterable<? extends E> elements) {
      System.out.println("Hello from ClassB");
      return null;
    }
  }
}