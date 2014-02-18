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

import java.util.*;

class Test {
    public static void bar() {
      <error descr="Incompatible types. Found: 'java.lang.Class<capture<? extends java.util.Iterator>>', required: 'java.lang.Class<? extends java.util.Iterator<?>>'">Class<? extends Iterator<?>> c = foo();</error>
    }
  
    public static Class<? extends Iterator> foo() {
      return null;
    }
}

class Example {
    static List<? extends AbstractTreeNode> treeNodes = null;

    public static void main(String[] args) {
        for (AbstractTreeNode<String> treeNode : treeNodes) {

        }
    }

}
class AbstractTreeNode<T> {}