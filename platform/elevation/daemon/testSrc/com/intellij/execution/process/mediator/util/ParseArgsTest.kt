// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator.util

import assertk.Assert
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import org.junit.jupiter.api.Test

class ParseArgsTest {
  @Test
  internal fun `check empty args`() {
    assertParsedArgs().isEmpty()
    assertParsedArgs("").containsExactly(null to "")
    assertParsedArgs("",
                     "").containsExactly(null to "",
                                         null to "")
  }

  @Test
  internal fun `check positional args`() {
    assertParsedArgs("bar").containsExactly(null to "bar")
    assertParsedArgs("bar",
                     "baz").containsExactly(null to "bar",
                                            null to "baz")
    assertParsedArgs("--",
                     "bar",
                     "baz").containsExactly(null to "bar",
                                            null to "baz")
    assertParsedArgs("bar",
                     "--").containsExactly(null to "bar",
                                           null to "--")
    assertParsedArgs("--",
                     "bar",
                     "--").containsExactly(null to "bar",
                                           null to "--")
    assertParsedArgs("--",
                     "--opt",
                     "bar").containsExactly(null to "--opt",
                                            null to "bar")
  }

  @Test
  internal fun `check options with values`() {
    assertParsedArgs("--foo=bar").containsExactly("--foo" to "bar")
    assertParsedArgs("--foo", "bar").containsExactly("--foo" to "bar")

    assertParsedArgs("--foo=bar",
                     "--opt=val").containsExactly("--foo" to "bar", "--opt" to "val")
    assertParsedArgs("--foo", "bar",
                     "--opt", "val").containsExactly("--foo" to "bar", "--opt" to "val")
  }

  @Test
  internal fun `check options without values`() {
    assertParsedArgs("--foo").containsExactly("--foo" to null)
    assertParsedArgs("--foo",
                     "--opt").containsExactly("--foo" to null,
                                              "--opt" to null)
    assertParsedArgs("--foo",
                     "--opt",
                     "--flag").containsExactly("--foo" to null,
                                               "--opt" to null,
                                               "--flag" to null)
    assertParsedArgs("--foo=bar",
                     "baz").containsExactly("--foo" to "bar",
                                            null to "baz")
  }

  @Test
  internal fun `check mixed options`() {
    assertParsedArgs("--opt",
                     "--foo", "bar").containsExactly("--opt" to null,
                                                     "--foo" to "bar")
    assertParsedArgs("--opt",
                     "--foo=bar").containsExactly("--opt" to null,
                                                  "--foo" to "bar")

    assertParsedArgs("--opt", "--flag",
                     "--foo", "bar").containsExactly("--opt" to null,
                                                     "--flag" to null,
                                                     "--foo" to "bar")
    assertParsedArgs("--opt", "--flag",
                     "--foo=bar").containsExactly("--opt" to null,
                                                  "--flag" to null,
                                                  "--foo" to "bar")

    assertParsedArgs("--opt",
                     "--foo", "bar",
                     "--flag").containsExactly("--opt" to null,
                                               "--foo" to "bar",
                                               "--flag" to null)
    assertParsedArgs("--opt",
                     "--foo=bar",
                     "--flag").containsExactly("--opt" to null,
                                               "--foo" to "bar",
                                               "--flag" to null)
    assertParsedArgs("--opt",
                     "--",
                     "--foo").containsExactly("--opt" to null,
                                              null to "--foo")
    assertParsedArgs("--opt",
                     "--",
                     "--foo",
                     "bar").containsExactly("--opt" to null,
                                            null to "--foo",
                                            null to "bar")
  }

  private fun assertParsedArgs(vararg args: String): Assert<List<Pair<String?, String?>>> {
    return assertThat(parseToPairs(*args))
  }

  private fun parseToPairs(vararg args: String): List<Pair<String?, String?>> {
    return parseArgs(args).map { (option, value) -> option to value }.toList()
  }
}