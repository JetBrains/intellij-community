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
package com.intellij.java.codeInsight.daemon.quickFix;


import com.intellij.java.codeInsight.daemon.quickFix.streams.*;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
  AllMatchStreamApiMigrationTest.class,
  AnyMatchStreamApiMigrationTest.class,
  BufferedReaderStreamApiMigrationTest.class,
  CollectionsStreamApiMigrationTest.class,
  CollectStreamApiMigrationTest.class,
  ContinueStreamApiMigrationTest.class,
  DistinctStreamApiMigrationTest.class,
  FilterStreamApiMigrationTest.class,
  FindFirstStreamApiMigrationTest.class,
  FlatMapFirstStreamApiMigrationTest.class,
  ForeachFirstStreamApiMigrationTest.class,
  LimitStreamApiMigrationTest.class,
  MinMaxStreamApiMigrationTest.class,
  NoneMatchStreamApiMigrationTest.class,
  OtherStreamApiMigrationTest.class,
  ReductionOperationStreamApiMigrationTest.class,
  SortedOperationStreamApiMigrationTest.class,
  SumOperationStreamApiMigrationTest.class,
  TakeWhileOperationStreamApiMigrationTest.class,
  ToArrayOperationStreamApiMigrationTest.class
})
public class StreamApiMigrationInspectionTest {

}