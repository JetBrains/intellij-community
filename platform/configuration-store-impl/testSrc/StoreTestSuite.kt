// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.configurationStore.xml.XmlElementStorageTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

// Testing 'All in package' is very slow, so we have to use a Suite to speed it up.
@RunWith(Suite::class)
@Suite.SuiteClasses(
  ApplicationStoreTest::class,
  ProjectStoreTest::class, DefaultProjectStoreTest::class,
  ModuleStoreTest::class, ModuleStoreRenameTest::class,
  StorageManagerTest::class,
  SchemeManagerTest::class,
  XmlElementStorageTest::class,
  DirectoryBasedStorageTest::class
)
class StoreTestSuite