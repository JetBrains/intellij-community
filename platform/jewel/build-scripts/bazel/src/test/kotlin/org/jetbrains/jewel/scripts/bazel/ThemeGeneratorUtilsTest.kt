package org.jetbrains.jewel.scripts.bazel

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class ThemeGeneratorUtilsTest {
    @Test
    fun `validateBuildNumber accepts a full three-part build number`() {
        assertTrue(validateBuildNumber("253.1234.567"))
    }

    @Test
    fun `validateBuildNumber accepts a bare major SNAPSHOT build number`() {
        assertTrue(validateBuildNumber("241.SNAPSHOT"))
    }

    @Test
    fun `validateBuildNumber accepts a major-numeric-SNAPSHOT build number`() {
        assertTrue(validateBuildNumber("253.28294.SNAPSHOT"))
    }

    @Test
    fun `validateBuildNumber rejects a blank build number`() {
        assertFalse(validateBuildNumber(""))
    }

    @Test
    fun `validateBuildNumber rejects a build number that is too short`() {
        assertFalse(validateBuildNumber("241."))
    }

    @Test
    fun `validateBuildNumber rejects a major version that is not above 240`() {
        assertFalse(validateBuildNumber("239.1234.567"))
    }

    @Test
    fun `validateBuildNumber rejects a major version that is not numeric`() {
        assertFalse(validateBuildNumber("abc.1234.567"))
    }

    @Test
    fun `validateBuildNumber rejects a missing separator after the major version`() {
        assertFalse(validateBuildNumber("253-1234.567"))
    }

    @Test
    fun `validateBuildNumber rejects garbage after the major version`() {
        assertFalse(validateBuildNumber("253.not-a-number"))
    }
}
