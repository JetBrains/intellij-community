package com.intellij.tools.build.bazel;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;


@RunWith(Suite.class)
@Suite.SuiteClasses({
  JvmIncBuilderTest.class,
  ZipBuilderTest.class,
  DependencyGraphTest.class,
  KotlinCriTest.class
})
public class AllTests {
}