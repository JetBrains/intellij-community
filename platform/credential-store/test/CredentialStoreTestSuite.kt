// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.credentialStore

import com.intellij.credentialStore.keePass.KeePassTestSuite
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(KeePassTestSuite::class, CredentialSerializeTest::class, CredentialStoreTest::class, PasswordSafeTest::class, PgpTest::class)
internal class CredentialStoreTestSuite