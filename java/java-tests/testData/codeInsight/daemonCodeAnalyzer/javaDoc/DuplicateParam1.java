// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
class Test {
  /**
   * Test. Don't report type param 'X' as duplicate of param 'X'.
   *
   * @param X my string
   * @param <X> my type
   */
  <X> X getValue(String X) {return null;}
}