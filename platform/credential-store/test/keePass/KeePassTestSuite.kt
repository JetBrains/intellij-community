// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.credentialStore.keePass

import com.intellij.credentialStore.com.intellij.credentialStore.keePass.KeePassClearTest
import com.intellij.credentialStore.com.intellij.credentialStore.keePass.KeePassImportTest
import com.intellij.credentialStore.com.intellij.credentialStore.keePass.KeePassSetMasterPasswordTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(KeePassCredentialStoreTest::class, KeePassFileManagerTest::class, PasswordSafeKeePassTest::class, KeePassClearTest::class, KeePassSetMasterPasswordTest::class, KeePassImportTest::class)
internal class KeePassTestSuite