// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.concatenation.merge_sequence;

class Append {

  void foo(StringBuilder s) {
      s.append(1)/*in source*/.append(2).append(3).append(4/*in arg*/);
      /*before dot*/
      /*after dot*/
      //after end
  }
}