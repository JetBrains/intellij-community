// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.concatenation.merge_sequence;

class Append {

  void foo(StringBuilder s) {
    s.append(1)/*in source*/.app<caret>end(2);
    s.append(3)/*before dot*/./*after dot*/append(4/*in arg*/);//after end
  }
}