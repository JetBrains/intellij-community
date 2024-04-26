// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.credentialStore.keePass

import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(KeePassCredentialStoreTest::class, KeePassFileManagerTest::class, PasswordSafeKeePassTest::class, KeePassClearTest::class, KeePassSetMasterPasswordTest::class, KeePassImportTest::class)
internal class KeePassTestSuite