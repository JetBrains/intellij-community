// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring;

import com.intellij.codeInsight.ConvertToAtomicIntentionTest;
import com.intellij.codeInsight.ConvertToLongAdderIntentionTest;
import com.intellij.codeInsight.ConvertToThreadLocalIntention6Test;
import com.intellij.codeInsight.ConvertToThreadLocalIntentionTest;
import com.intellij.codeInsight.inspections.GuavaInspectionTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
  TypeMigrationTest.class,
  TypeMigrationByAtomicRuleTest.class,
  TypeMigrationByThreadLocalRuleTest.class,
  TypeMigrationByLongAdderTest.class,
  MigrateTypeSignatureTest.class,
  ChangeTypeSignatureTest.class,
  WildcardTypeMigrationTest.class,
  ConvertToAtomicIntentionTest.class,
  ConvertToLongAdderIntentionTest.class,
  ConvertToThreadLocalIntentionTest.class,
  ConvertToThreadLocalIntention6Test.class,
  GuavaInspectionTest.class,
})
public class AllTypeMigrationTestSuite {
}