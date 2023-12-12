// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.ui

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CloneDvcsValidationUtilsTest {
  @Test
  fun testIsRepositoryUrlValidHTTP() {
    val httpUrl = "http://example.com/git/repo.git"
    assertTrue(CloneDvcsValidationUtils.isRepositoryUrlValid(httpUrl), "HTTP url is not recognized as a valid repository url")
  }

  @Test
  fun testIsRepositoryUrlValidSSH() {
    val sshUrl = "git@example.com:repo.git"
    assertTrue(CloneDvcsValidationUtils.isRepositoryUrlValid(sshUrl), "SSH url is not recognized as a valid repository url")
  }

  @Test
  fun testIsRepositoryUrlValidSSHWithSubDirectories() {
    val sshUrlSubDirs = "git@example.com:dir1/dir2/repo.git"
    assertTrue(CloneDvcsValidationUtils.isRepositoryUrlValid(sshUrlSubDirs),
               "SSH url with subdirectories is not recognized as a valid repository url")
  }

  @Test
  fun testIsRepositoryUrlValidInvalidUrl() {
    val invalidUrl = "something://not a url"
    assertFalse(CloneDvcsValidationUtils.isRepositoryUrlValid(invalidUrl), "Invalid url is recognized as a valid repository url")
  }

  @Test
  fun testIsRepositoryUrlValidEmptyUrl() {
    val emptyUrl = ""
    assertFalse(CloneDvcsValidationUtils.isRepositoryUrlValid(emptyUrl), "Empty url is recognized as a valid repository url")
  }
}