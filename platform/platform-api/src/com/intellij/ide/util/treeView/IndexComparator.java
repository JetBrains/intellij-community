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

/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Aug 30, 2002
 */
package com.intellij.ide.util.treeView;

import java.util.Comparator;

public class IndexComparator implements Comparator<NodeDescriptor> {
  public static final IndexComparator INSTANCE = new IndexComparator();

  private IndexComparator() {}

  @Override
  public int compare(NodeDescriptor nodeDescriptor1, NodeDescriptor nodeDescriptor2) {
    return nodeDescriptor1.getIndex() - nodeDescriptor2.getIndex();
  }
}
