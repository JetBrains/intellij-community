// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
class Test {
  /**
   * Test.
   *
   * @param X my string
   * @param <X> my type
   * <warning descr="Duplicate @param tag for parameter '<X>'">@param</warning> <X> another type
   */
  <X> X getValue(String X) {return null;}
}